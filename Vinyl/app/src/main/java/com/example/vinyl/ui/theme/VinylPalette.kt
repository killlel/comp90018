package com.example.vinyl.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlin.math.abs

internal data class AccentPanelColors(
    val panelGradient: Brush,
    val panelBorder: Color,
    val panelWell: Color,
)

internal fun accentPanelColors(accent: Color): AccentPanelColors {
    val hue = accent.hueDegrees()
    return AccentPanelColors(
        panelGradient = Brush.verticalGradient(
            listOf(
                hslColor(hue, saturation = 0.12f, lightness = 0.07f),
                hslColor(hue, saturation = 0.12f, lightness = 0.20f),
            )
        ),
        panelBorder = hslColor(hue, saturation = 0.10f, lightness = 0.27f),
        panelWell = hslColor(hue, saturation = 0.11f, lightness = 0.16f),
    )
}

private fun Color.hueDegrees(): Float {
    val maximum = maxOf(red, green, blue)
    val minimum = minOf(red, green, blue)
    val delta = maximum - minimum
    if (delta == 0f) return 0f

    val hue = when (maximum) {
        red -> 60f * (((green - blue) / delta) % 6f)
        green -> 60f * (((blue - red) / delta) + 2f)
        else -> 60f * (((red - green) / delta) + 4f)
    }
    return if (hue < 0f) hue + 360f else hue
}

private fun hslColor(hue: Float, saturation: Float, lightness: Float): Color {
    val chroma = (1f - abs(2f * lightness - 1f)) * saturation
    val segment = hue / 60f
    val x = chroma * (1f - abs(segment % 2f - 1f))
    val (redPart, greenPart, bluePart) = when {
        segment < 1f -> Triple(chroma, x, 0f)
        segment < 2f -> Triple(x, chroma, 0f)
        segment < 3f -> Triple(0f, chroma, x)
        segment < 4f -> Triple(0f, x, chroma)
        segment < 5f -> Triple(x, 0f, chroma)
        else -> Triple(chroma, 0f, x)
    }
    val match = lightness - chroma / 2f
    return Color(redPart + match, greenPart + match, bluePart + match)
}

object VinylPalette {
    val Background = Color(0xFF0D0D0D)
    val PanelDark = Color(0xFF0E0E0E)
    val RecordDark = Color(0xFF141414)
    val SheetSurface = Color(0xFF1C1C1C)

    val TealAccent: Color get() = ThemeState.accent.color
    internal val PanelColors: AccentPanelColors get() = accentPanelColors(TealAccent)
    val BrownAccent = Color(0xFF6E3A2C)

    val Cream = Color(0xFFF4F0EA)
    val TextPrimary = Color(0xFFF4F0EA)
    val TextMuted = Color(0xFFF4F0EA).copy(alpha = 0.55f)
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0D0D, widthDp = 360)
@Composable
private fun AccentPanelColorsPreview() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VinylPalette.Background)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AccentPanelPreview(Accent.Teal)
        AccentPanelPreview(Accent.Sun)
    }
}

@Composable
private fun AccentPanelPreview(accent: Accent) {
    val colors = accentPanelColors(accent.color)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .background(colors.panelGradient, RoundedCornerShape(20.dp))
            .border(1.dp, colors.panelBorder, RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).background(colors.panelWell, CircleShape))
        Spacer(Modifier.width(12.dp))
        Text(accent.label, color = VinylPalette.Cream)
    }
}
