package com.example.vinyl.ui.home

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward5
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay5
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.vinyl.ui.theme.VinylPalette

/**
 * What's on the deck and how to drive it, above the turntable: the title and artist, the progress
 * bar, then the controls — back five seconds, pause or resume, on five seconds. The deck
 * itself shows the cover, so the panel carries only the words.
 *
 * With nothing playing it says so in grey and the controls stay greyed out, so the panel keeps its
 * shape and the screen doesn't jump when a song starts. Pause works during loading;
 * skipping waits for the preview to be ready.
 *
 * @param record the song on the deck, or null when nothing is
 * @param clock where the song is, including preparation; null when stopped
 */
@Composable
fun NowPlayingPanel(
    record: NowPlaying?,
    clock: PlaybackClock?,
    modifier: Modifier = Modifier,
    onTogglePause: () -> Unit = {},
    onSeekBy: (Long) -> Unit = {},
) {
    val paused = clock?.isPaused == true
    val sounding = clock != null
    val canSeek = sounding && !clock.isLoading

    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DeckStatus(record = record, paused = paused)
            Spacer(Modifier.width(10.dp))
            Text(
                text = record?.title ?: "Nothing playing",
                color = if (record != null) VinylPalette.TextPrimary else VinylPalette.TextMuted,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = record?.artist ?: "Open a music card and play its song.",
            color = VinylPalette.TextMuted,
            fontSize = 15.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp),
        )

        Spacer(Modifier.height(14.dp))
        PlaybackProgressBar(clock = clock)
        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { onSeekBy(-SKIP_MILLIS) }, enabled = canSeek) {
                Icon(
                    imageVector = Icons.Filled.Replay5,
                    contentDescription = "Back 5 seconds",
                    tint = sideTint(canSeek),
                    modifier = Modifier.size(SIDE_ICON),
                )
            }
            FilledIconButton(
                onClick = onTogglePause,
                enabled = sounding,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = VinylPalette.TealAccent,
                    contentColor = VinylPalette.Background,
                    disabledContainerColor = VinylPalette.TextMuted.copy(alpha = 0.2f),
                    disabledContentColor = VinylPalette.TextMuted,
                ),
                modifier = Modifier.size(MAIN_BUTTON),
            ) {
                Icon(
                    imageVector = if (paused || !sounding) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                    contentDescription = if (paused) "Resume" else "Pause",
                    modifier = Modifier.size(MAIN_ICON),
                )
            }
            IconButton(onClick = { onSeekBy(SKIP_MILLIS) }, enabled = canSeek) {
                Icon(
                    imageVector = Icons.Filled.Forward5,
                    contentDescription = "Forward 5 seconds",
                    tint = sideTint(canSeek),
                    modifier = Modifier.size(SIDE_ICON),
                )
            }
        }
    }
}

private fun sideTint(enabled: Boolean): Color =
    if (enabled) VinylPalette.TextPrimary else VinylPalette.TextMuted.copy(alpha = 0.5f)

private const val SKIP_MILLIS = 5_000L
private val MAIN_BUTTON = 56.dp
private val MAIN_ICON = 30.dp
private val SIDE_ICON = 30.dp

/**
 * What's in front of the title: teal bars bouncing while a song plays, the same bars standing
 * still while it's paused, and a grey note when nothing is on. The quickest way to tell the three
 * apart without reading.
 */
@Composable
private fun DeckStatus(record: NowPlaying?, paused: Boolean) {
    when {
        record == null -> Icon(
            imageVector = Icons.Filled.MusicNote,
            contentDescription = null,
            tint = VinylPalette.TextMuted,
            modifier = Modifier.size(16.dp),
        )
        paused -> Bars(levels = { PAUSED_BAR_LEVELS }, colour = VinylPalette.TealAccent.copy(alpha = 0.6f))
        else -> SoundBars()
    }
}

/** Only composed while a song plays, so an idle panel never runs an animation. */
@Composable
private fun SoundBars() {
    val transition = rememberInfiniteTransition(label = "soundBars")
    val levels = SOUND_BAR_OFFSETS_MS.map { offset ->
        transition.animateFloat(
            initialValue = LOW_BAR_LEVEL,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(SOUND_BAR_MILLIS, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
                initialStartOffset = StartOffset(offset),
            ),
            label = "bar$offset",
        )
    }
    Bars(levels = { levels.map { it.value } }, colour = VinylPalette.TealAccent)
}

/** [levels] is read at draw time, so a moving bar redraws without recomposing the panel. */
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

private const val SOUND_BAR_MILLIS = 420
private val SOUND_BAR_OFFSETS_MS = listOf(0, 140, 280)
private const val LOW_BAR_LEVEL = 0.25f

/** A frozen frame of the bounce: uneven, so it still reads as sound bars and not an ellipsis. */
private val PAUSED_BAR_LEVELS = listOf(0.55f, 1f, 0.4f)

@Preview(showBackground = true, backgroundColor = 0xFF0D0D0D, widthDp = 393)
@Composable
private fun NowPlayingPanelPreview() {
    NowPlayingPanel(
        record = NowPlaying(
            submissionId = null,
            title = "Pink + White",
            artist = "Frank Ocean",
            artworkUrl = null,
            previewUrl = null,
        ),
        clock = PlaybackClock(startedAtMillis = 0L, durationMillis = 30_000L, pausedAtMillis = 12_000L),
        modifier = Modifier.padding(20.dp),
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0D0D, widthDp = 393)
@Composable
private fun NowPlayingPanelIdlePreview() {
    NowPlayingPanel(record = null, clock = null, modifier = Modifier.padding(20.dp))
}
