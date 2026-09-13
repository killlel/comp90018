package com.example.vinyl.ui.received

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.vinyl.data.MoodOptions
import com.example.vinyl.data.MoodTag
import com.example.vinyl.ui.theme.VinylPalette

/**
 * What the recipient sees for one received card
 */
data class ReceivedCardUiState(
    val trackName: String,
    val artistName: String,
    val artworkUrl: String? = null,
    val mood: MoodTag? = null,
    val message: String,
    val senderDistanceLabel: String? = null,
    val senderWeatherLabel: String? = null,
    val sentTimeLabel: String,
    val isKept: Boolean = false,
    val isLiked: Boolean = false,
)

@Composable
fun ReceivedCardScreen(
    state: ReceivedCardUiState,
    onClose: () -> Unit = {},
    onKeep: () -> Unit = {},
    onToggleLike: () -> Unit = {},
) {
    val moodOption = MoodOptions.all.firstOrNull { it.tag == state.mood }

    // Same dark-on-cream colors as CardPreview's letter card
    val letterTextPrimary = VinylPalette.Background
    val letterTextMuted = VinylPalette.Background.copy(alpha = 0.6f)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VinylPalette.SheetSurface)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "LETTER CARD · ANONYMOUS",
                color = VinylPalette.TextMuted,
                fontSize = 11.sp,
                letterSpacing = 1.sp,
            )
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "Close", tint = VinylPalette.TextPrimary)
            }
        }

        // The letter TODO: artworkUrl isn't wired to real data yet
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(VinylPalette.Cream)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.artworkUrl != null) {
                    AsyncImage(
                        model = state.artworkUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(8.dp)),
                    )
                }
                Column {
                    Text(state.trackName, color = letterTextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Medium)
                    Text(state.artistName, color = letterTextMuted, fontSize = 14.sp)
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(VinylPalette.Background.copy(alpha = 0.05f))
                    .padding(16.dp),
            ) {
                Text(
                    text = state.message,
                    color = letterTextPrimary,
                    fontSize = 18.sp,
                    lineHeight = 26.sp,
                )
            }

            moodOption?.let {
                LetterInfoBlock(
                    label = "MOOD",
                    value = it.title,
                    textPrimary = letterTextPrimary,
                    textMuted = letterTextMuted,
                )
            }

            Text(
                text = "— ${state.senderDistanceLabel?.let { "someone $it away" } ?: "someone, somewhere"}",
                color = letterTextMuted,
                fontSize = 12.sp,
                modifier = Modifier.align(Alignment.End),
            )
        }

        if (state.senderDistanceLabel != null || state.senderWeatherLabel != null) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "CAME WITH THE RECORD",
                    color = VinylPalette.TextMuted,
                    fontSize = 10.sp,
                    letterSpacing = 1.sp,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                ) {
                    state.senderDistanceLabel?.let {
                        ReceivedInfoBlock(label = "DISTANCE", value = it, modifier = Modifier.weight(1f).fillMaxHeight())
                    }
                    state.senderWeatherLabel?.let {
                        ReceivedInfoBlock(label = "THEIR SKY", value = it, modifier = Modifier.weight(1f).fillMaxHeight())
                    }
                    ReceivedInfoBlock(label = "SENT", value = state.sentTimeLabel, modifier = Modifier.weight(1f).fillMaxHeight())
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = onKeep,
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(50),
                border = BorderStroke(1.5.dp, VinylPalette.Cream),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = VinylPalette.Cream),
            ) {
                Text(if (state.isKept) "Kept" else "Keep this record", fontWeight = FontWeight.SemiBold)
            }
            IconButton(
                onClick = onToggleLike,
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(VinylPalette.PanelDark)
                    .border(1.dp, VinylPalette.TextMuted.copy(alpha = 0.3f), CircleShape),
            ) {
                Icon(
                    imageVector = if (state.isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = "React",
                    tint = if (state.isLiked) VinylPalette.TealAccent else VinylPalette.TextMuted,
                )
            }
        }

        Text(
            text = "Reactions stay anonymous. The sender only sees that someone listened.",
            color = VinylPalette.TextMuted,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        )
    }
}

@Composable
private fun LetterInfoBlock(
    label: String,
    value: String,
    textPrimary: Color,
    textMuted: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(textPrimary.copy(alpha = 0.06f))
            .border(1.dp, textPrimary.copy(alpha = 0.15f), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(value, color = textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(2.dp))
        Text(label, color = textMuted, fontSize = 10.sp, letterSpacing = 1.sp)
    }
}

@Composable
private fun ReceivedInfoBlock(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .border(1.dp, VinylPalette.TextMuted.copy(alpha = 0.15f), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            value,
            color = VinylPalette.TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(label, color = VinylPalette.TextMuted, fontSize = 9.sp, letterSpacing = 1.sp)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0D0D, widthDp = 393, heightDp = 852)
@Composable
private fun ReceivedCardScreenPreview() {
    ReceivedCardScreen(
        state = ReceivedCardUiState(
            trackName = "Text Back Anthem",
            artistName = "Rio Han",
            mood = MoodTag.Romantic,
            message = "I have read the same three-word message nine times tonight. This is the song I put on to stop myself replying too fast. It did not work, obviously.",
            senderDistanceLabel = "1.2 km",
            senderWeatherLabel = "Clear, 20°",
            sentTimeLabel = "11:11 PM",
        ),
    )
}