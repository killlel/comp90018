package com.example.vinyl.ui.daily

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.vinyl.R
import com.example.vinyl.data.MoodOption
import com.example.vinyl.data.MoodOptions
import com.example.vinyl.data.MoodTag
import com.example.vinyl.data.onboarding.GenreOption
import com.example.vinyl.ui.theme.PoppinsFontFamily
import com.example.vinyl.ui.theme.VinylPalette

// Mood panel / genre row styling from the sheet mockup. Local to this screen on purpose.
private val PanelBrush = Brush.verticalGradient(listOf(Color(0xFF111413), Color(0xFF2E3938)))
private val PanelBorder = Color(0xFF3E4A49)
private val IconWell = Color(0xFF242A2A)
private val PanelShape = RoundedCornerShape(20.dp)

private fun poppins(size: TextUnit, weight: FontWeight, lineHeight: TextUnit = TextUnit.Unspecified) =
    TextStyle(fontFamily = PoppinsFontFamily, fontSize = size, fontWeight = weight, lineHeight = lineHeight)

/**
 * @param genreOptions from the `genres` table; empty hides the genre section
 * @param selectedGenres slugs (`k_pop`), not labels - they are sent to the matcher as-is
 * @param onGenreToggled receives a slug
 * @param onBack unused since the sheet lost its back row (swipe, scrim tap and system back close
 *   it); kept so callers don't change
 */
