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
import com.example.vinyl.ui.theme.VinylPalette

/**
 * The button area shared by the location page (3) and the notification page (4).
 *
 * Both pages put their buttons in this one block, pinned to the bottom of the page at a fixed
 * height, so the buttons sit at exactly the same height on both and are the same size. Only the
 * text above them is free to differ. Pages 1 and 2 keep their own layout.
 *
 * To move the buttons on both pages at once, change [OnboardingActionAreaHeight]: a larger value
 * lifts them, a smaller one lowers them. To resize them, change [OnboardingButtonHeight].
 */
internal val OnboardingActionAreaHeight: Dp = 260.dp
internal val OnboardingButtonHeight: Dp = 56.dp

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
) {
    Button(
        onClick = onClick,
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
            fontSize = 15.sp,
        )
    }
}

/** The muted text button under it ("Not now"). */
@Composable
internal fun OnboardingSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TextButton(onClick = onClick, modifier = modifier.padding(top = 4.dp)) {
        Text(text, color = VinylPalette.TextMuted, fontSize = 14.sp)
    }
}