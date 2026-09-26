package com.example.vinyl.ui.daily

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.vinyl.ui.theme.VinylPalette

data class UnopenedRecordUiState(
    /** Null when either end has no location — see [distanceNote] for why. */
    val distanceLabel: String?,
    val moodLabel: String,
    val sentTimeLabel: String,
    val distanceNote: String? = null,
)

/**
 * The sealed "you've got a record" reveal —  this is just the pre-open moment.
 *
 * Shake-to-open is real: [DetectShakeGesture] listens to the accelerometer for as long as this
 * screen is visible and calls [onOpen], the same callback the button uses. See ShakeDetector.kt
 * and ShakeAlgorithm.kt for the detection itself - deliberately not implemented here, so this
 * composable stays about layout, not sensor logic.
 */
@Composable
fun UnopenedRecordScreen(
    state: UnopenedRecordUiState,
    onOpen: () -> Unit,
    onBack: () -> Unit = {},
) {
    DetectShakeGesture(onShake = onOpen)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VinylPalette.Background)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = VinylPalette.TextPrimary)
            }
            Text(
                text = "UNOPENED RECORD",
                color = VinylPalette.TextMuted,
                fontSize = 11.sp,
                letterSpacing = 1.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(48.dp))
        }

        Spacer(modifier = Modifier.weight(1f))

        Box(
            modifier = Modifier.align(Alignment.CenterHorizontally),
            contentAlignment = Alignment.BottomStart,
        ) {
            Column(
                modifier = Modifier
                    .width(220.dp)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, VinylPalette.TextMuted.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                    .background(VinylPalette.PanelDark),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("sleeve art", color = VinylPalette.TextMuted, fontSize = 13.sp)
                Text(
                    "or browse files",
                    color = VinylPalette.TealAccent,
                    fontSize = 12.sp,
                    textDecoration = TextDecoration.Underline,
                )
            }

            Row(
                modifier = Modifier
                    .padding(bottom = 14.dp, start = 8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(VinylPalette.Background.copy(alpha = 0.92f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(modifier = Modifier.size(14.dp).clip(CircleShape).background(VinylPalette.TealAccent))
                Text("SEALED · DO NOT BEND", color = VinylPalette.TextMuted, fontSize = 10.sp, letterSpacing = 1.sp)
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = state.distanceLabel
                ?.let { "Someone $it away\nsent you a record" }
                ?: "Someone\nsent you a record",
            color = VinylPalette.TextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 28.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        state.distanceNote?.let { note ->
            Text(
                text = "Distance N/A · $note",
                color = VinylPalette.TextMuted,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "MOOD: ${state.moodLabel.uppercase()} · SENT ${state.sentTimeLabel}",
            color = VinylPalette.TextMuted,
            fontSize = 12.sp,
            letterSpacing = 0.5.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(20.dp))

        Row(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .clip(RoundedCornerShape(50))
                .background(VinylPalette.PanelDark)
                .border(1.dp, VinylPalette.TextMuted.copy(alpha = 0.2f), RoundedCornerShape(50))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(VinylPalette.TealAccent))
            Text("Shake your phone to slide it out", color = VinylPalette.TextPrimary, fontSize = 13.sp)
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = onOpen,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(
                containerColor = VinylPalette.Cream,
                contentColor = VinylPalette.Background,
            ),
        ) {
            Text("Open it now", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "Opening drops the needle and unfolds the letter. It buzzes once when the record catches.",
            color = VinylPalette.TextMuted,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0D0D, widthDp = 393, heightDp = 852)
@Composable
private fun UnopenedRecordScreenPreview() {
    UnopenedRecordScreen(
        state = UnopenedRecordUiState(
            distanceLabel = "2.4 km",
            moodLabel = "Homesick",
            sentTimeLabel = "6:20 AM",
        ),
        onOpen = {},
    )
}