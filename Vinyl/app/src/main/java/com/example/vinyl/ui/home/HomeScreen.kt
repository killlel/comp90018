package com.example.vinyl.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.vinyl.R
import com.example.vinyl.data.repository.placeholderAccent
import com.example.vinyl.repository.RoomCard
import com.example.vinyl.ui.components.ShelfLedge
import com.example.vinyl.ui.components.VinylSleeveThumbnail
import com.example.vinyl.ui.theme.VinylPalette
import com.example.vinyl.ui.theme.VinylSectionTitleStyle

/**
 * The "Today" tab — the turntable, what arrived, and a glance at the shelf.
 *
 * Reads only. Picking a mood and being dealt letters is the receive flow, reached through the one
 * button; there is deliberately no mood or genre picker here, so the question has one home.
 */
@Composable
fun HomeScreen(
    onOpenReceive: () -> Unit,
    onOpenSettings: () -> Unit,
    onSeeCollection: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel(),
    /** Activity-scoped: the same instance a music card's "Play this song" starts. */
    playback: PlaybackViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val nowPlaying by playback.nowPlaying.collectAsState()
    val playbackClock by playback.clock.collectAsState()

    HomeScreen(
        state = state,
        nowPlaying = nowPlaying,
        playbackClock = playbackClock,
        onOpenReceive = onOpenReceive,
        onOpenSettings = onOpenSettings,
        onSeeCollection = onSeeCollection,
        onStopPlaying = playback::stop,
        onRefresh = viewModel::refresh,
        modifier = modifier,
    )
}

/** Stateless half, so the preview and any future test can drive it directly. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeUiState,
    onOpenReceive: () -> Unit,
    onOpenSettings: () -> Unit,
    onSeeCollection: () -> Unit,
    modifier: Modifier = Modifier,
    /** The song playing on the turntable, or null when the deck is idle. */
    nowPlaying: NowPlaying? = null,
    /** Where the song is, for the progress bar. Null until sound starts. */
    playbackClock: PlaybackClock? = null,
    onStopPlaying: () -> Unit = {},
    onRefresh: () -> Unit = {},
) {
    // The room only changes when the server hands out new letters, so there is nothing to poll
    // for — but a user who has just sent one will pull to see if anything came back.
    PullToRefreshBox(
        isRefreshing = state.isLoading,
        onRefresh = onRefresh,
        modifier = modifier
            .fillMaxSize()
            .background(VinylPalette.Background),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // The "Now playing" card takes whatever height is left, so the screen is filled to the
            // bottom on a tall phone and nothing is pushed off it; a short phone scrolls instead.
            FillViewportColumn(
                viewportHeight = maxHeight,
                spacing = 18.dp,
                minFill = NOW_PLAYING_MIN_HEIGHT,
                maxFill = NOW_PLAYING_MAX_HEIGHT,
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                above = {
                    TopBar(onOpenSettings = onOpenSettings)

                    RecentlyCollected(
                        items = state.recentlyCollected,
                        onSeeAll = onSeeCollection,
                    )
                },
                // Directly above the deck it describes. Display only — the button below is the
                // way into today's cards.
                fill = {
                    NowPlayingCard(record = nowPlaying)
                },
                below = {
                    // The deck and its progress bar read as one unit, so they sit closer together
                    // than the sections around them.
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        // Bare, with the arm parked, unless music is playing. Tapping it then
                        // lifts the needle and stops the song.
                        Turntable(
                            playing = nowPlaying != null,
                            labelArtworkUrl = nowPlaying?.artworkUrl,
                            onStop = onStopPlaying,
                            startedAtMillis = nowPlaying?.startedAtMillis ?: 0L,
                            // The hero of the screen: it runs closer to the edges than the text.
                            modifier = Modifier.bleed(TURNTABLE_BLEED),
                        )
                        PlaybackProgressBar(clock = playbackClock)
                    }

                    OpenCardsButton(arrivedCount = state.arrivedCount, onClick = onOpenReceive)

                    // Zero-height end marker: the space above it is an ordinary gap, so the
                    // bottom margin always matches the spacing between the sections.
                    Spacer(Modifier)
                },
            )
        }
    }
}

