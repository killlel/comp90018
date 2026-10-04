package com.example.vinyl.ui.onboarding

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.vinyl.ui.theme.PoppinsFontFamily
import com.example.vinyl.ui.theme.VinylPalette

/** Shared dimensions retained for permission-page callers. */
internal val OnboardingActionAreaHeight: Dp = 128.dp
internal val OnboardingButtonHeight: Dp = 56.dp
internal val OnboardingHorizontalPadding: Dp = 28.dp
internal val OnboardingActionMinimumHeight: Dp = 112.dp
internal val OnboardingBottomPadding: Dp = 20.dp

/** Fixed-height block at the bottom of a page. Its content is stacked from the top. */
@Composable
internal fun OnboardingActionArea(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(OnboardingActionAreaHeight),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}

/** The big pill button ("Share my city", "Turn On", ...). */
@Composable
internal fun OnboardingPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(OnboardingButtonHeight),
        shape = RoundedCornerShape(50),
        colors = ButtonDefaults.buttonColors(
            containerColor = VinylPalette.TealAccent,
            contentColor = VinylPalette.Background,
        ),
    ) {
        Text(
            text = text,
            fontWeight = FontWeight.Medium,
            fontFamily = PoppinsFontFamily,
            fontSize = 18.sp,
            lineHeight = 24.sp,
        )
    }
}

/** The muted text button under it ("Not now"). */
@Composable
internal fun OnboardingSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    TextButton(onClick = onClick, enabled = enabled, modifier = modifier.padding(top = 4.dp)) {
        Text(text, color = VinylPalette.TextMuted, fontSize = 16.sp, lineHeight = 24.sp, fontFamily = PoppinsFontFamily)
    }
}
