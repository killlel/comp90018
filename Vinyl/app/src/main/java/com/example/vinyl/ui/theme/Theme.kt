package com.example.vinyl.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

// The product design (Figma hi-fi kit) is dark-themed throughout, so Vinyl has a single fixed
// color scheme rather than switching with the system light/dark setting.
private val VinylDarkColorScheme = darkColorScheme(
    primary = VinylColors.Teal,
    onPrimary = VinylColors.Charcoal,
    secondary = VinylColors.Rust,
    onSecondary = VinylColors.Cream,
    tertiary = VinylColors.Rust,
    onTertiary = VinylColors.Cream,
    background = VinylColors.Ink,
    onBackground = VinylColors.Cream,
    surface = VinylColors.Ink,
    onSurface = VinylColors.Cream,
    surfaceVariant = VinylColors.Surface,
    onSurfaceVariant = VinylColors.Cream,
)

@Composable
fun VinylTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = VinylDarkColorScheme,
        typography = Typography,
        content = content
    )
}
