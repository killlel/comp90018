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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.vinyl.R
import com.example.vinyl.data.MoodTag
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
 * Page 2 of the receive flow, inside the sheet: today's music cards, all of them openable.
 * [onNotNow] is the "Done" button, which the caller takes back to the mood sheet.
 */
@Composable
fun ArrivedTodayScreen(
    state: ArrivedTodayUiState,
    onSelect: (ArrivedRecordOption) -> Unit,
    onNotNow: () -> Unit,
) {
    ArrivedTodayLayout {
        state.fallbackNote?.let {
            Spacer(modifier = Modifier.height(12.dp))
            FallbackChip(text = it)
        }

        Spacer(modifier = Modifier.height(20.dp))

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            state.options.forEachIndexed { index, option ->
                RiseIn(index = index) {
                    ArrivedRecordCard(option = option, onClick = { onSelect(option) })
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))
        Spacer(modifier = Modifier.height(24.dp))

        TextButton(onClick = onNotNow, modifier = Modifier.height(44.dp)) {
            Text(
                "Done",
                color = VinylPalette.TealAccent,
                style = ReceiveFlowStyle.text(14.sp, FontWeight.Medium),
            )
        }
    }
}

/** What page 2 shows while today's cards are still being fetched: a spinning record. */
@Composable
fun ArrivedTodayLoading() {
    val spin = rememberInfiniteTransition(label = "loadingRecord")
    val rotation by spin.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart),
        label = "rotation",
    )
    ArrivedTodayLayout {
        Spacer(modifier = Modifier.weight(1f))
        Image(
            painter = painterResource(R.drawable.turntable_record),
            contentDescription = "Loading today's music cards",
            modifier = Modifier.size(140.dp).rotate(rotation),
        )
        Spacer(modifier = Modifier.weight(1.4f))
    }
}

/**
 * The sheet background, header and scrolling column shared by the loaded and loading states.
 * The header sits at the same height as the mood sheet's, so the swap between them reads as
 * one sheet turning its page.
 */
@Composable
private fun ArrivedTodayLayout(content: @Composable ColumnScope.() -> Unit) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(VinylPalette.SheetSurface),
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                // Top padding matches the mood sheet's handle plus its gap, so the titles line up.
                .padding(start = 20.dp, end = 20.dp, top = 34.dp, bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Arrived today",
                color = VinylPalette.Cream,
                style = ReceiveFlowStyle.Title,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "All three are yours to open.",
                color = VinylPalette.TextMuted,
                style = ReceiveFlowStyle.Helper,
                textAlign = TextAlign.Center,
            )
            content()
        }
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

/** Fades and lifts [content] into place, each card a beat after the one above it. */
@Composable
private fun RiseIn(index: Int, content: @Composable () -> Unit) {
    // Previews don't run effects, so they start settled rather than invisible.
    val inPreview = LocalInspectionMode.current
    val progress = remember { Animatable(if (inPreview) 1f else 0f) }
    LaunchedEffect(Unit) {
        delay(index * 120L)
        progress.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
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

@Composable
private fun ArrivedRecordCard(option: ArrivedRecordOption, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ReceiveFlowStyle.PanelShape)
            .background(ReceiveFlowStyle.PanelBrush)
            .border(1.dp, ReceiveFlowStyle.PanelBorder, ReceiveFlowStyle.PanelShape)
            .clickable(onClick = onClick)
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
                color = VinylPalette.Cream.copy(alpha = 0.55f),
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
                    model = artworkUrl,
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

@Preview(showBackground = true, backgroundColor = 0xFF0D0D0D, widthDp = 390, heightDp = 744)
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

@Preview(showBackground = true, backgroundColor = 0xFF0D0D0D, widthDp = 390, heightDp = 744)
@Composable
private fun ArrivedTodayFallbackPreview() {
    ArrivedTodayScreen(
        state = ArrivedTodayUiState(
            moodLabel = "Nostalgic",
            fallbackNote = "Nothing in the crate yet. Check back later.",
            options = emptyList(),
        ),
        onSelect = {},
        onNotNow = {},
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0D0D, widthDp = 390, heightDp = 744)
@Composable
private fun ArrivedTodayLoadingPreview() {
    ArrivedTodayLoading()
}
