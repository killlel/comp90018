package com.example.vinyl.ui.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.AccountCircle
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.vinyl.R
import com.example.vinyl.data.PULL_DAY_STARTS_AT_HOUR
import com.example.vinyl.data.repository.placeholderAccent
import com.example.vinyl.repository.RoomCard
import com.example.vinyl.ui.components.ShelfLedge
import com.example.vinyl.ui.components.VinylSleeveThumbnail
import com.example.vinyl.ui.settings.AvatarPreview
import com.example.vinyl.ui.theme.VinylPalette
import com.example.vinyl.ui.theme.VinylSectionTitleStyle
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** The signed-in user's profile picture, as Settings draws it. */
data class HomeAvatar(val iconIndex: Int, val gradientIndex: Int, val imageUrl: String?)

/**
 * The Home tab — today's cards, the way in to them, and the turntable.
 *
 * Reads only. Picking a mood and being dealt cards is the receive flow, reached through the one
 * button; there is deliberately no mood or genre picker here, so the question has one home.
 */
@Composable
fun HomeScreen(
    onOpenReceive: () -> Unit,
    onOpenSettings: () -> Unit,
    /** Opens one of today's cards, as picking it in Arrived Today would. */
    onOpenCard: (RoomCard) -> Unit,
    modifier: Modifier = Modifier,
    /** Shown top right, where it opens Settings. Null falls back to a generic profile icon. */
    avatar: HomeAvatar? = null,
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
        onOpenCard = onOpenCard,
        avatar = avatar,
        onTogglePause = playback::togglePause,
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
    modifier: Modifier = Modifier,
    onOpenCard: (RoomCard) -> Unit = {},
    avatar: HomeAvatar? = null,
    /** The song on the turntable, or null when the deck is idle. */
    nowPlaying: NowPlaying? = null,
    /** Where the song is, including a pending preview's pause state. Null when stopped. */
    playbackClock: PlaybackClock? = null,
    onTogglePause: () -> Unit = {},
    onRefresh: () -> Unit = {},
) {
    val paused = playbackClock?.isPaused == true

    // The room only changes when the server hands out new cards, so there is nothing to poll
    // for — but a user who has just pulled elsewhere will pull down to see them here.
    PullToRefreshBox(
        isRefreshing = state.isLoading,
        onRefresh = onRefresh,
        modifier = modifier
            .fillMaxSize()
            .background(VinylPalette.Background),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // Top to bottom: today's cards, the button into them, the song, the deck. Whatever
            // height the phone has spare is shared equally between the gaps, so nothing is
            // crowded while a hole opens up somewhere else. A short phone scrolls instead.
            EvenlySpacedColumn(
                viewportHeight = maxHeight,
                minSpacing = 18.dp,
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
            ) {
                TopBar(avatar = avatar, onOpenSettings = onOpenSettings)

                TodaysCardsShelf(
                    cards = state.arrivedToday,
                    onOpenCard = onOpenCard,
                )

                // One pull a day. It's used once today's cards are on the shelf, or the server
                // says so (say, pulled on another phone) — but a hand dealt and not yet shown can
                // always be opened.
                val usedToday = state.arrivedToday.isNotEmpty() ||
                    (state.pendingToday.isEmpty() && !state.canPull)
                OpenCardsButton(
                    canDraw = !state.isLoading && state.error == null && !usedToday,
                    usedToday = usedToday,
                    onClick = onOpenReceive,
                )

                NowPlayingPanel(
                    record = nowPlaying,
                    clock = playbackClock,
                    onTogglePause = onTogglePause,
                )

                // Bare, with the arm parked, unless music is playing. Tapping it then pauses or
                // resumes the song.
                Turntable(
                    playing = nowPlaying != null,
                    paused = paused,
                    labelArtworkUrl = nowPlaying?.artworkUrl,
                    onTogglePause = onTogglePause,
                    startedAtMillis = nowPlaying?.startedAtMillis ?: 0L,
                    // The hero of the screen: it runs closer to the edges than the text.
                    modifier = Modifier.bleed(TURNTABLE_BLEED),
                )

                // Zero-height end marker: the space above it is an ordinary gap, so the bottom
                // margin always matches the spacing between the sections.
                Spacer(Modifier)
            }
        }
    }
}

