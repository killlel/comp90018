package com.example.vinyl.ui.daily

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.foundation.Canvas
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.vinyl.R
import com.example.vinyl.data.MoodTag
import com.example.vinyl.data.THUMB_ARTWORK_PX
import com.example.vinyl.data.itunesArtworkAt
import com.example.vinyl.ui.theme.VinylPalette
import kotlinx.coroutines.delay

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
    /** Where the letter was sent from — needed to compute which way to point the compass arrow. */
    val senderLat: Double? = null,
    val senderLng: Double? = null,
    /** The song's 30-second preview, for "Play this song". Null when the track has none. */
    val previewUrl: String? = null,
    /** The day it was sent, for example "8 Oct". Null when the send time is unknown. */
    val sentDateLabel: String? = null,
)

data class ArrivedTodayUiState(
    val moodLabel: String,
    val genreLabel: String? = null,
    val fallbackNote: String? = null,
    val options: List<ArrivedRecordOption>,
)

/**
 * Page 2 of the receive flow, full screen: today's music cards, all of them openable.
 * [onNotNow] is the "Back to Home" button.
 *
 * @param onRetry shows a "Try again" button under the note; pass it only when the load failed.
 * @param animateIn true the first time a new set of cards is shown, so they rise in one by one.
 *   Coming back to the same cards from the envelope shows them already in place.
 */
@Composable
fun ArrivedTodayScreen(
    state: ArrivedTodayUiState,
    onSelect: (ArrivedRecordOption) -> Unit,
    onNotNow: () -> Unit,
    animateIn: Boolean = true,
    onRetry: (() -> Unit)? = null,
) {
    ArrivedTodayLayout(helper = if (onRetry == null) helperFor(state.options.size) else null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            state.fallbackNote?.let {
                Spacer(modifier = Modifier.height(12.dp))
                FallbackChip(text = it)
            }

            onRetry?.let {
                Spacer(modifier = Modifier.height(12.dp))
                TextButton(onClick = it, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(
                        "Try again",
                        color = VinylPalette.TealAccent,
                        style = ReceiveFlowStyle.text(14.sp, FontWeight.Medium),
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                state.options.forEachIndexed { index, option ->
                    RiseIn(index = index, animate = animateIn) {
                        ArrivedRecordCard(option = option, onClick = { onSelect(option) })
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        TextButton(
            onClick = onNotNow,
            modifier = Modifier.align(Alignment.BottomCenter).height(44.dp),
        ) {
            Text(
                "Back to Home",
                color = VinylPalette.TealAccent,
                style = ReceiveFlowStyle.text(14.sp, FontWeight.Medium),
            )
        }
    }
}

/**
 * What page 2 shows while today's cards are still being fetched: a record turning in the centre
 * of the screen, inside the teal progress ring the loading state has always had.
 */
@Composable
fun ArrivedTodayLoading() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(VinylPalette.SheetSurface),
    ) {
        ArrivedTodayLayout(helper = null) {}
        Box(
            modifier = Modifier.align(Alignment.Center).size(LoadingSize),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(
                color = VinylPalette.TealAccent,
                strokeWidth = 2.dp,
                modifier = Modifier.fillMaxSize(),
            )
            LoadingRecord(modifier = Modifier.size(LoadingSize - 24.dp))
        }
    }
}

/**
 * A record drawn from circles rather than the turntable photo. The photo's sheen is lit by the
 * room and its disc sits slightly off the image centre, so turning it reads as a wobble; this one
 * is centred exactly and only its highlight and label mark show the turn.
 */
@Composable
private fun LoadingRecord(modifier: Modifier = Modifier) {
    val spin = rememberInfiniteTransition(label = "loadingRecord")
    val rotation = spin.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart),
        label = "rotation",
    )
    Canvas(
        // Read at draw time, so each frame of the spin redraws without recomposing.
        modifier = modifier.graphicsLayer { rotationZ = rotation.value },
    ) {
        val radius = size.minDimension / 2f
        drawCircle(VinylPalette.RecordDark, radius)
        var groove = radius * 0.45f
        while (groove < radius * 0.95f) {
            drawCircle(VinylPalette.Cream.copy(alpha = 0.07f), groove, style = Stroke(width = 1.dp.toPx()))
            groove += radius * 0.07f
        }
        drawArc(
            color = VinylPalette.Cream.copy(alpha = 0.10f),
            startAngle = -70f,
            sweepAngle = 40f,
            useCenter = true,
        )
        drawCircle(VinylPalette.TealAccent, radius * 0.32f)
        drawCircle(VinylPalette.RecordDark, radius * 0.05f, center = center + Offset(0f, -radius * 0.2f))
        drawCircle(VinylPalette.SheetSurface, radius * 0.035f)
    }
}

/** The line under the title, matched to how many cards came; none when nothing came. */
private fun helperFor(count: Int): String? = when (count) {
    0 -> null
    1 -> "It's yours to open."
    2 -> "Both are yours to open."
    3 -> "All three are yours to open."
    else -> "All of them are yours to open."
}

/**
 * The full-screen background and header shared by the loaded and loading states.
 *
 * @param helper the line under the title; null hides it (loading, empty and error)
 */
@Composable
private fun ArrivedTodayLayout(helper: String?, content: @Composable BoxScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VinylPalette.SheetSurface)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Arrived today",
            color = VinylPalette.Cream,
            style = ReceiveFlowStyle.Title,
            textAlign = TextAlign.Center,
        )
        if (helper != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = helper,
                color = VinylPalette.TextMuted,
                style = ReceiveFlowStyle.Helper,
                textAlign = TextAlign.Center,
            )
        }
        Box(modifier = Modifier.fillMaxWidth().weight(1f), content = content)
    }
}

