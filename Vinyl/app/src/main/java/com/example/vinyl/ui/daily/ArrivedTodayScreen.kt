package com.example.vinyl.ui.daily

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.vinyl.data.MoodTag
import com.example.vinyl.ui.theme.VinylPalette

data class ArrivedRecordOption(
    val id: String,
    val trackName: String,
    val artistName: String,
    val messagePreview: String,
    val moodLabel: String,
    /** Null when either end has no location — see [distanceNote] for why. */
    val distanceLabel: String?,
    val artworkUrl: String? = null,
    /** Why [distanceLabel] is missing, for the screens with room to say so. */
    val distanceNote: String? = null,
    val mood: MoodTag? = null,
    /** The record's own id. Reacting and keeping act on this, not on [id] (the delivery's id). */
    val submissionId: String? = null,
    /** How long ago the sender sent it, for example "Just now", "3 hr. ago" or "Yesterday". */
    val sentTimeLabel: String = "Recently",
)

data class ArrivedTodayUiState(
    val moodLabel: String,
    val genreLabel: String? = null,
    val fallbackNote: String? = null,
    val options: List<ArrivedRecordOption>,
)

/**
 * "One of three" picker shown after the mood questionnaire — deliberately just this list, not
 * the full Record Room home screen it sits on top of in the mockup.
 */
@Composable
fun ArrivedTodayScreen(
    state: ArrivedTodayUiState,
    onSelect: (ArrivedRecordOption) -> Unit,
    onNotNow: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VinylPalette.SheetSurface)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Text("Arrived today", color = VinylPalette.TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
            Text(
                text = listOfNotNull(state.moodLabel.uppercase(), state.genreLabel?.uppercase()).joinToString(" · "),
                color = VinylPalette.TextMuted,
                fontSize = 11.sp,
                letterSpacing = 1.sp,
            )
        }

        Text(
            text = "One at a time. Choosing a record puts it on the player and unfolds the letter that came with it.",
            color = VinylPalette.TextMuted,
            fontSize = 13.sp,
            lineHeight = 18.sp,
        )

        state.fallbackNote?.let {
            Text(
                text = it.uppercase(),
                color = VinylPalette.TealAccent.copy(alpha = 0.85f),
                fontSize = 10.sp,
                letterSpacing = 0.5.sp,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            state.options.forEach { option ->
                ArrivedRecordCard(option = option, onClick = { onSelect(option) })
            }
        }

        OutlinedButton(
            onClick = onNotNow,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(50),
            border = BorderStroke(1.5.dp, VinylPalette.TextMuted.copy(alpha = 0.3f)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = VinylPalette.TextPrimary),
        ) {
            Text("Not now", fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun ArrivedRecordCard(option: ArrivedRecordOption, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(VinylPalette.PanelDark)
            .border(1.dp, VinylPalette.TextMuted.copy(alpha = 0.15f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(VinylPalette.Background),
            contentAlignment = Alignment.Center,
        ) {
            if (option.artworkUrl != null) {
                AsyncImage(
                    model = option.artworkUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                // Not every song has artwork (or it hasn't been saved with the record).
                Text(
                    "album\nart",
                    color = VinylPalette.TextMuted,
                    fontSize = 9.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 11.sp,
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(VinylPalette.TealAccent))
                Text(
                    text = "${option.moodLabel.uppercase()} · ${option.distanceLabel ?: "N/A"}",
                    color = VinylPalette.TextMuted,
                    fontSize = 10.sp,
                    letterSpacing = 0.5.sp,
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(option.trackName, color = VinylPalette.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(option.artistName, color = VinylPalette.TextMuted, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = option.messagePreview,
                color = VinylPalette.TextMuted,
                fontSize = 12.sp,
                fontStyle = FontStyle.Italic,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0D0D, widthDp = 393, heightDp = 852)
@Composable
private fun ArrivedTodayScreenPreview() {
    ArrivedTodayScreen(
        state = ArrivedTodayUiState(
            moodLabel = "Homesick",
            genreLabel = "K-pop",
            fallbackNote = "No K-pop in today's crate — closest three instead",
            options = listOf(
                ArrivedRecordOption("1", "Slow Rain, Rooftop", "Marin Ochre", "I played this the night I moved out…", "Homesick", "2.4 km"),
                ArrivedRecordOption("2", "Kitchen Light, 2am", "Sora Lin", "My mother never turned the hall lamp off…", "Homesick", "5.1 km"),
                ArrivedRecordOption("3", "Long Way From Cebu", "Teo Marasigan", "Third winter here and it still surprises me…", "Homesick", "18 km"),
            ),
        ),
        onSelect = {},
        onNotNow = {},
    )
}