package com.example.vinyl.ui.home

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.vinyl.ui.theme.VinylPalette

/**
 * How far through the song is: elapsed time, a track with a knob riding along it, total time.
 *
 * Idle it still looks like a player — "0:00", a grey knob at the start, "--:--" — rather than a
 * bare line that reads as a divider. It stays at the start while the needle is still dropping or
 * the preview is still loading: [clock] only starts once sound does. The track is redrawn each
 * frame without recomposing; the times change once a second.
 */
@Composable
fun PlaybackProgressBar(clock: PlaybackClock?, modifier: Modifier = Modifier) {
    val now = remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    // Ticks once a frame while a song runs; with nothing playing, nothing is scheduled.
    LaunchedEffect(clock) {
        if (clock == null) return@LaunchedEffect
        while (true) withFrameMillis { now.longValue = SystemClock.elapsedRealtime() }
    }

    val elapsedSeconds by remember(clock) {
        derivedStateOf { clock?.let { elapsedMillis(it, now.longValue) / 1000 } ?: 0L }
    }

    val active = clock != null
    val accent = if (active) VinylPalette.TealAccent else VinylPalette.TextMuted

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TimeLabel(formatTime(elapsedSeconds), active, TextAlign.Start)

        Canvas(
            Modifier
                .weight(1f)
                .height(KNOB_DIAMETER),
        ) {
            val knobRadius = size.height / 2f
            // Inset by the knob's radius, so the knob sits fully inside at 0% and at 100%.
            val start = knobRadius
            val length = size.width - knobRadius * 2
            val trackTop = (size.height - TRACK_HEIGHT.toPx()) / 2f
            val trackSize = Size(length, TRACK_HEIGHT.toPx())
            val corner = CornerRadius(trackSize.height / 2f)

            drawRoundRect(
                color = VinylPalette.TextMuted.copy(alpha = 0.25f),
                topLeft = Offset(start, trackTop),
                size = trackSize,
                cornerRadius = corner,
            )
            val fraction = clock?.let {
                elapsedMillis(it, now.longValue).toFloat() / it.durationMillis
            } ?: 0f
            if (fraction > 0f) {
                drawRoundRect(
                    color = accent,
                    topLeft = Offset(start, trackTop),
                    size = Size(length * fraction, trackSize.height),
                    cornerRadius = corner,
                )
            }
            drawCircle(
                color = accent,
                radius = knobRadius,
                center = Offset(start + length * fraction, size.height / 2f),
            )
        }

        TimeLabel(clock?.let { formatTime(it.durationMillis / 1000) } ?: "--:--", active, TextAlign.End)
    }
}

/** Fixed width, so the track doesn't shift as "0:09" becomes "0:10". */
@Composable
private fun TimeLabel(text: String, active: Boolean, align: TextAlign) {
    Text(
        text = text,
        color = if (active) VinylPalette.TextPrimary else VinylPalette.TextMuted,
        fontSize = 12.sp,
        textAlign = align,
        modifier = Modifier.width(TIME_WIDTH),
    )
}

private fun elapsedMillis(clock: PlaybackClock, now: Long): Long =
    (now - clock.startedAtMillis).coerceIn(0L, clock.durationMillis)

/** 0:07, 0:30, 3:05. */
private fun formatTime(seconds: Long): String = "%d:%02d".format(seconds / 60, seconds % 60)

private val TRACK_HEIGHT = 5.dp
private val KNOB_DIAMETER = 12.dp
private val TIME_WIDTH = 36.dp

@Preview(showBackground = true, backgroundColor = 0xFF0D0D0D, widthDp = 393)
@Composable
private fun PlaybackProgressBarPreview() {
    PlaybackProgressBar(
        clock = PlaybackClock(SystemClock.elapsedRealtime() - 12_000L, 30_000L),
        modifier = Modifier.padding(20.dp),
    )
}
