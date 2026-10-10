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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.example.vinyl.R
import com.example.vinyl.data.MoodOptions
import com.example.vinyl.data.MoodTag
import com.example.vinyl.data.THUMB_ARTWORK_PX
import com.example.vinyl.data.itunesArtworkAt
import com.example.vinyl.network.AudioPreviewController
import com.example.vinyl.network.rememberAudioPreviewController
import com.example.vinyl.ui.daily.ReceiveFlowStyle
import com.example.vinyl.ui.daily.moodIcon
import com.example.vinyl.ui.theme.ThemeState
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
    /** Why the last keep (or, from the Collection, the last favourite or remove) didn't go through. */
    val keepError: String? = null,
    /** The Collection's favourite state. Null hides it: the receive flow, and cards the reader sent. */
    val isFavourite: Boolean? = null,
    /** A card the reader sent themselves, opened from their Collection. Signed "you". */
    val isOwn: Boolean = false,
)

/**
 * The opened music card. Page 4 of the receive flow shows it full screen; the details sheet
 * (from the Home shelf or the Collection) shows it in a bottom sheet with [inSheet].
 *
 * The round play button plays the 30 second preview here and never navigates.
 *
 * @param onToggleKeep the labelled Collection action. Null hides it, as in the Collection.
 * @param onPlayOnTurntable null hides the button; the store links then fill the row.
 * @param onToggleFavourite the heart, shown when [MusicCardUiState.isFavourite] isn't null.
 * @param onRemove the Collection's remove action. Null hides it.
 * @param inSheet draws a drag handle instead of clearing the status bar, and stops and releases
 *   the preview when the app goes to the background (full screen it only pauses).
 * @param playerActive false stops and releases the preview at once, for a sheet that is closing
 *   but still drawn while it slides away.
 */
@Composable
fun MusicCardScreen(
    state: MusicCardUiState,
    onToggleKeep: (() -> Unit)?,
    onOpenCompass: () -> Unit,
    onPlayOnTurntable: (() -> Unit)?,
    onToggleFavourite: () -> Unit = {},
    onRemove: (() -> Unit)? = null,
    inSheet: Boolean = false,
    playerActive: Boolean = true,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VinylPalette.SheetSurface)
            .then(if (inSheet) Modifier else Modifier.statusBarsPadding())
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        if (inSheet) {
            // The sheet's drag strip covers the top 32dp, so the buttons start below it.
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(VinylPalette.Cream.copy(alpha = 0.3f)),
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        TopBar(
            state = state,
            onToggleKeep = onToggleKeep,
            onToggleFavourite = onToggleFavourite,
            onRemove = onRemove,
        )

        Spacer(modifier = Modifier.height(16.dp))

        PlayerCard(state = state, releaseOnBackground = inSheet, active = playerActive)

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

/**
 * The title and contextual actions. A received card names the unfamiliar keep action explicitly;
 * a card already in Collection uses the familiar heart and bin symbols for favourite and remove.
 */
@Composable
private fun TopBar(
    state: MusicCardUiState,
    onToggleKeep: (() -> Unit)?,
    onToggleFavourite: () -> Unit,
    onRemove: (() -> Unit)?,
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = TopBarTouchSize),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Card details",
            color = VinylPalette.Cream,
            style = ReceiveFlowStyle.Title,
            maxLines = 1,
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp),
        )
        state.isFavourite?.let { favourite ->
            TopBarButton(onClick = onToggleFavourite) {
                Icon(
                    if (favourite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = if (favourite) "Remove from favourites" else "Add to favourites",
                    tint = if (favourite) VinylPalette.TealAccent else VinylPalette.Cream,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        onRemove?.let { remove ->
            TopBarButton(onClick = remove) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "Remove from collection",
                    tint = VinylPalette.Cream,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        onToggleKeep?.let { keep ->
            CollectionButton(isKept = state.isKept, onClick = keep)
        }
    }
}

/** The receive flow spells out this action because a bookmark alone does not explain its scope. */
@Composable
private fun CollectionButton(isKept: Boolean, onClick: () -> Unit) {
    val colour = if (isKept) VinylPalette.TealAccent else VinylPalette.Cream
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.height(TopBarTouchSize),
        shape = CircleShape,
        border = BorderStroke(1.5.dp, colour),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = colour),
        contentPadding = PaddingValues(horizontal = 12.dp),
    ) {
        Icon(
            if (isKept) Icons.Rounded.Bookmark else Icons.Outlined.BookmarkBorder,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(7.dp))
        Text(
            text = if (isKept) "In collection" else "Add to collection",
            style = ReceiveFlowStyle.text(13.sp, FontWeight.Medium, 18.sp),
            maxLines = 1,
        )
    }
}

/** A 48dp touch target around a 24dp icon. */
@Composable
private fun TopBarButton(onClick: () -> Unit, icon: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(TopBarTouchSize)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        icon()
    }
}

/**
 * The song, with its preview playable in place. The countdown runs from 0:30 and the line
 * along the bottom edge fills while it plays; the preview stops at 0:30.
 */
