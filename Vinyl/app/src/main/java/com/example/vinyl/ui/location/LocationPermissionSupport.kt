package com.example.vinyl.ui.location

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/** The pair we ask for together: coarse is what we need, fine makes the dialog offer both. */
internal val LOCATION_PERMISSIONS = arrayOf(
    Manifest.permission.ACCESS_COARSE_LOCATION,
    Manifest.permission.ACCESS_FINE_LOCATION,
)

/**
 * The primary button's behaviour on any screen that asks for location.
 *
 * [permanentlyDenied] is true once Android has stopped showing its dialog; [request] then opens
 * this app's system settings instead of the dialog.
 */
internal class LocationPermissionRequest(val permanentlyDenied: Boolean, val request: () -> Unit)

/**
 * Asks for location, and notices when Android has stopped asking.
 *
 * Permanent denial is decided inside the result callback, not during composition. Device testing
 * showed why: a second denial produces exactly the same UI state as the first, so nothing
 * recomposes and a check made during composition never re-runs. The callback is also the only
 * moment `shouldShowRequestPermissionRationale` reliably means "permanent" — it is false before
 * the first ask too.
 *
 * When the user comes back from system settings having switched location on, this carries on by
 * itself via [onAnswered], so they don't have to find the button again.
 */
@Composable
internal fun rememberLocationPermissionRequest(
    hasPermission: () -> Boolean,
    onAnswered: () -> Unit,
): LocationPermissionRequest {
    val context = LocalContext.current
    val currentHasPermission by rememberUpdatedState(hasPermission)
    val currentOnAnswered by rememberUpdatedState(onAnswered)

    // Saveable so a rotation on the dead-end screen doesn't bring back a button that can't work.
    var permanentlyDenied by rememberSaveable { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        // Granting coarse only is a full success, so "denied" means nothing at all was granted.
        permanentlyDenied = results.values.none { it } && context.isLocationPermanentlyDenied()
        currentOnAnswered()
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && permanentlyDenied && currentHasPermission()) {
                permanentlyDenied = false
                currentOnAnswered()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    return LocationPermissionRequest(permanentlyDenied) {
        if (permanentlyDenied) context.openAppSettings() else launcher.launch(LOCATION_PERMISSIONS)
    }
}

/**
 * Whether Android will refuse to show the permission dialog again. Only meaningful straight
 * after a denial — before the first ask this reads the same as a permanent refusal.
 */
private fun Context.isLocationPermanentlyDenied(): Boolean {
    val activity = findActivity() ?: return false
    return LOCATION_PERMISSIONS.none { ActivityCompat.shouldShowRequestPermissionRationale(activity, it) }
}

/** Opens this app's page in system settings, the only remaining route once the dialog is gone. */
private fun Context.openAppSettings() {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", packageName, null),
    ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
    startActivity(intent)
}

/** Compose hands out a wrapped context; unwrap until the Activity turns up. */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
