package com.example.vinyl.ui.home

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * The feel of the needle landing.
 *
 * Three tiers, because the effect that actually feels like a stylus hitting a groove only exists
 * on API 30+. Below that it degrades to a predefined click, then to a plain pulse, then to
 * nothing. Each step down is less convincing, none of them crash.
 *
 * Nothing here throws: a device with no vibrator, a user who turned haptics off, or a primitive
 * the hardware does not implement all end up as silence rather than an error. Call it freely.
 */
class TurntableHaptics(context: Context) {

    private val vibrator: Vibrator? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(VibratorManager::class.java)
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }.getOrNull()?.takeIf { it.hasVibrator() }

    /** Reported per-primitive: a device can have a vibrator and still not implement THUD. */
    private val supportsPrimitives: Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            runCatching {
                vibrator?.areAllPrimitivesSupported(
                    VibrationEffect.Composition.PRIMITIVE_THUD,
                    VibrationEffect.Composition.PRIMITIVE_LOW_TICK,
                ) == true
            }.getOrDefault(false)

    /** The stylus settling into the groove — one soft, weighty landing. */
    fun needleDrop() {
        val v = vibrator ?: return
        runCatching {
            when {
                supportsPrimitives -> v.vibrate(
                    VibrationEffect.startComposition()
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, THUD_SCALE)
                        .compose(),
                )

                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> v.vibrate(
                    VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK),
                )

                else -> v.vibrate(
                    VibrationEffect.createOneShot(FALLBACK_MILLIS, FALLBACK_AMPLITUDE),
                )
            }
        }
    }

    /** The arm lifting clear — lighter than the drop, so the pair reads as down then up. */
    fun needleLift() {
        val v = vibrator ?: return
        runCatching {
            when {
                supportsPrimitives -> v.vibrate(
                    VibrationEffect.startComposition()
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, TICK_SCALE)
                        .compose(),
                )

                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> v.vibrate(
                    VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK),
                )

                else -> Unit // A plain buzz for a lift is worse than staying quiet.
            }
        }
    }

    private companion object {
        const val THUD_SCALE = 0.75f
        const val TICK_SCALE = 0.4f

        /** Pre-Q has no effect vocabulary, only a duration and a strength. */
        const val FALLBACK_MILLIS = 28L
        const val FALLBACK_AMPLITUDE = 140
    }
}

@Composable
fun rememberTurntableHaptics(): TurntableHaptics {
    val context = LocalContext.current
    return remember(context) { TurntableHaptics(context.applicationContext) }
}
