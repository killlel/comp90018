package com.example.vinyl.ui.home

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.vinyl.data.repository.placeholderAccent
import com.example.vinyl.ui.theme.VinylPalette

/**
 * The song that's playing: the cover on the left, title and artist on the right. Display only —
 * it has no tap action; the button under the turntable is the way into today's cards.
 *
 * With nothing playing it shows an idle state instead: an empty record, still bars, and no song.
 * A record waiting on the deck is *not* playing, so it never appears here on its own.
 *
 * Takes whatever height its parent gives it — Home hands it the space the rest of the screen
 * leaves — and the cover grows with it, staying square.
 *
 * @param record the song playing, or null when nothing is
 */
@Composable
fun NowPlayingCard(
    record: NowPlaying?,
    modifier: Modifier = Modifier,
) {
    val playing = record != null
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(VinylPalette.PanelDark)
            .border(1.dp, VinylPalette.TextMuted.copy(alpha = 0.2f), shape)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val coverModifier = Modifier
            .fillMaxHeight()
            .aspectRatio(1f)
        if (record != null) Cover(record, coverModifier) else EmptyRecord(coverModifier)

        Spacer(Modifier.width(16.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SoundBars(playing = playing)
                Text(
                    text = if (playing) "NOW PLAYING" else "NOT PLAYING",
                    color = if (playing) VinylPalette.TealAccent else VinylPalette.TextMuted,
                    fontSize = 11.sp,
                    letterSpacing = 1.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            Text(
                text = record?.title ?: "Nothing playing",
                color = if (playing) VinylPalette.TextPrimary else VinylPalette.TextMuted,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 22.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = record?.artist ?: "Songs you play will show up here.",
                color = VinylPalette.TextMuted,
                fontSize = 14.sp,
                maxLines = if (playing) 1 else 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun Cover(record: NowPlaying, modifier: Modifier = Modifier) {
    // Same colour the record's sleeve has on the shelf and in Collection, picked from its id.
    val accent = record.submissionId?.let { Color(placeholderAccent(it)) } ?: VinylPalette.BrownAccent
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(accent),
        contentAlignment = Alignment.Center,
    ) {
        if (record.artworkUrl != null) {
            AsyncImage(
                model = record.artworkUrl,
                contentDescription = "Cover of ${record.title}",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            // A song without a cover still gets a recognisable square rather than a hole.
            Icon(
                imageVector = Icons.Filled.MusicNote,
                contentDescription = null,
                tint = if (accent.luminance() > 0.45f) Color.Black else Color.White,
                modifier = Modifier.fillMaxSize(0.4f),
            )
        }
    }
}

/** A bare record in muted greys — the idle stand-in for a cover. Drawn, so it scales cleanly. */
@Composable
private fun EmptyRecord(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(VinylPalette.SheetSurface),
    ) {
        Canvas(Modifier.fillMaxSize().padding(10.dp)) {
            val centre = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension / 2f
            drawCircle(color = EmptyDisc, radius = radius, center = centre)
            // A few grooves, spaced like a real side of vinyl.
            listOf(0.92f, 0.80f, 0.68f, 0.56f).forEach { fraction ->
                drawCircle(
                    color = EmptyGroove,
                    radius = radius * fraction,
                    center = centre,
                    style = Stroke(width = 1.dp.toPx()),
                )
            }
            // A blank label and the spindle hole: no song, so no colour.
            drawCircle(color = EmptyLabel, radius = radius * 0.30f, center = centre)
            drawCircle(color = VinylPalette.SheetSurface, radius = radius * 0.05f, center = centre)
        }
    }
}

/**
 * Three bars beside the label: bouncing in teal while a song plays, flat and grey when nothing
 * does. The quickest way to tell the two states apart without reading.
 */
@Composable
private fun SoundBars(playing: Boolean, modifier: Modifier = Modifier) {
    if (playing) {
        // Only composed while playing, so an idle card never runs an animation.
        val transition = rememberInfiniteTransition(label = "soundBars")
        val levels = SOUND_BAR_OFFSETS_MS.map { offset ->
            transition.animateFloat(
                initialValue = IDLE_BAR_LEVEL,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(SOUND_BAR_MILLIS, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                    initialStartOffset = StartOffset(offset),
                ),
                label = "bar$offset",
            )
        }
        Bars(levels = { levels.map { it.value } }, colour = VinylPalette.TealAccent, modifier = modifier)
    } else {
        // Idle bars rest at a sliver rather than vanishing, so the slot doesn't look broken.
        Bars(
            levels = { List(SOUND_BAR_OFFSETS_MS.size) { IDLE_BAR_LEVEL } },
            colour = VinylPalette.TextMuted.copy(alpha = 0.6f),
            modifier = modifier,
        )
    }
}

/** [levels] is read at draw time, so a moving bar redraws without recomposing the card. */
@Composable
private fun Bars(levels: () -> List<Float>, colour: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(width = 14.dp, height = 12.dp)) {
        val current = levels()
        val barWidth = 3.dp.toPx()
        val gap = (size.width - barWidth * current.size) / (current.size - 1)
        current.forEachIndexed { i, level ->
            val height = size.height * level
            drawRect(
                color = colour,
                topLeft = Offset(i * (barWidth + gap), size.height - height),
                size = Size(barWidth, height),
            )
        }
    }
}

private val EmptyDisc = Color(0xFF1C1C1C)
private val EmptyGroove = Color(0xFF2E2E2E)
private val EmptyLabel = Color(0xFF3A3A3A)

private const val SOUND_BAR_MILLIS = 420
private val SOUND_BAR_OFFSETS_MS = listOf(0, 140, 280)
private const val IDLE_BAR_LEVEL = 0.25f

/**
 * A column that gives [fill] whatever height the screen has left, so Home fills a tall phone
 * without a blank strip at the bottom and without scrolling, while a short phone still scrolls.
 *
 * [fill] never shrinks below [minFill] or grows past [maxFill]. Once it is at [maxFill], any
 * height still left is shared evenly between the gaps, so the column always ends exactly at the
 * bottom of the screen instead of leaving a blank strip there. Children are laid out top to
 * bottom with at least [spacing] between them, like `Column(verticalArrangement = spacedBy(...))`.
 *
 * A plain `Column` can't do this: inside `verticalScroll` its height is unbounded, so
 * `Modifier.weight` has nothing to divide.
 */
@Composable
fun FillViewportColumn(
    viewportHeight: Dp,
    spacing: Dp,
    minFill: Dp,
    maxFill: Dp,
    modifier: Modifier = Modifier,
    above: @Composable () -> Unit,
    fill: @Composable () -> Unit,
    below: @Composable () -> Unit,
) {
    Layout(contents = listOf(above, fill, below), modifier = modifier) { (aboveItems, fillItems, belowItems), constraints ->
        val child = Constraints(maxWidth = constraints.maxWidth)
        val gap = spacing.roundToPx()

        val abovePlaced = aboveItems.map { it.measure(child) }
        val belowPlaced = belowItems.map { it.measure(child) }
        val fixedCount = abovePlaced.size + belowPlaced.size
        val used = abovePlaced.sumOf { it.height } + belowPlaced.sumOf { it.height } +
            gap * fixedCount // one gap between each pair, counting the fill slot

        val fillHeight = (viewportHeight.roundToPx() - used)
            .coerceIn(minFill.roundToPx(), maxFill.roundToPx())
        val fillPlaced = fillItems.map {
            it.measure(child.copy(minHeight = fillHeight, maxHeight = fillHeight))
        }

        val all = abovePlaced + fillPlaced + belowPlaced
        val gaps = (all.size - 1).coerceAtLeast(0)
        val content = all.sumOf { it.height } + gap * gaps

        // Space the fill couldn't take, spread over the gaps. Zero on a short screen.
        val spare = (viewportHeight.roundToPx() - content).coerceAtLeast(0)
        val extra = if (gaps > 0) spare / gaps else 0

        val height = content + extra * gaps
        layout(constraints.maxWidth, maxOf(height, constraints.minHeight)) {
            var y = 0
            all.forEach {
                it.placeRelative(0, y)
                y += it.height + gap + extra
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0D0D, widthDp = 393, heightDp = 140)
@Composable
private fun NowPlayingCardPreview() {
    NowPlayingCard(
        record = null,
        modifier = Modifier
            .padding(20.dp)
            .height(120.dp),
    )
}
