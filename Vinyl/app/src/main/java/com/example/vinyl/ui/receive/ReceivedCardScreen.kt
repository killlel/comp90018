package com.example.vinyl.ui.receive

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
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
    /** Why [senderDistanceLabel] is missing; shown in place of the distance block. */
    val senderDistanceNote: String? = null,
    val senderWeatherLabel: String? = null,
    val sentTimeLabel: String,
    val isKept: Boolean = false,
    /** Why the last keep didn't go through, shown under the buttons. */
    val keepError: String? = null,
    /** The Collection's star. Null hides it (the receive flow, and cards you sent). */
    val isFavourite: Boolean? = null,
    /** A card the reader sent themselves, opened from their Collection. */
    val isOwn: Boolean = false,
)

@Composable
fun ReceivedCardScreen(
    state: ReceivedCardUiState,
    onClose: () -> Unit = {},
    /** Null hides the keep button - a card you sent yourself can't be kept. */
    onKeep: (() -> Unit)? = {},
    /** Set from the Collection: the keep button becomes "Remove from collection" and calls this. */
    onRemove: (() -> Unit)? = null,
    onToggleFavourite: () -> Unit = {},
    /** The DISTANCE block calls this - only when a distance is actually known (see below), since
     *  there is nothing to show a bearing for otherwise. Intended target: CompassScreen. */
    onViewDirection: () -> Unit = {},
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
                text = if (state.isOwn) "MUSIC CARD YOU SENT" else "MUSIC CARD · ANONYMOUS",
                color = VinylPalette.TextMuted,
                fontSize = 11.sp,
                letterSpacing = 1.sp,
                modifier = Modifier.weight(1f),
            )
            state.isFavourite?.let { favourite ->
                IconButton(onClick = onToggleFavourite) {
                    Icon(
                        if (favourite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        contentDescription = if (favourite) "Remove from favourites" else "Add to favourites",
                        tint = if (favourite) VinylPalette.TealAccent else VinylPalette.TextMuted,
                    )
                }
            }
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
                text = when {
                    state.isOwn -> "— you"
                    else -> "— ${state.senderDistanceLabel?.let { "someone $it away" } ?: "someone, somewhere"}"
                },
                color = letterTextMuted,
                fontSize = 12.sp,
                modifier = Modifier.align(Alignment.End),
            )
        }

        // No location on one end: the DISTANCE block is left out and only the note below says why.
        val distanceLabel = state.senderDistanceLabel
        if (distanceLabel != null || state.senderDistanceNote != null || state.senderWeatherLabel != null) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "CAME WITH THE MUSIC CARD",
                    color = VinylPalette.TextMuted,
                    fontSize = 10.sp,
                    letterSpacing = 1.sp,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                ) {
                    if (distanceLabel != null) {
                        ReceivedInfoBlock(
                            label = "DISTANCE",
                            value = distanceLabel,
                            hint = "Tap for direction",
                            onClick = onViewDirection,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                    state.senderWeatherLabel?.let {
                        ReceivedInfoBlock(label = "THEIR SKY", value = it, modifier = Modifier.weight(1f).fillMaxHeight())
                    }
                    ReceivedInfoBlock(label = "SENT", value = state.sentTimeLabel, modifier = Modifier.weight(1f).fillMaxHeight())
                }
                state.senderDistanceNote?.let { note ->
                    Text(text = note, color = VinylPalette.TextMuted, fontSize = 12.sp, lineHeight = 17.sp)
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Not wired to anything yet - same style as the row below it.
        OutlinedButton(
            onClick = {},
            modifier = Modifier.fillMaxWidth().height(80.dp),
            shape = RoundedCornerShape(30),
            border = BorderStroke(1.5.dp, VinylPalette.Cream),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = VinylPalette.Cream),
        ) {
            Text("Play this song", fontWeight = FontWeight.SemiBold)
        }

        StreamingBadges(trackName = state.trackName, artistName = state.artistName)

        Spacer(modifier = Modifier.height(12.dp))

        // From the Collection it's a remove; in the receive flow a keep toggle; for your own card, nothing.
        val keepAction = onRemove ?: onKeep
        if (keepAction != null) {
            OutlinedButton(
                onClick = keepAction,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(50),
                border = BorderStroke(1.5.dp, VinylPalette.Cream),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = VinylPalette.Cream),
            ) {
                Text(
                    text = when {
                        onRemove != null -> "Remove from collection"
                        state.isKept -> "Kept"
                        else -> "Keep this music card"
                    },
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        state.keepError?.let {
            Text(
                text = it,
                color = VinylPalette.Cream,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

/**
 * "Listen on Apple Music" and Spotify, side by side. Each opens a search for this song - the
 * card only knows the title and artist, not either service's own track id. Android hands the
 * link to the app when it's installed, the browser when it isn't.
 */
@Composable
private fun StreamingBadges(trackName: String, artistName: String) {
    val uriHandler = LocalUriHandler.current
    val query = Uri.encode("$trackName $artistName")

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
    ) {
        // Apple's own badge, used as supplied: black with a grey hairline and its own padding.
        Image(
            painter = painterResource(R.drawable.badge_apple_music),
            contentDescription = "Listen on Apple Music",
            modifier = Modifier
                .height(BadgeHeight)
                .aspectRatio(APPLE_BADGE_ASPECT)
                .clip(RoundedCornerShape(BadgeCorner))
                .clickable { uriHandler.openUri("https://music.apple.com/search?term=$query") },
        )
        // Spotify ships only a logo, so it gets a badge built to match Apple's: same size,
        // black fill, grey hairline, rounded corners, the logo centred with matching padding.
        Box(
            modifier = Modifier
                .height(BadgeHeight)
                .aspectRatio(APPLE_BADGE_ASPECT)
                .clip(RoundedCornerShape(BadgeCorner))
                .background(Color.Black)
                .border(1.dp, BadgeOutline, RoundedCornerShape(BadgeCorner))
                .clickable { uriHandler.openUri("https://open.spotify.com/search/$query") },
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.badge_spotify_logo),
                contentDescription = "Listen on Spotify",
                modifier = Modifier.fillMaxHeight(SPOTIFY_LOGO_HEIGHT_FRACTION),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

private val BadgeHeight = 44.dp

/** Apple's badge export is 390x114; its corners are about a seventh of its height. */
private const val APPLE_BADGE_ASPECT = 390f / 114f
private val BadgeCorner = 6.dp

/** The grey of Apple's hairline, sampled from the export. */
private val BadgeOutline = Color(0xFFABABAB)

/** Matches the height of Apple's note icon inside its badge, so the two logos read as a pair. */
private const val SPOTIFY_LOGO_HEIGHT_FRACTION = 0.6f

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
private fun ReceivedInfoBlock(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    /** Shown as a small extra line under the label, e.g. "Tap for direction". Null hides it. */
    hint: String? = null,
    /** Null means not clickable at all - the block looks and behaves exactly as before. */
    onClick: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .border(1.dp, VinylPalette.TextMuted.copy(alpha = 0.15f), RoundedCornerShape(14.dp))
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
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
        hint?.let {
            Spacer(modifier = Modifier.height(3.dp))
            Text(it, color = VinylPalette.TealAccent, fontSize = 9.sp, lineHeight = 11.sp, maxLines = 2)
        }
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