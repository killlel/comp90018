package com.example.vinyl.ui.daily

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.vinyl.sensor.ShakeDetector

/**
 * Calls [onShake] whenever the device is shaken while this composable is in the composition.
 *
 * Always unregisters when the composable leaves the composition too - e.g. once the record opens
 * and this screen is replaced - so nothing is left listening after the screen is gone.
 *
 * Safe on a device with no accelerometer, or one whose sensor service misbehaves: see
 * ShakeDetector.enableSensor(), which never throws. Shaking just has no effect, and the on-screen
 * "Open it now" button still works either way.
 */
@Composable
internal fun DetectShakeGesture(enabled: Boolean = true, onShake: () -> Unit) {
    val context = LocalContext.current
    val currentOnShake = rememberUpdatedState(onShake)
    val lifecycleOwner = LocalLifecycleOwner.current

    // One instance per composition, matching how MainActivity holds one `barometer` for the
    // Activity's lifetime rather than creating a new one on every start.
    val detector = remember(context) { ShakeDetector(context) }

    DisposableEffect(enabled, lifecycleOwner) {
        if (!enabled) return@DisposableEffect onDispose {}

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> detector.enableSensor { currentOnShake.value() }
                Lifecycle.Event.ON_STOP -> detector.disableSensor()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            detector.disableSensor()
        }
    }
}