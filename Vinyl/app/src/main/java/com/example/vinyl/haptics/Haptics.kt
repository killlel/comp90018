package com.example.vinyl.haptics

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * A single short buzz - the confirmation this screen's own copy promises ("It buzzes once when
 * the record catches"). Fires the same way whether the record was opened by a shake or by
 * tapping the button - both call this, not just the shake.
 *
 * Handles the three eras of Android's vibration API:
 *  - API 31+ (S): the Vibrator is fetched through VibratorManager - the older
 *    getSystemService(VIBRATOR_SERVICE) path still technically works here but is deprecated.
 *  - API 26-30 (O-R): VibrationEffect.createOneShot() gives a shaped, one-shot buzz.
 *  - Below API 26: falls back to the old vibrate(ms) call, the only option that exists there.
 *
 * Safe with no vibration hardware, or a missing/misbehaving vibrator service: a missing haptic
 * buzz is never worth crashing the app over, so every failure path is swallowed silently.
 */
fun Context.vibrateOnce(durationMs: Long = 60) {
    try {
        val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

        if (vibrator == null || !vibrator.hasVibrator()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(durationMs)
        }
    } catch (e: Exception) {
        // Swallowed on purpose - see docstring.
    }
}