@Composable
private fun OpenCardsButton(arrivedCount: Int, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(50),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, VinylPalette.TealAccent),
    ) {
        Text(
            text = if (arrivedCount > 0) "Open today's music cards" else "Find three music cards",
            color = VinylPalette.Cream,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.width(8.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = VinylPalette.TealAccent,
            modifier = Modifier.size(18.dp),
        )
    }
}

/**
 * Below this the card stops shrinking and the screen scrolls. Above it the card would outweigh
 * the turntable it only describes, so any extra height goes to the gaps instead.
 */
private val NOW_PLAYING_MIN_HEIGHT = 80.dp
private val NOW_PLAYING_MAX_HEIGHT = 120.dp

/** How far the turntable reaches past the screen's side padding, on each side. */
private val TURNTABLE_BLEED = 12.dp

/**
 * Lays the content out [amount] wider on each side than its parent allows, centred, so it can run
 * past the parent's padding. The parent still sees the original width.
 */
private fun Modifier.bleed(amount: Dp) = layout { measurable, constraints ->
    val extra = amount.roundToPx() * 2
    val width = constraints.maxWidth + extra
    val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
    layout(constraints.maxWidth, placeable.height) {
        placeable.place(-extra / 2, 0)
    }
}

@Composable
private fun TopBar(onOpenSettings: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.vinyl_logo_white),
            contentDescription = "Vinyl",
            modifier = Modifier.height(22.dp),
        )
        Text(
            text = "Today",
            color = VinylPalette.TextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onOpenSettings) {
            Icon(
                imageVector = Icons.Filled.Settings,
                contentDescription = "Settings",
                tint = VinylPalette.TextPrimary,
            )
        }
    }
}

/**
 * The newest kept records, on the same shelf as the Collection tab: the same header, three
 * sleeves filling the width, the same wooden ledge, and the same per-record sleeve colour.
 * Tapping a sleeve opens the Collection, where records can be played and starred.
 */
@Composable
private fun RecentlyCollected(items: List<RoomCard>, onSeeAll: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // Three sleeves, each with its disc overhang, fill the width exactly — as in Collection.
        val sleeveSize = maxWidth / SLEEVES_PER_SHELF / SLEEVE_WITH_DISC

        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Recently collected",
                    style = VinylSectionTitleStyle,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "See all",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable(onClick = onSeeAll),
                )
            }
            Spacer(Modifier.height(12.dp))

            if (items.isEmpty()) {
                // Day one for every user, so it gets a real sentence rather than a bare ledge.
                Text(
                    text = "Nothing kept yet. Music cards you keep will line up here.",
                    color = VinylPalette.TextMuted,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            } else {
                Row {
                    items.take(SLEEVES_PER_SHELF).forEach { card ->
                        VinylSleeveThumbnail(
                            songName = card.trackTitle,
                            artist = card.trackArtist,
                            coverUrl = card.artworkUrl,
                            // Same colour the record has in Collection, picked from its id.
                            accentColor = Color(placeholderAccent(card.submissionId)),
                            sleeveSize = sleeveSize,
                            modifier = Modifier.clickable(onClick = onSeeAll),
                        )
                    }
                }
            }

            ShelfLedge()
        }
    }
}

private const val SLEEVES_PER_SHELF = 3

/** A sleeve is drawn with its record poking out a quarter-width to the right. */
private const val SLEEVE_WITH_DISC = 1.25f

@Preview(showBackground = true, backgroundColor = 0xFF0D0D0D, widthDp = 393, heightDp = 900)
@Composable
private fun HomeScreenPreview() {
    HomeScreen(
        state = HomeUiState(arrivedCount = 3),
        onOpenReceive = {},
        onOpenSettings = {},
        onSeeCollection = {},
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0D0D, widthDp = 393, heightDp = 900)
@Composable
private fun HomeScreenEmptyPreview() {
    HomeScreen(
        state = HomeUiState(arrivedCount = 0),
        onOpenReceive = {},
        onOpenSettings = {},
        onSeeCollection = {},
    )
}
