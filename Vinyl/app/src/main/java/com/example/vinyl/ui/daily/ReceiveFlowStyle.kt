package com.example.vinyl.ui.daily

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.vinyl.ui.theme.PoppinsFontFamily
import com.example.vinyl.ui.theme.VinylPalette

/**
 * Tokens shared by the receive flow after the mood sheet: Arrived today, the unopened envelope
 * and the music card. Same values as the mood sheet's own panel styling.
 */
internal object ReceiveFlowStyle {
    val PanelBrush get() = VinylPalette.PanelColors.panelGradient
    val PanelBorder get() = VinylPalette.PanelColors.panelBorder
    val PanelShape = RoundedCornerShape(20.dp)
    val IconWell get() = VinylPalette.PanelColors.panelWell

    fun text(
        size: TextUnit,
        weight: FontWeight,
        lineHeight: TextUnit = TextUnit.Unspecified,
        italic: Boolean = false,
    ) = TextStyle(
        fontFamily = PoppinsFontFamily,
        fontSize = size,
        fontWeight = weight,
        lineHeight = lineHeight,
        fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
    )

    /** Page title: Medium 20/28. */
    val Title = text(20.sp, FontWeight.Medium, 28.sp)

    /** Helper line under a title: Regular 14/20, drawn in Cream at 55%. */
    val Helper = text(14.sp, FontWeight.Normal, 20.sp)
}