@Composable
private fun OpenCardsButton(canDraw: Boolean, usedToday: Boolean, onClick: () -> Unit) {
    val ink = if (canDraw) VinylPalette.Cream else VinylPalette.TextMuted
    OutlinedButton(
        onClick = onClick,
        enabled = canDraw,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(50),
        border = androidx.compose.foundation.BorderStroke(
            1.5.dp, if (canDraw) VinylPalette.TealAccent else VinylPalette.TextMuted.copy(alpha = 0.4f),
        ),
    ) {
        Text(
            text = if (usedToday) "New cards at $NEXT_PULL_TIME" else "Open today's card",
            color = ink,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.width(8.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = if (canDraw) VinylPalette.TealAccent else ink,
            modifier = Modifier.size(18.dp),
        )
    }
}

/** When the next pull opens, in the phone's own time format: "6:00 AM" or "06:00". */
private val NEXT_PULL_TIME: String =
    LocalTime.of(PULL_DAY_STARTS_AT_HOUR, 0).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))

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

/** The logo, and the user's avatar as the way into Settings. */
@Composable
private fun TopBar(avatar: HomeAvatar?, onOpenSettings: () -> Unit) {
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
            modifier = Modifier.height(26.dp),
        )
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onOpenSettings) {
            if (avatar != null) {
                AvatarPreview(
                    iconIndex = avatar.iconIndex,
                    gradientIndex = avatar.gradientIndex,
                    size = AVATAR_SIZE,
                    imageUrl = avatar.imageUrl,
                    label = "Profile and settings",
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.AccountCircle,
                    contentDescription = "Profile and settings",
                    tint = VinylPalette.TextPrimary,
                    modifier = Modifier.size(AVATAR_SIZE),
                )
            }
        }
    }
}

private val AVATAR_SIZE = 36.dp

/**
 * Today's three music cards as sleeves on a shelf, drawn like the Collection's: three sleeves
 * filling the width over the same wooden ledge, each in its record's sleeve colour. A card not
 * dealt yet keeps its place as an empty square, so the shelf looks the same before the first
 * pull as after it. Tapping a sleeve opens that card's details.
 */
@Composable
private fun TodaysCardsShelf(
    cards: List<RoomCard>,
    onOpenCard: (RoomCard) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // Three sleeves, each with its disc overhang, fill the width exactly — as in Collection.
        val sleeveSize = maxWidth / SLEEVES_PER_SHELF / SLEEVE_WITH_DISC

        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Today's cards",
                    style = VinylSectionTitleStyle,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(12.dp))

            Row {
                for (slot in 0 until SLEEVES_PER_SHELF) {
                    val card = cards.getOrNull(slot)
                    if (card != null) {
                        VinylSleeveThumbnail(
                            songName = card.trackTitle,
                            artist = card.trackArtist,
                            coverUrl = card.artworkUrl,
                            // Same colour the record has in Collection, picked from its id.
                            accentColor = Color(placeholderAccent(card.submissionId)),
                            sleeveSize = sleeveSize,
                            modifier = Modifier.clickable { onOpenCard(card) },
                        )
                    } else {
                        PendingSleeve(sleeveSize)
                    }
                }
            }

            ShelfLedge()
        }
    }
}

/**
 * A card not dealt yet, drawn as a record still in its plain paper inner sleeve: off-white paper
 * with a thumb notch, and a round window showing the record and its blank label. Nothing printed,
 * because nobody has picked the song yet. A soft light passes over the paper now and then, so the
 * slot reads as waiting rather than empty. Same footprint as a real sleeve, so the shelf doesn't
 * move when the cards arrive.
 */
