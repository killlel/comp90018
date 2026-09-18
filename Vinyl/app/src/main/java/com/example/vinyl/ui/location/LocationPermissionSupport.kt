package com.example.vinyl.ui.location

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.app.ActivityCompat

/** The pair we ask for together: coarse is what we need, fine makes the dialog offer both. */
internal val LOCATION_PERMISSIONS = arrayOf(
    Manifest.permission.ACCESS_COARSE_LOCATION,
    Manifest.permission.ACCESS_FINE_LOCATION,
)

/**
 * Whether Android will refuse to show the permission dialog again.
 *
 * `shouldShowRequestPermissionRationale` returns false in two opposite situations — before the
 * first ask, and after the user has denied firmly enough that the system stops asking. Only the
 * second is a dead end, so the caller has to tell us whether the dialog has already been shown
 * ([hasAsked]).
 */
internal fun Context.isLocationPermanentlyDenied(hasAsked: Boolean): Boolean {
    if (!hasAsked) return false
    val activity = findActivity() ?: return false
    return LOCATION_PERMISSIONS.none { ActivityCompat.shouldShowRequestPermissionRationale(activity, it) }
}

/**
 * Opens this app's page in system settings, the only remaining route once the dialog is gone.
 */
internal fun Context.openAppSettings() {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", packageName, null),
    ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
    startActivity(intent)
}

/** Compose hands out a wrapped context; unwrap until the Activity turns up. */
internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
