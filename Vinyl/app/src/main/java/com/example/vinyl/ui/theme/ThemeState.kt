package com.example.vinyl.ui.theme

import android.content.Context
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode

/**
 * The accent colours the user can pick in Settings.
 *
 * [color] is the accent on the dark page; [onCream] is a darker shade of it for anything drawn on
 * a cream letter or seal, where the light accent would wash out. Red is left out on purpose: it
 * means Sign out and Remove.
 *
 * The fills are tuned in OKLCH to sit near Teal's lightness (0.875) and vividness on the dark
 * page. Pink and Violet sit a little darker (0.79 and 0.75) because sRGB has no brighter version
 * of those hues that isn't pastel.
 */
enum class Accent(val label: String, val color: Color, val onCream: Color) {
    Teal("Teal", Color(0xFF77EDE5), Color(0xFF0A6F69)),
    Lime("Lime", Color(0xFFAEF631), Color(0xFF4A7300)),
    Sun("Sun", Color(0xFFFFBE3D), Color(0xFF8A5A00)),
    Pink("Pink", Color(0xFFFF7ACE), Color(0xFFB8326F)),
    Violet("Violet", Color(0xFFB58AFF), Color(0xFF6D4BD8)),
}

/**
 * App-wide appearance choices. [accent] is snapshot state, so VinylPalette.TealAccent and
 * VinylColors.Teal (both read it) recompose and redraw every screen when it changes, without any
 * call site knowing about it.
 */
object ThemeState {
    private const val PREFS = "appearance"
    private const val KEY_ACCENT = "accent"

    var accent by mutableStateOf(Accent.Teal)
        private set

    private var loaded = false

    /**
     * Reads the saved accent once per process. Later calls do nothing. A name saved by an older
     * build that no longer exists (Blue, Amber, Purple) falls back to Teal.
     */
    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val saved = prefs(context).getString(KEY_ACCENT, null)
        accent = Accent.entries.firstOrNull { it.name == saved } ?: Accent.Teal
    }

    fun setAccent(context: Context, value: Accent) {
        accent = value
        prefs(context).edit().putString(KEY_ACCENT, value.name).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

/** [base] with the saved accent as its primary. Loads the saved accent on first use. */
@Composable
internal fun vinylColorScheme(base: ColorScheme): ColorScheme {
    val context = LocalContext.current
    val inPreview = LocalInspectionMode.current
    remember(context) { if (!inPreview) ThemeState.load(context) }
    val accent = ThemeState.accent
    return remember(base, accent) {
        base.copy(
            primary = accent.color,
            surfaceVariant = accentPanelColors(accent.color).panelWell,
        )
    }
}