@Composable
private fun PendingSleeve(sleeveSize: Dp) {
    // -1 → 3 sweeps the band across the sleeve (0 → 1) and then rests off it for a while.
    val sheen by rememberInfiniteTransition(label = "pendingSleeve").animateFloat(
        initialValue = -1f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(PENDING_SHEEN_MILLIS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "sheen",
    )
    val label = VinylPalette.TealAccent

    Box(Modifier.size(width = sleeveSize * SLEEVE_WITH_DISC, height = sleeveSize)) {
        // Behind the sleeve, as on a real one.
        Image(
            painter = painterResource(R.drawable.black_vinyl),
            contentDescription = null,
            modifier = Modifier
                .size(sleeveSize * PENDING_DISC_TO_SLEEVE)
                .align(Alignment.CenterEnd),
        )
        Box(
            Modifier
                .size(sleeveSize)
                .align(Alignment.CenterStart)
                .drawWithContent {
                    val corner = CornerRadius(2.dp.toPx())
                    // The paper, with the thumb notch at the top of every inner sleeve cut out of
                    // it, so the shelf behind shows through.
                    val notch = size.minDimension * PENDING_NOTCH_OF_SLEEVE
                    val paper = Path.combine(
                        operation = PathOperation.Difference,
                        path1 = Path().apply { addRoundRect(RoundRect(Rect(Offset.Zero, size), corner)) },
                        path2 = Path().apply { addOval(Rect(Offset(size.width / 2f, 0f), notch)) },
                    )
                    clipPath(paper) {
                        drawRect(Brush.verticalGradient(listOf(PendingPaperTop, PendingPaperBottom)))

                        // The window: the record inside, a groove or two, and its blank label.
                        val window = size.minDimension * PENDING_WINDOW_OF_SLEEVE
                        drawCircle(color = VinylPalette.RecordDark, radius = window)
                        drawCircle(
                            color = Color.White.copy(alpha = 0.08f),
                            radius = window * 0.82f,
                            style = Stroke(width = 1.dp.toPx()),
                        )
                        drawCircle(color = label.copy(alpha = 0.75f), radius = window * 0.55f)
                        drawCircle(color = VinylPalette.RecordDark, radius = window * 0.08f)
                        // The paper's cut edge, a shade darker than the paper.
                        drawCircle(
                            color = Color.Black.copy(alpha = 0.25f),
                            radius = window,
                            style = Stroke(width = 1.5.dp.toPx()),
                        )

                        // The passing light: a soft diagonal band.
                        val band = size.width * 0.6f
                        val x = sheen * size.width
                        drawRect(
                            Brush.linearGradient(
                                colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.22f), Color.Transparent),
                                start = Offset(x - band, 0f),
                                end = Offset(x, size.height),
                            ),
                        )
                    }
                },
        )
    }
}

/** The window's radius, and the thumb notch's, as fractions of the sleeve. */
private const val PENDING_WINDOW_OF_SLEEVE = 0.2f
private const val PENDING_NOTCH_OF_SLEEVE = 0.09f

/** The record's size against its sleeve — the same as a real sleeve's. */
private const val PENDING_DISC_TO_SLEEVE = 0.83f

/** One pass of the light plus the rest before the next. */
private const val PENDING_SHEEN_MILLIS = 3600

/** Off-white paper, dimmed so it sits with the covers beside it rather than glaring. */
private val PendingPaperTop = Color(0xFFD8D1C4)
private val PendingPaperBottom = Color(0xFFB8B0A2)

private const val SLEEVES_PER_SHELF = 3

/** A sleeve is drawn with its record poking out a quarter-width to the right. */
private const val SLEEVE_WITH_DISC = 1.25f

/**
 * A column whose children are spread down the whole screen with equal gaps between them, so a
 * tall phone has no blank strip at the bottom and no single hole in the middle. The gaps never
 * shrink below [minSpacing]; once they would, the column grows past the screen and scrolls.
 *
 * A plain `Column` can't do this: inside `verticalScroll` its height is unbounded, so
 * `Arrangement.SpaceBetween` has no height to spread.
 */
@Composable
private fun EvenlySpacedColumn(
    viewportHeight: Dp,
    minSpacing: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val placeables = measurables.map { it.measure(Constraints(maxWidth = constraints.maxWidth)) }
        val gaps = (placeables.size - 1).coerceAtLeast(0)
        val contentHeight = placeables.sumOf { it.height }
        val gap = if (gaps == 0) 0 else maxOf(
            minSpacing.roundToPx(),
            (viewportHeight.roundToPx() - contentHeight) / gaps,
        )

        val height = contentHeight + gap * gaps
        layout(constraints.maxWidth, maxOf(height, constraints.minHeight)) {
            var y = 0
            placeables.forEach {
                it.placeRelative(0, y)
                y += it.height + gap
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0D0D, widthDp = 393, heightDp = 900)
@Composable
private fun HomeScreenPreview() {
    HomeScreen(
        state = HomeUiState(),
        onOpenReceive = {},
        onOpenSettings = {},
    )
}
