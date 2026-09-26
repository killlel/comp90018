package com.example.vinyl.ui.notification

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Whether Vinyl may post notifications right now.
 *
 * Android owns this answer and the app stores nothing: on Android 13+ it reflects the
 * POST_NOTIFICATIONS grant, and on every version it also reflects the switch in system settings.
 * Ask it whenever you need to know rather than keeping a copy that can go stale.
 */
fun Context.notificationsAllowed(): Boolean =
    NotificationManagerCompat.from(this).areNotificationsEnabled()

/**
 * The "Turn On" button's behaviour on any screen that asks for notifications.
 *
 * [permanentlyDenied] is true once Android has stopped showing its dialog; [request] then opens
 * this app's notification settings instead.
 */
internal class NotificationPermissionRequest(val permanentlyDenied: Boolean, val request: () -> Unit)

/**
 * Asks for notification permission, and notices when Android has stopped asking.
 *
 * Same approach as `rememberLocationPermissionRequest`: permanent denial is decided inside the
 * result callback, because `shouldShowRequestPermissionRationale` only means "permanent" straight
 * after a denial - it is false before the first ask too.
 *
 * [onAnswered] gets `true` when notifications are allowed and `false` when the user said no. When
 * the user comes back from system settings having switched notifications on, it fires with `true`
 * by itself, so they don't have to find the button again.
 *
 * Below Android 13 there is no runtime dialog. If notifications are already allowed this answers
 * `true` straight away; if the user switched them off in system settings, it opens those.
 */
@Composable
internal fun rememberNotificationPermissionRequest(
    onAnswered: (granted: Boolean) -> Unit,
): NotificationPermissionRequest {
    val context = LocalContext.current
    val currentOnAnswered by rememberUpdatedState(onAnswered)

    // Saveable so a rotation on the dead-end state doesn't bring back a button that can't work.
    var permanentlyDenied by rememberSaveable { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permanentlyDenied = !granted && context.isNotificationPermanentlyDenied()
        currentOnAnswered(granted)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && permanentlyDenied && context.notificationsAllowed()) {
                permanentlyDenied = false
                currentOnAnswered(true)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    return NotificationPermissionRequest(permanentlyDenied) {
        when {
            context.notificationsAllowed() -> currentOnAnswered(true)
            permanentlyDenied || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ->
                context.openNotificationSettings()
            else -> launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

/**
 * Whether Android will refuse to show the permission dialog again. Only meaningful straight
 * after a denial. Note it also reads as "permanent" if POST_NOTIFICATIONS is missing from the
 * manifest, because the dialog never appears at all.
 */
private fun Context.isNotificationPermanentlyDenied(): Boolean {
    val activity = findActivity() ?: return false
    return !ActivityCompat.shouldShowRequestPermissionRationale(
        activity,
        Manifest.permission.POST_NOTIFICATIONS,
    )
}

/** Opens this app's notification settings (its general page below Android 8). */
private fun Context.openNotificationSettings() {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
    }
    startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** Compose hands out a wrapped context; unwrap until the Activity turns up. */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}