@Composable
fun MoodQuestionnaireScreen(
    selectedMood: MoodTag?,
    genreOptions: List<GenreOption>,
    selectedGenres: Set<String>,
    onMoodSelected: (MoodTag) -> Unit,
    onGenreToggled: (String) -> Unit,
    onSubmit: () -> Unit,
    onLetCrateDecide: () -> Unit,
    onBack: () -> Unit = {},
) {
    var genreOpen by remember { mutableStateOf(false) }
    // Where the genre row + dropdown sit, so a tap anywhere else on the sheet can close it.
    var genreBounds by remember { mutableStateOf(Rect.Zero) }
    var sheetOffset by remember { mutableStateOf(Offset.Zero) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(VinylPalette.SheetSurface)
            .onGloballyPositioned { sheetOffset = it.positionInRoot() }
            .pointerInput(genreOpen) {
                if (!genreOpen) return@pointerInput
                awaitEachGesture {
                    // Initial pass: observe the tap without consuming it, so whatever was tapped
                    // (a mood, a button) still gets it.
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    if (!genreBounds.contains(down.position + sheetOffset)) genreOpen = false
                }
            }
    ) {
        // Min height = the sheet, so the weighted spacer pins the actions to the bottom when
        // the content is short; taller content just scrolls with the actions after it.
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Drag handle. The swipe itself is handled by BottomSheetContainer over this strip.
            Box(
                modifier = Modifier
                    .size(width = 36.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(VinylPalette.Cream.copy(alpha = 0.3f)),
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "How did today find you?",
                color = VinylPalette.Cream,
                style = poppins(20.sp, FontWeight.Medium, 28.sp),
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Pick a mood and we'll find songs to match.",
                color = VinylPalette.TextMuted,
                style = poppins(14.sp, FontWeight.Normal, 20.sp),
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(20.dp))

            MoodPanel(selectedMood = selectedMood, onMoodSelected = onMoodSelected)

            // Hidden rather than shown empty while the list loads or if it can't: genre is optional.
            if (genreOptions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { genreBounds = it.boundsInRoot() },
                ) {
                    GenrePicker(
                        options = genreOptions,
                        selected = selectedGenres,
                        open = genreOpen,
                        onOpenChange = { genreOpen = it },
                        onToggle = onGenreToggled,
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.height(24.dp))

            TextButton(onClick = onLetCrateDecide) {
                Icon(
                    Icons.Rounded.Shuffle,
                    contentDescription = null,
                    tint = VinylPalette.TealAccent,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Surprise me", color = VinylPalette.TealAccent, style = poppins(14.sp, FontWeight.Medium))
            }

            Spacer(modifier = Modifier.height(8.dp))

            PullRecordsButton(enabled = selectedMood != null, onClick = onSubmit)
        }
    }
}

@Composable
private fun MoodPanel(selectedMood: MoodTag?, onMoodSelected: (MoodTag) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PanelBrush, PanelShape)
            .border(1.dp, PanelBorder, PanelShape)
            .padding(horizontal = 6.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        MoodOptions.all.chunked(5).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                row.forEach { option ->
                    MoodCell(
                        option = option,
                        selected = option.tag == selectedMood,
                        onClick = { onMoodSelected(option.tag) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun MoodCell(
    option: MoodOption,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null, // the ring and glow are the feedback
                onClick = onClick,
            )
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .drawBehind {
                    if (selected) {
                        val glowRadius = size.minDimension * 0.8f
                        drawCircle(
                            brush = Brush.radialGradient(
                                listOf(VinylPalette.TealAccent.copy(alpha = 0.3f), Color.Transparent),
                                center = center,
                                radius = glowRadius,
                            ),
                            radius = glowRadius,
                        )
                    }
                }
                .background(IconWell, CircleShape)
                .then(
                    if (selected) {
                        Modifier
                            .background(VinylPalette.TealAccent.copy(alpha = 0.16f), CircleShape)
                            .border(1.5.dp, VinylPalette.TealAccent, CircleShape)
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(moodIcon(option.tag)),
                contentDescription = null, // the title below names it
                tint = if (selected) VinylPalette.TealAccent else VinylPalette.Cream,
                modifier = Modifier.size(30.dp),
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = option.title,
            color = VinylPalette.Cream,
            style = poppins(11.sp, if (selected) FontWeight.Medium else FontWeight.Light, 16.sp),
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** Also drawn on the envelope and the music card, so shared within the module. */
@DrawableRes
internal fun moodIcon(tag: MoodTag): Int = when (tag) {
    MoodTag.Happy -> R.drawable.ic_mood_happy
    MoodTag.Sad -> R.drawable.ic_mood_sad
    MoodTag.Calm -> R.drawable.ic_mood_calm
    MoodTag.Energetic -> R.drawable.ic_mood_energetic
    MoodTag.Nostalgic -> R.drawable.ic_mood_nostalgic
    MoodTag.Anxious -> R.drawable.ic_mood_anxious
    MoodTag.Romantic -> R.drawable.ic_mood_romantic
    MoodTag.Angry -> R.drawable.ic_mood_angry
    MoodTag.Hopeful -> R.drawable.ic_mood_hopeful
    MoodTag.Lonely -> R.drawable.ic_mood_lonely
}

/**
 * Multi-choice genre picker. The selection starts on the user's onboarding favourites; each cell
 * toggles one genre, and "Any genre" clears them all.
 */
@Composable
private fun GenrePicker(
    options: List<GenreOption>,
    selected: Set<String>,
    open: Boolean,
    onOpenChange: (Boolean) -> Unit,
    onToggle: (String) -> Unit,
) {
    // Labels in the dropdown's order; a slug no longer on offer falls back to itself.
    val labels = options.filter { it.slug in selected }.map { it.label } +
        selected.filter { slug -> options.none { it.slug == slug } }
    val value = when {
        labels.isEmpty() -> "Any"
        labels.size <= 2 -> labels.joinToString(", ")
        else -> labels.take(2).joinToString(", ") + " +${labels.size - 2}"
    }
    val highlighted = open || selected.isNotEmpty()
    val chevronRotation by animateFloatAsState(if (open) 180f else 0f, label = "chevron")

    // Stays open so several can be picked; "Any genre" (null) toggles off everything selected.
    fun pick(slug: String?) {
        if (slug == null) selected.forEach(onToggle) else onToggle(slug)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(PanelShape)
            .background(PanelBrush)
            .border(1.dp, if (highlighted) VinylPalette.TealAccent else PanelBorder, PanelShape)
            .clickable { onOpenChange(!open) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Rounded.MusicNote,
            contentDescription = null,
            tint = VinylPalette.TealAccent,
            modifier = Modifier.size(22.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("Genre (optional)", color = VinylPalette.Cream.copy(alpha = 0.7f), style = poppins(11.sp, FontWeight.Light, 16.sp))
            Text(
                text = value,
                color = if (highlighted) VinylPalette.TealAccent else VinylPalette.Cream,
                style = poppins(16.sp, FontWeight.Normal, 22.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            Icons.Rounded.KeyboardArrowDown,
            contentDescription = if (open) "Close genres" else "Open genres",
            tint = VinylPalette.TealAccent,
            modifier = Modifier.rotate(chevronRotation),
        )
    }

    // Expands in place rather than floating, so it pushes the buttons down instead of covering them.
    AnimatedVisibility(
        visible = open,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
    ) {
        val cells = listOf<GenreOption?>(null) + options // null = "Any genre"
        Column(
            modifier = Modifier
                .padding(top = 8.dp)
                .fillMaxWidth()
                .heightIn(max = 180.dp)
                .background(PanelBrush, PanelShape)
                .border(1.dp, PanelBorder, PanelShape)
                .clip(PanelShape)
                .verticalScroll(rememberScrollState())
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            cells.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { genre ->
                        GenreCell(
                            label = genre?.label ?: "Any genre",
                            selected = if (genre == null) selected.isEmpty() else genre.slug in selected,
                            onClick = { pick(genre?.slug) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    repeat(3 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun GenreCell(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = modifier
            .height(38.dp)
            .clip(shape)
            .background(if (selected) VinylPalette.TealAccent.copy(alpha = 0.16f) else Color.Transparent)
            .border(1.dp, if (selected) VinylPalette.TealAccent else Color.Transparent, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (selected) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = null,
                    tint = VinylPalette.TealAccent,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = label,
                color = if (selected) VinylPalette.TealAccent else VinylPalette.Cream,
                style = poppins(13.sp, if (selected) FontWeight.Medium else FontWeight.Normal),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
    }
}

@Composable
private fun PullRecordsButton(enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        shape = CircleShape,
        border = BorderStroke(
            1.5.dp,
            if (enabled) VinylPalette.TealAccent else VinylPalette.Cream.copy(alpha = 0.15f),
        ),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = VinylPalette.Cream,
            disabledContentColor = VinylPalette.Cream.copy(alpha = 0.45f),
        ),
        contentPadding = PaddingValues(0.dp),
    ) {
        // Label centred on the whole button; the arrow is pinned to the edge independently of it.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (enabled) Modifier.background(PanelBrush) else Modifier),
        ) {
            Text(
                text = if (enabled) "Pull records" else "Pick a mood first",
                style = poppins(16.sp, FontWeight.Medium),
                modifier = Modifier.align(Alignment.Center),
            )
            if (enabled) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = null,
                    tint = VinylPalette.TealAccent,
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 22.dp).size(20.dp),
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0D0D, widthDp = 390, heightDp = 844)
@Composable
private fun MoodQuestionnaireScreenPreview() {
    MoodQuestionnaireScreen(
        selectedMood = MoodTag.Nostalgic,
        genreOptions = listOf(
            GenreOption("pop", "Pop"),
            GenreOption("jazz", "Jazz"),
            GenreOption("k_pop", "K-pop"),
            GenreOption("rnb", "R&B"),
            GenreOption("shoegaze", "Shoegaze"),
        ),
        selectedGenres = setOf("k_pop"),
        onMoodSelected = {},
        onGenreToggled = {},
        onSubmit = {},
        onLetCrateDecide = {},
    )
}
