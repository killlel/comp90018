package com.example.vinyl.ui.onboarding

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.vinyl.ui.theme.VinylPalette

@Composable
fun NamePage(
    viewModel: OnboardingViewModel,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()
    OnboardingPageLayout(
        title = "Choose your\ndisplay name",
        description = "This is your anonymous identity on Vinyl. Refresh the name until it feels right.",
        modifier = modifier,
        centreContentInVisualArea = true,
        actions = {
            OnboardingPrimaryButton("Continue", onNext,
                enabled = !state.isLoadingProfile && !state.isRerollingName && state.username.isNotBlank())
        },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(64.dp)
                    .clip(RoundedCornerShape(50))
                    .border(1.dp, VinylPalette.TealAccent, RoundedCornerShape(50))
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                FitText(
                    text = if (state.isLoadingProfile) "…" else state.username,
                    color = Color.White,
                    maxSp = 28f,
                    minSp = 14f,
                )
            }
            IconButton(
                onClick = viewModel::rerollUsername,
                enabled = !state.isLoadingProfile && !state.isRerollingName,
            ) {
                if (state.isRerollingName) {
                    CircularProgressIndicator(
                        color = VinylPalette.TextMuted,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(22.dp),
                    )
                } else {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = "Try another name",
                        tint = VinylPalette.TealAccent,
                    )
                }
            }
        }
        state.errorMessage?.let {
            Text(
                it,
                color = VinylPalette.TealAccent,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp),
            )
        }

    }
}

@Composable
private fun FitText(
    text: String,
    color: Color,
    maxSp: Float,
    minSp: Float,
) {
    var sizeSp by remember(text) { mutableFloatStateOf(maxSp) }
    var ready by remember(text) { mutableStateOf(false) }
    Text(
        text = text,
        color = color,
        fontSize = sizeSp.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        softWrap = false,
        onTextLayout = { result ->
            if (result.didOverflowWidth && sizeSp > minSp) {
                sizeSp = maxOf(minSp, sizeSp * 0.92f)
            } else {
                ready = true
            }
        },
        modifier = Modifier.drawWithContent { if (ready) drawContent() },
    )
}
