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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.vinyl.data.GenreOptions
import com.example.vinyl.data.MoodOption
import com.example.vinyl.data.MoodOptions
import com.example.vinyl.data.MoodTag
import com.example.vinyl.ui.theme.VinylPalette

@Composable
fun MoodQuestionnaireScreen(
    selectedMood: MoodTag?,
    selectedGenres: Set<String>,
    onMoodSelected: (MoodTag) -> Unit,
    onGenreToggled: (String) -> Unit,
    onSubmit: () -> Unit,
    onLetCrateDecide: () -> Unit,
    onBack: () -> Unit = {},
) {
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
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = VinylPalette.TextPrimary)
            }
            Text(
                text = "BEFORE THE NEEDLE DROPS",
                color = VinylPalette.TextMuted,
                fontSize = 11.sp,
                letterSpacing = 1.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(48.dp)) // balances the back button so the label centers
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "How did today find you?",
                color = VinylPalette.TextPrimary,
                fontSize = 26.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 32.sp,
            )
            Text(
                // Used to add "Your answer stays on this phone." — true while this flow ran on sample
                // data, false since it calls request_recommendations, which stores the mood.
                text = "Three records get pulled from the crate to match.",
                color = VinylPalette.TextMuted,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            MoodOptions.all.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEach { option ->
                        DailyMoodCard(
                            option = option,
                            selected = option.tag == selectedMood,
                            onClick = { onMoodSelected(option.tag) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "GENRE · optional — nudges the pull",
                color = VinylPalette.TextMuted,
                fontSize = 11.sp,
                letterSpacing = 1.sp,
            )
            GenreFlowRow(
                items = GenreOptions.all,
                selected = selectedGenres,
                onToggle = onGenreToggled,
            )
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(),
        ) {
            OutlinedButton(
                onClick = onSubmit,
                enabled = selectedMood != null,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(50),
                border = BorderStroke(
                    1.5.dp,
                    if (selectedMood != null) VinylPalette.Cream else VinylPalette.TextMuted.copy(alpha = 0.3f),
                ),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = VinylPalette.Cream,
                    disabledContentColor = VinylPalette.TextMuted,
                ),
            ) {
                Text(
                    text = if (selectedMood != null) "Pull three records" else "Pick a mood first",
                    fontWeight = FontWeight.SemiBold,
                )
            }
            TextButton(onClick = onLetCrateDecide) {
                Text(
                    "Let the crate decide",
                    color = VinylPalette.TealAccent,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun DailyMoodCard(
    option: MoodOption,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .height(76.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) VinylPalette.TealAccent.copy(alpha = 0.15f) else VinylPalette.PanelDark)
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) VinylPalette.TealAccent else VinylPalette.TextMuted.copy(alpha = 0.2f),
                shape = RoundedCornerShape(10.dp),
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(CircleShape)
                .border(
                    width = 1.5.dp,
                    color = if (selected) VinylPalette.TealAccent else VinylPalette.TextMuted.copy(alpha = 0.4f),
                    shape = CircleShape,
                )
                .background(if (selected) VinylPalette.TealAccent else Color.Transparent),
        )
        Column {
            Text(option.title, color = VinylPalette.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(
                option.subtitle,
                color = VinylPalette.TextMuted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun GenreFlowRow(items: List<String>, selected: Set<String>, onToggle: (String) -> Unit) {
    val rows = remember(items) {
        val chunks = mutableListOf<MutableList<String>>()
        var current = mutableListOf<String>()
        var lineLen = 0
        items.forEach { genre ->
            val approxLen = genre.length + 3
            if (lineLen + approxLen > 30 && current.isNotEmpty()) {
                chunks.add(current); current = mutableListOf(); lineLen = 0
            }
            current.add(genre); lineLen += approxLen
        }
        if (current.isNotEmpty()) chunks.add(current)
        chunks
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { genre ->
                    val isSelected = genre in selected
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (isSelected) VinylPalette.TealAccent else VinylPalette.PanelDark)
                            .border(1.dp, VinylPalette.TextMuted.copy(alpha = 0.2f), RoundedCornerShape(50))
                            .clickable(onClick = { onToggle(genre) })
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        Text(
                            genre,
                            color = if (isSelected) VinylPalette.Background else VinylPalette.TextPrimary,
                            fontSize = 13.sp,
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0D0D, widthDp = 393, heightDp = 852)
@Composable
private fun MoodQuestionnaireScreenPreview() {
    MoodQuestionnaireScreen(
        selectedMood = MoodTag.Nostalgic,
        selectedGenres = setOf("K-pop"),
        onMoodSelected = {},
        onGenreToggled = {},
        onSubmit = {},
        onLetCrateDecide = {},
    )
}