@Composable
private fun FallbackChip(text: String) {
    Text(
        text = text,
        color = VinylPalette.TealAccent,
        style = ReceiveFlowStyle.text(12.sp, FontWeight.Normal, 16.sp),
        textAlign = TextAlign.Center,
        modifier = Modifier
            .border(1.dp, VinylPalette.TealAccent.copy(alpha = 0.7f), CircleShape)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

/**
 * Fades and lifts [content] into place, each card a beat after the one above it. With [animate]
 * false it is simply shown.
 */
@Composable
private fun RiseIn(index: Int, animate: Boolean, content: @Composable () -> Unit) {
    // Previews don't run effects, so they start settled rather than invisible.
    val settled = !animate || LocalInspectionMode.current
    val progress = remember { Animatable(if (settled) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (progress.value < 1f) {
            delay(index * 120L)
            progress.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
        }
    }
    val lift = with(LocalDensity.current) { 24.dp.toPx() }
    Box(
        modifier = Modifier.graphicsLayer {
            alpha = progress.value
            translationY = (1f - progress.value) * lift
        },
    ) {
        content()
    }
}

private val LoadingSize = 150.dp

@Composable
private fun ArrivedRecordCard(option: ArrivedRecordOption, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ReceiveFlowStyle.PanelShape)
            .background(ReceiveFlowStyle.PanelBrush)
            .border(1.dp, ReceiveFlowStyle.PanelBorder, ReceiveFlowStyle.PanelShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverWithDisc(artworkUrl = option.artworkUrl)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = option.trackName,
                color = VinylPalette.Cream,
                style = ReceiveFlowStyle.text(15.sp, FontWeight.Medium, 22.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = option.artistName,
                color = VinylPalette.Cream.copy(alpha = 0.7f),
                style = ReceiveFlowStyle.text(12.sp, FontWeight.Light, 18.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = option.messagePreview,
                color = VinylPalette.Cream.copy(alpha = 0.65f),
                style = ReceiveFlowStyle.text(12.sp, FontWeight.Light, 18.sp, italic = true),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Box(
            modifier = Modifier
                .size(36.dp)
                .border(1.5.dp, VinylPalette.TealAccent, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = "Open",
                tint = VinylPalette.TealAccent,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** A 64dp cover with a 60dp record peeking out from behind its right edge. */
@Composable
private fun CoverWithDisc(artworkUrl: String?) {
    val coverShape = RoundedCornerShape(6.dp)
    Box(modifier = Modifier.width(CoverSize + DiscPeek).height(CoverSize)) {
        Image(
            painter = painterResource(R.drawable.black_vinyl),
            contentDescription = null,
            modifier = Modifier.size(DiscSize).align(Alignment.CenterEnd),
        )
        Box(
            modifier = Modifier
                .size(CoverSize)
                .align(Alignment.CenterStart)
                .clip(coverShape)
                .background(ReceiveFlowStyle.IconWell),
            contentAlignment = Alignment.Center,
        ) {
            if (artworkUrl != null) {
                AsyncImage(
                    model = itunesArtworkAt(artworkUrl, THUMB_ARTWORK_PX),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                // Not every song has artwork: a flat panel with a note instead.
                Icon(
                    Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = VinylPalette.Cream.copy(alpha = 0.55f),
                    modifier = Modifier.size(26.dp),
                )
            }
        }
    }
}

private val CoverSize = 64.dp
private val DiscSize = 60.dp

/** How far the record shows past the cover's right edge. */
private val DiscPeek = 20.dp

@Preview(showBackground = true, backgroundColor = 0xFF1C1C1C, widthDp = 390, heightDp = 844)
@Composable
private fun ArrivedTodayScreenPreview() {
    ArrivedTodayScreen(
        state = ArrivedTodayUiState(
            moodLabel = "Nostalgic",
            options = listOf(
                ArrivedRecordOption("1", "Slow Rain, Rooftop", "Marin Ochre", "I played this the night I moved out and never stopped", "Nostalgic", "2.4 km"),
                ArrivedRecordOption("2", "Kitchen Light, 2am", "Sora Lin", "My mother never turned the hall lamp off", "Nostalgic", "5.1 km"),
                ArrivedRecordOption("3", "Long Way From Cebu", "Teo Marasigan", "Third winter here and it still surprises me", "Nostalgic", "18 km"),
            ),
        ),
        onSelect = {},
        onNotNow = {},
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF1C1C1C, widthDp = 390, heightDp = 844)
@Composable
private fun ArrivedTodayFallbackPreview() {
    ArrivedTodayScreen(
        state = ArrivedTodayUiState(
            moodLabel = "Nostalgic",
            fallbackNote = "No music cards yet. Check back later.",
            options = emptyList(),
        ),
        onSelect = {},
        onNotNow = {},
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF1C1C1C, widthDp = 390, heightDp = 844)
@Composable
private fun ArrivedTodayErrorPreview() {
    ArrivedTodayScreen(
        state = ArrivedTodayUiState(
            moodLabel = "Nostalgic",
            fallbackNote = "Couldn't load today's music cards. Check your connection and try again.",
            options = emptyList(),
        ),
        onSelect = {},
        onNotNow = {},
        onRetry = {},
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF1C1C1C, widthDp = 390, heightDp = 844)
@Composable
private fun ArrivedTodayLoadingPreview() {
    ArrivedTodayLoading()
}
