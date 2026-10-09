package com.example.vinyl.ui.receive

import android.net.Uri
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
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
import com.example.vinyl.network.AudioPreviewController
import com.example.vinyl.network.rememberAudioPreviewController
import com.example.vinyl.ui.daily.ReceiveFlowStyle
import com.example.vinyl.ui.daily.moodIcon
import com.example.vinyl.ui.theme.VinylPalette

/** What page 4 of the receive flow shows for one opened music card. */
data class MusicCardUiState(
    val trackName: String,
    val artistName: String,
    val artworkUrl: String? = null,
    val previewUrl: String? = null,
    val mood: MoodTag? = null,
    val message: String,
    /** The day it was sent, for example "8 Oct". Null hides the "Sent" line. */
    val sentDateLabel: String? = null,
    /** False when there is no location to point at, which hides the compass chip. */
    val hasDirection: Boolean = false,
    val isKept: Boolean = false,
    /** Why the last keep didn't go through. */
    val keepError: String? = null,
)

/**
 * Page 4 of the receive flow, full screen: the opened music card.
 *
 * The round play button plays the 30 second preview here and never navigates. The way out is
 * [onPlayOnTurntable], or the system back, both of which the caller sends to Home.
 */
@Composable
fun MusicCardScreen(
    state: MusicCardUiState,
    onToggleKeep: () -> Unit,
    onOpenCompass: () -> Unit,
    onPlayOnTurntable: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VinylPalette.SheetSurface)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        TopBar(isKept = state.isKept, onToggleKeep = onToggleKeep)

        Spacer(modifier = Modifier.height(16.dp))

        PlayerCard(state = state)

        state.keepError?.let {
            Text(
                text = it,
                color = VinylPalette.TextMuted,
                style = ReceiveFlowStyle.text(12.sp, FontWeight.Normal, 16.sp),
                modifier = Modifier.padding(top = 8.dp, start = 4.dp),
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Letter(
            state = state,
            onOpenCompass = onOpenCompass,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(16.dp))

        BottomRow(
            trackName = state.trackName,
            artistName = state.artistName,
            onPlayOnTurntable = onPlayOnTurntable,
        )
    }
}

