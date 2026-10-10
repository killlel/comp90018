package com.example.vinyl.ui.daily

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.vinyl.ui.theme.PoppinsFontFamily

/**
 * Tokens shared by the receive flow after the mood sheet: Arrived today, the unopened envelope
 * and the music card. Same values as the mood sheet's own panel styling.
 */
internal object ReceiveFlowStyle {
    val PanelBrush = Brush.verticalGradient(listOf(Color(0xFF111413), Color(0xFF2E3938)))
    val PanelBorder = Color(0xFF3E4A49)
    val PanelShape = RoundedCornerShape(20.dp)
    val IconWell = Color(0xFF242A2A)

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
