package com.example.vinyl.ui.receive

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.vinyl.data.location.Distance
import com.example.vinyl.sensor.CompassDetector

/**
 * Turns the raw device heading from [CompassDetector] into the [CompassState] CompassScreen
 * renders. Same ON_START/ON_STOP lifecycle pattern as DetectShakeGesture.
 *
 * [readerLat]/[readerLng] are the reader's own saved location (used both to work out which way
 * the sender is, and to correct the compass for magnetic declination - see CompassDetector).
 * [senderLat]/[senderLng] are the letter's own location, from ArrivedRecordOption.
 *
 * If either pair is missing, there is nothing to point at, so this reports [CompassState.Unavailable]
 * immediately and never even registers the sensor - no battery spent listening for a direction
 * that cannot be shown.
 */
@Composable
internal fun rememberCompassHeading(
    enabled: Boolean,
    readerLat: Double?,
    readerLng: Double?,
    senderLat: Double?,
    senderLng: Double?,
): State<CompassState> {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val targetBearing = remember(readerLat, readerLng, senderLat, senderLng) {
        Distance.bearingOrNull(readerLat, readerLng, senderLat, senderLng)
    }

    val state = remember(targetBearing) {
        mutableStateOf<CompassState>(if (targetBearing == null) CompassState.Unavailable else CompassState.Loading)
    }

    val detector = remember(context, readerLat, readerLng) {
        CompassDetector(context, readerLat, readerLng)
    }

    DisposableEffect(enabled, lifecycleOwner, targetBearing) {
        if (!enabled || targetBearing == null) return@DisposableEffect onDispose {}

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    state.value = CompassState.Loading
                    val started = detector.enableSensor { deviceHeading ->
                        state.value = CompassState.Available(
                            deviceHeadingDegrees = deviceHeading,
                            targetBearingDegrees = targetBearing.toFloat(),
                        )
                    }
                    if (!started) state.value = CompassState.Unavailable
                }
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

    return state
}