@Composable
private fun PlayerCard(state: MusicCardUiState, releaseOnBackground: Boolean, active: Boolean) {
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

    // Leaving the app (the Home button, another app on top): full screen, the preview pauses and
    // stays at the same point until the play button is pressed again; in the sheet it stops and
    // is released, and starts over from 0:30.
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentUrl by rememberUpdatedState(url)
    val currentReleaseOnBackground by rememberUpdatedState(releaseOnBackground)
    DisposableEffect(lifecycleOwner, audio) {
        val observer = LifecycleEventObserver { _, event ->
            if (event != Lifecycle.Event.ON_STOP) return@LifecycleEventObserver
            if (currentReleaseOnBackground) {
                audio.release()
                elapsedMs = 0L
            } else if (audio.isPlaying && audio.currentUrl == currentUrl) {
                audio.toggle(currentUrl) // pauses
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // A sheet that is closing is still drawn while it slides away; the sound stops straight away.
    LaunchedEffect(active) {
        if (!active) {
            audio.release()
            elapsedMs = 0L
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
                // The note stands in while the artwork loads, if it fails, and when there is none.
                var artworkShown by remember(state.artworkUrl) { mutableStateOf(false) }
                if (!artworkShown) {
                    Icon(
                        Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = VinylPalette.Cream.copy(alpha = 0.55f),
                        modifier = Modifier.size(24.dp),
                    )
                }
                if (state.artworkUrl != null) {
                    AsyncImage(
                        // iTunes hands out 100px covers, soft at 56dp on a dense screen.
                        model = itunesArtworkAt(state.artworkUrl, THUMB_ARTWORK_PX),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        onSuccess = { artworkShown = true },
                        onError = { artworkShown = false },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.trackName,
                    color = VinylPalette.Cream,
                    style = ReceiveFlowStyle.text(18.sp, FontWeight.Medium, 26.sp),
                    maxLines = 2,
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
            // Time left in the preview, "0:30" down to "0:00". Tabular figures keep the digits
            // from shifting as they count.
            Text(
                text = countdown(PreviewMs - elapsedMs),
                color = VinylPalette.Cream.copy(alpha = 0.65f),
                style = ReceiveFlowStyle.text(12.sp, FontWeight.Light, 18.sp)
                    .copy(fontFeatureSettings = "tnum"),
            )
            Spacer(modifier = Modifier.width(10.dp))
            val enabled = url != null
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(VinylPalette.TealAccent.copy(alpha = if (enabled) 1f else 0.35f))
                    .clickable(enabled = enabled, role = Role.Button, onClick = onPlayPause),
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

/** Whole seconds left, rounded up so it reads "0:30" at the start and "0:00" only at the end. */
private fun countdown(remainingMs: Long): String {
    val seconds = ((remainingMs.coerceAtLeast(0L) + 999L) / 1000L).toInt()
    return "%d:%02d".format(seconds / 60, seconds % 60)
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
                text = if (state.isOwn) "— you" else "- someone, somewhere",
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

        Box(
            modifier = Modifier
                .padding(horizontal = LetterTextInset)
                .fillMaxWidth()
                .height(1.dp)
                .background(VinylPalette.Background.copy(alpha = 0.1f)),
        )
        Spacer(modifier = Modifier.height(14.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = LetterTextInset),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            state.mood?.let { mood ->
                val moodLabel = MoodOptions.all.firstOrNull { it.tag == mood }?.title ?: mood.name
                Icon(
                    painter = painterResource(moodIcon(mood)),
                    contentDescription = "Sender's mood: $moodLabel",
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
    // The chip sits on the cream letter, so it uses the accent's darker on-cream shade.
    val chipInk = ThemeState.accent.onCream
    Row(
        modifier = Modifier
            .height(48.dp)
            .clip(CircleShape)
            .background(chipInk.copy(alpha = 0.06f))
            .border(1.5.dp, chipInk, CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = 10.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.Explore,
            contentDescription = null,
            tint = chipInk,
            modifier = Modifier.size(28.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "See where it's from",
            color = chipInk,
            style = ReceiveFlowStyle.text(14.sp, FontWeight.Medium, 20.sp),
        )
    }
}

/**
 * Apple Music and Spotify together in one pill, then "Play on turntable". Each service opens a
 * search for this song, the same links the Collection's card uses: the card only knows the title
 * and artist. The row spans exactly the letter card's width. With no [onPlayOnTurntable] the pill
 * takes the whole row, each service a labelled half.
 */
@Composable
private fun BottomRow(trackName: String, artistName: String, onPlayOnTurntable: (() -> Unit)?) {
    val uriHandler = LocalUriHandler.current
    val query = Uri.encode("$trackName $artistName")
    val openAppleMusic = { uriHandler.openUri("https://music.apple.com/search?term=$query") }
    val openSpotify = { uriHandler.openUri("https://open.spotify.com/search/$query") }

    if (onPlayOnTurntable == null) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(CircleShape)
                .background(ReceiveFlowStyle.PanelBrush)
                .border(1.5.dp, ReceiveFlowStyle.PanelBorder, CircleShape),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StoreHalf(label = "Apple Music", onClickLabel = "Open in Apple Music", onClick = openAppleMusic) {
                AppleMusicIcon(contentDescription = null)
            }
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(24.dp)
                    .background(ReceiveFlowStyle.PanelBorder),
            )
            StoreHalf(label = "Spotify", onClickLabel = "Open in Spotify", onClick = openSpotify) {
                SpotifyIcon(contentDescription = null)
            }
        }
        return
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .height(56.dp)
                .clip(CircleShape)
                .background(ReceiveFlowStyle.PanelBrush)
                .border(1.5.dp, ReceiveFlowStyle.PanelBorder, CircleShape)
                .padding(horizontal = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Both official icons are drawn as supplied: no backing shape, no tint. Each sits in
            // a 48dp touch target; the padding keeps the pill and the icons where they were.
            Box(
                modifier = Modifier
                    .size(StreamingTouchSize)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = openAppleMusic),
                contentAlignment = Alignment.Center,
            ) {
                AppleMusicIcon(contentDescription = "Open in Apple Music")
            }
            Box(
                modifier = Modifier
                    .size(StreamingTouchSize)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = openSpotify),
                contentAlignment = Alignment.Center,
            ) {
                SpotifyIcon(contentDescription = "Open in Spotify")
            }
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

/** One labelled half of the full-width store pill: the official icon, then the service's name. */
@Composable
private fun RowScope.StoreHalf(
    label: String,
    onClickLabel: String,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clickable(role = Role.Button, onClickLabel = onClickLabel, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(StreamingIconSize), contentAlignment = Alignment.Center) { icon() }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = label,
            color = VinylPalette.Cream,
            style = ReceiveFlowStyle.text(15.sp, FontWeight.Medium, 22.sp),
            maxLines = 1,
        )
    }
}

@Composable
private fun AppleMusicIcon(contentDescription: String?) {
    Image(
        painter = painterResource(R.drawable.ic_apple_music),
        contentDescription = contentDescription,
        modifier = Modifier.size(StreamingIconSize),
    )
}

/**
 * The file keeps a little empty canvas around its circle, so it is drawn just larger than its
 * slot to make the circle itself the slot's size. Only that transparent margin spills past.
 */
@Composable
private fun SpotifyIcon(contentDescription: String?) {
    Image(
        painter = painterResource(R.drawable.ic_spotify),
        contentDescription = contentDescription,
        modifier = Modifier.requiredSize(SpotifyCanvasWidth, SpotifyCanvasHeight),
    )
}

private const val PreviewMs = 30_000L

/** Messages this long or shorter are set large and centred on the letter. */
private const val ShortMessageMaxChars = 80

private val LetterTextInset = 28.dp

/** Apple Music and Spotify icons inside their shared pill. */
private val StreamingIconSize = 36.dp
private val StreamingTouchSize = 48.dp
private val TopBarTouchSize = 48.dp

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
            trackName = "A Very Long Song Title That Wraps Onto a Second Line Here",
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

/** History mode, as the Collection opens it: star and menu, no bookmark, compass or turntable. */
@Preview(showBackground = true, backgroundColor = 0xFF1C1C1C, widthDp = 390, heightDp = 743)
@Composable
private fun MusicCardHistoryPreview() {
    MusicCardScreen(
        state = MusicCardUiState(
            trackName = "Roygbiv",
            artistName = "Boards of Canada",
            previewUrl = "https://example.com/preview.m4a",
            mood = MoodTag.Nostalgic,
            message = "Sounds like an old home video of a summer you half remember.",
            sentDateLabel = "6 Oct",
            isFavourite = true,
        ),
        onToggleKeep = null,
        onOpenCompass = {},
        onPlayOnTurntable = null,
        onRemove = {},
        inSheet = true,
    )
}

/** A card the reader sent: no star or menu, signed by them. */
@Preview(showBackground = true, backgroundColor = 0xFF1C1C1C, widthDp = 390, heightDp = 743)
@Composable
private fun MusicCardSentPreview() {
    MusicCardScreen(
        state = MusicCardUiState(
            trackName = "Liability",
            artistName = "Lorde",
            previewUrl = "https://example.com/preview.m4a",
            mood = MoodTag.Lonely,
            message = "For when you feel like too much for everyone. You are not.",
            sentDateLabel = "5 Oct",
            isOwn = true,
        ),
        onToggleKeep = null,
        onOpenCompass = {},
        onPlayOnTurntable = null,
        inSheet = true,
    )
}

/** Receive mode in the sheet, for a song with no preview: the play button is greyed out. */
@Preview(showBackground = true, backgroundColor = 0xFF1C1C1C, widthDp = 390, heightDp = 743)
@Composable
private fun MusicCardNoPreviewUrlPreview() {
    MusicCardScreen(
        state = MusicCardUiState(
            trackName = "Heaven or Las Vegas",
            artistName = "Cocteau Twins",
            previewUrl = null,
            mood = MoodTag.Romantic,
            message = "You won't catch half the words. You don't need to.",
            sentDateLabel = "7 Oct",
            hasDirection = true,
        ),
        onToggleKeep = {},
        onOpenCompass = {},
        onPlayOnTurntable = {},
        inSheet = true,
    )
}
