package com.example.vinyl.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.example.vinyl.MainActivity
import com.example.vinyl.R
import com.example.vinyl.data.pullDay
import com.example.vinyl.ui.notification.notificationsAllowed
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * One local notification a day nudging the user to open Vinyl and pick up today's music.
 *
 * Local rather than push: cards are pulled when the user asks (request_recommendations), so the
 * server has no "a card arrived" moment to announce. This is a reminder to use the app, nothing
 * more, so it needs no Firebase, no device-token table and no server.
 */
object DailyReminder {
    const val CHANNEL_ID = "daily_reminder"

    /** Local time the reminder aims for. WorkManager may run it a little later to save battery. */
    const val REMINDER_HOUR = 18

    /** Intent extra on the notification's tap, telling MainActivity to open the receive flow. */
    const val EXTRA_OPEN_RECEIVE = "com.example.vinyl.OPEN_RECEIVE"

    private const val WORK_NAME = "daily_reminder"
    private const val NOTIFICATION_ID = 1001

    /** Creates the channel. Safe to call on every launch; Android ignores repeats. */
    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.reminder_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = context.getString(R.string.reminder_channel_description) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /**
     * Schedules the daily job if the user wants reminders, and cancels it if not. Called on every
     * launch and whenever the choice changes. UPDATE keeps one job and re-aims it at
     * [REMINDER_HOUR], so calling this repeatedly never stacks reminders.
     */
    fun sync(context: Context) {
        val workManager = WorkManager.getInstance(context)
        if (!ReminderPrefs(context).enabled) {
            workManager.cancelUniqueWork(WORK_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<DailyReminderWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(delayUntilNext(REMINDER_HOUR, ZonedDateTime.now()).toMillis(), TimeUnit.MILLISECONDS)
            .build()
        workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    /** Turns reminders on or off from onboarding or Settings, and reschedules to match. */
    fun setEnabled(context: Context, enabled: Boolean) {
        ReminderPrefs(context).enabled = enabled
        sync(context)
    }

    /** On sign-out: no reminders for a signed-out phone, and forget the last visit. */
    fun stop(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        ReminderPrefs(context).lastPulledDate = null
    }

    internal fun post(context: Context) {
        // Android 13+ needs the runtime grant; below that there is none to check.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val tap = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_OPEN_RECEIVE, true)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.reminder_title))
            .setContentText(context.getString(R.string.reminder_text))
            .setContentIntent(tap)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }
}

/**
 * Whether today's reminder should go out. Skipped when the user turned reminders off, when
 * Android won't show notifications, or when they already picked up today's music — a reminder
 * after they've done the thing is just noise.
 */
internal fun shouldRemind(enabled: Boolean, allowed: Boolean, lastPulled: LocalDate?, today: LocalDate): Boolean =
    enabled && allowed && lastPulled != today

/** Time from [now] until the next [hour]:00 local time — later today, or tomorrow if it's passed. */
internal fun delayUntilNext(hour: Int, now: ZonedDateTime): Duration {
    var next = now.toLocalDate().atTime(hour, 0).atZone(now.zone)
    if (!next.isAfter(now)) next = next.plusDays(1)
    return Duration.between(now, next)
}

/**
 * Device-local, not a profile column: a reminder belongs to the phone it rings on, and the OS
 * permission it depends on is per device too (DesignDecision.md §7 keeps those out of the DB).
 */
class ReminderPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("daily_reminder", Context.MODE_PRIVATE)

    /** The user's own choice. Defaults to on; Android's permission still has the final say. */
    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, true)
        set(value) = prefs.edit { putBoolean(KEY_ENABLED, value) }

    /** The last pull day (turning over at 06:00) the user asked for recommendations, or null. */
    var lastPulledDate: LocalDate?
        get() = prefs.getString(KEY_LAST_PULLED, null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        set(value) = prefs.edit { putString(KEY_LAST_PULLED, value?.toString()) }

    /** Call when the user asks for today's music, so today's reminder is skipped. */
    fun markPulledToday() {
        lastPulledDate = pullDay()
    }

    private companion object {
        const val KEY_ENABLED = "enabled"
        const val KEY_LAST_PULLED = "last_pulled"
    }
}

class DailyReminderWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val prefs = ReminderPrefs(applicationContext)
        val remind = shouldRemind(
            enabled = prefs.enabled,
            allowed = applicationContext.notificationsAllowed(),
            lastPulled = prefs.lastPulledDate,
            today = pullDay(),
        )
        if (remind) DailyReminder.post(applicationContext)
        return Result.success()
    }
}