@Composable
private fun TopBar(isKept: Boolean, onToggleKeep: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        // Balances the bookmark so the title stays centred.
        Spacer(modifier = Modifier.size(40.dp))
        Text(
            text = "Card details",
            color = VinylPalette.Cream,
            style = ReceiveFlowStyle.Title,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .clickable(onClick = onToggleKeep),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (isKept) Icons.Rounded.Bookmark else Icons.Outlined.BookmarkBorder,
                contentDescription = if (isKept) "Remove from collection" else "Keep this music card",
                tint = if (isKept) VinylPalette.TealAccent else VinylPalette.Cream,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

/**
 * The song, with its preview playable in place. The line along the bottom edge fills while it
 * plays, and the preview stops at 0:30.
 */
@Composable
private fun PlayerCard(state: MusicCardUiState) {
    val audio = rememberAudioPreviewController()
    val url = state.previewUrl
    var elapsedMs by remember { mutableLongStateOf(0L) }
    val playing = audio.isPlaying && audio.currentUrl == url

    // The clock follows the player: it only runs while sound is actually playing.
    LaunchedEffect(playing) {
        if (!playing) return@LaunchedEffect
        var last = withFrameMillis { it }
        while (true) {
            val now = withFrameMillis { it }
            elapsedMs = (elapsedMs + now - last).coerceAtMost(PreviewMs)
            last = now
            if (elapsedMs >= PreviewMs) {
                audio.toggle(url) // pauses
                break
            }
        }
    }

    val onPlayPause: () -> Unit = {
        // Starting again after the end plays from the top.
        if (!playing && elapsedMs >= PreviewMs - 500) {
            elapsedMs = 0L
            audio.restartFromTop(url)
        } else {
            audio.toggle(url)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ReceiveFlowStyle.PanelShape)
            .background(ReceiveFlowStyle.PanelBrush)
            .border(1.dp, ReceiveFlowStyle.PanelBorder, ReceiveFlowStyle.PanelShape),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val coverShape = RoundedCornerShape(8.dp)
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(coverShape)
                    .background(ReceiveFlowStyle.IconWell)
                    .border(1.dp, VinylPalette.Cream.copy(alpha = 0.5f), coverShape),
                contentAlignment = Alignment.Center,
            ) {
                if (state.artworkUrl != null) {
                    AsyncImage(
                        model = state.artworkUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = VinylPalette.Cream.copy(alpha = 0.55f),
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.trackName,
                    color = VinylPalette.Cream,
                    style = ReceiveFlowStyle.text(18.sp, FontWeight.Medium, 26.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = state.artistName,
                    color = VinylPalette.Cream.copy(alpha = 0.65f),
                    style = ReceiveFlowStyle.text(14.sp, FontWeight.Light, 20.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            val enabled = url != null
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(VinylPalette.TealAccent.copy(alpha = if (enabled) 1f else 0.35f))
                    .clickable(enabled = enabled, onClick = onPlayPause),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = when {
                        !enabled -> "No preview for this song"
                        playing -> "Pause preview"
                        else -> "Play preview"
                    },
                    tint = VinylPalette.Background,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
        // Progress along the card's bottom edge. Not drawn at 0: a zero-width line still showed
        // as a dot in the rounded corner.
        if (elapsedMs > 0L) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(elapsedMs.toFloat() / PreviewMs)
                    .height(3.dp)
                    .background(VinylPalette.TealAccent),
            )
        }
    }
}

/**
 * [AudioPreviewController.toggle] resumes from wherever playback stopped. After the 0:30 cap
 * that is the end, so a fresh start releases the player first and loads the preview again.
 */
private fun AudioPreviewController.restartFromTop(url: String?) {
    release()
    toggle(url)
}

/** The sender's message on a cream letter, signed like a real one, with the mood and compass below. */
@Composable
private fun Letter(state: MusicCardUiState, onOpenCompass: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(VinylPalette.Cream)
            .padding(top = 30.dp, bottom = 18.dp),
    ) {
        // The writing area. A short note is set large in the middle of it; a longer one reads
        // like a letter, from the top left, and scrolls inside the card.
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = LetterTextInset),
            contentAlignment = Alignment.Center,
        ) {
            if (state.message.length <= ShortMessageMaxChars) {
                Text(
                    text = state.message,
                    color = VinylPalette.Background,
                    style = ReceiveFlowStyle.text(24.sp, FontWeight.Normal, 36.sp),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
            } else {
                Text(
                    text = state.message,
                    color = VinylPalette.Background,
                    style = ReceiveFlowStyle.text(16.sp, FontWeight.Normal, 29.sp),
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                )
            }
        }

        // The signature stays put at the bottom right, whatever the message does.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = LetterTextInset, end = LetterTextInset, top = 14.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Text(
                text = "- someone, somewhere",
                color = VinylPalette.Background.copy(alpha = 0.75f),
                style = ReceiveFlowStyle.text(15.sp, FontWeight.Light, 22.sp),
            )
            state.sentDateLabel?.let {
                Text(
                    text = "Sent $it",
                    color = VinylPalette.Background.copy(alpha = 0.6f),
                    style = ReceiveFlowStyle.text(13.sp, FontWeight.Light, 20.sp),
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // The footer sits 14dp further out than the text, lining up with the bottom row below.
        Box(
            modifier = Modifier
                .padding(horizontal = LetterFooterInset)
                .fillMaxWidth()
                .height(1.dp)
                .background(VinylPalette.Background.copy(alpha = 0.1f)),
        )
        Spacer(modifier = Modifier.height(14.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = LetterFooterInset),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            state.mood?.let { mood ->
                Icon(
                    painter = painterResource(moodIcon(mood)),
                    contentDescription = null,
                    tint = VinylPalette.Background,
                    modifier = Modifier.size(36.dp),
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            if (state.hasDirection) {
                CompassChip(onClick = onOpenCompass)
            }
        }
    }
}

@Composable
private fun CompassChip(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .height(48.dp)
            .clip(CircleShape)
            .background(ChipTeal.copy(alpha = 0.1f))
            .border(1.5.dp, ChipTeal, CircleShape)
            .clickable(onClick = onClick)
            .padding(start = 10.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.Explore,
            contentDescription = null,
            tint = ChipInk,
            modifier = Modifier.size(28.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "See where it's from",
            color = ChipInk,
            style = ReceiveFlowStyle.text(14.sp, FontWeight.Medium, 20.sp),
        )
    }
}

/**
 * Apple Music and Spotify, then "Play on turntable". Each service opens a search for this song,
 * the same links the Collection's card uses: the card only knows the title and artist.
 */
@Composable
private fun BottomRow(trackName: String, artistName: String, onPlayOnTurntable: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    val query = Uri.encode("$trackName $artistName")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Both official icons are the whole button, drawn as supplied: no backing shape, no tint.
        Image(
            painter = painterResource(R.drawable.ic_apple_music),
            contentDescription = "Open in Apple Music",
            modifier = Modifier
                .size(StreamingIconSize)
                .clickable { uriHandler.openUri("https://music.apple.com/search?term=$query") },
        )
        Box(
            modifier = Modifier
                .size(StreamingIconSize)
                .clickable { uriHandler.openUri("https://open.spotify.com/search/$query") },
            contentAlignment = Alignment.Center,
        ) {
            // The file keeps a little empty canvas around its circle, so it is drawn just larger
            // than the button to make the circle itself the button's size. Only that transparent
            // margin spills past the button's edge.
            Image(
                painter = painterResource(R.drawable.ic_spotify),
                contentDescription = "Open in Spotify",
                modifier = Modifier.requiredSize(SpotifyCanvasWidth, SpotifyCanvasHeight),
            )
        }
        OutlinedButton(
            onClick = onPlayOnTurntable,
            modifier = Modifier.weight(1f).height(56.dp),
            shape = CircleShape,
            border = BorderStroke(1.5.dp, VinylPalette.TealAccent),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = VinylPalette.Cream),
            contentPadding = PaddingValues(0.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ReceiveFlowStyle.PanelBrush)
                    .padding(start = 18.dp, end = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Play on turntable",
                    style = ReceiveFlowStyle.text(15.sp, FontWeight.Medium),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = null,
                    tint = VinylPalette.TealAccent,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

private const val PreviewMs = 30_000L

/** Messages this long or shorter are set large and centred on the letter. */
private const val ShortMessageMaxChars = 80

private val LetterTextInset = 28.dp
private val LetterFooterInset = 14.dp

private val ChipTeal = Color(0xFF0E8C85)
private val ChipInk = Color(0xFF0A6F69)
/** Apple Music and Spotify buttons; smaller than "Play on turntable" and centred beside it. */
private val StreamingIconSize = 44.dp

// Icon_Spotify.svg is 236.05 x 225.25 with a circle about 218.7 across. Scaled so the circle
// matches [StreamingIconSize], the whole canvas is this size.
private val SpotifyCanvasWidth = StreamingIconSize * (236.05f / 218.7f)
private val SpotifyCanvasHeight = StreamingIconSize * (225.25f / 218.7f)

@Preview(showBackground = true, backgroundColor = 0xFF1C1C1C, widthDp = 390, heightDp = 844)
@Composable
private fun MusicCardScreenPreview() {
    MusicCardScreen(
        state = MusicCardUiState(
            trackName = "Text Back Anthem",
            artistName = "Rio Han",
            previewUrl = "https://example.com/preview.m4a",
            mood = MoodTag.Romantic,
            message = "I have read the same three-word message nine times tonight. This is the song I put on to stop myself replying too fast. It did not work, obviously.",
            sentDateLabel = "8 Oct",
            hasDirection = true,
            isKept = false,
        ),
        onToggleKeep = {},
        onOpenCompass = {},
        onPlayOnTurntable = {},
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF1C1C1C, widthDp = 390, heightDp = 844)
@Composable
private fun MusicCardScreenKeptNoLocationPreview() {
    MusicCardScreen(
        state = MusicCardUiState(
            trackName = "A Very Long Song Title That Will Not Fit On One Line",
            artistName = "Somebody",
            mood = MoodTag.Calm,
            message = "Short one.",
            sentDateLabel = "8 Oct 2025",
            hasDirection = false,
            isKept = true,
            keepError = "Couldn't update your shelf. Try again.",
        ),
        onToggleKeep = {},
        onOpenCompass = {},
        onPlayOnTurntable = {},
    )
}
