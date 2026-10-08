package com.example.vinyl.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.vinyl.R
import com.example.vinyl.repository.RoomCard
import com.example.vinyl.ui.components.VinylSleeveThumbnail
import com.example.vinyl.ui.theme.VinylPalette

/**
 * The "Today" tab — the turntable, what arrived, and a glance at the shelf.
 *
 * Reads only. Picking a mood and being dealt letters is the receive flow; this screen's job is to
 * say whether anything is waiting and get out of the way.
 */
@Composable
fun HomeScreen(
    onOpenReceive: () -> Unit,
    onOpenSettings: () -> Unit,
    onSeeCollection: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    HomeScreen(
        state = state,
        onOpenReceive = onOpenReceive,
        onOpenSettings = onOpenSettings,
        onSeeCollection = onSeeCollection,
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
    onRefresh: () -> Unit = {},
    modifier: Modifier = Modifier,
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            TopBar(onOpenSettings = onOpenSettings)

            MoodGenreSummary(onClick = onOpenReceive)

            RecentlyCollected(
                items = state.recentlyCollected,
                onSeeAll = onSeeCollection,
            )

            ArrivedLine(count = state.arrivedCount, isLoading = state.isLoading)

            // Parked when there is nothing to open — a spinning record over an empty room reads as a
            // promise the screen can't keep.
            Turntable(
                playing = state.arrivedCount > 0,
                labelArtworkUrl = state.nowOnDeckArtworkUrl,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedButton(
                onClick = onOpenReceive,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(50),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, VinylPalette.TealAccent),
            ) {
                Text(
                    text = if (state.arrivedCount > 0) "Open Today's Cards" else "Pull three records",
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

            Spacer(Modifier.height(16.dp))
        }
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
 * A summary, not a control. The mood question lives in the receive flow — asking it is the daily
 * ritual, and duplicating the picker here would give two places to answer it and no agreement on
 * which one wins. Tapping opens the flow.
 */
@Composable
private fun MoodGenreSummary(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(VinylPalette.SheetSurface)
            .border(1.dp, VinylPalette.TextMuted.copy(alpha = 0.2f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SummaryField(label = "Today's Mood", value = "Not set yet", modifier = Modifier.weight(1f))
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(34.dp)
                .background(VinylPalette.TextMuted.copy(alpha = 0.2f)),
        )
        SummaryField(
            label = "Genre (optional)",
            value = "Any",
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp),
        )
    }
}

@Composable
private fun SummaryField(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(label, color = VinylPalette.TextMuted, fontSize = 11.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(value, color = VinylPalette.TextPrimary, fontSize = 15.sp)
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = VinylPalette.TealAccent,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun RecentlyCollected(items: List<RoomCard>, onSeeAll: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Recently collected",
                color = VinylPalette.TextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onSeeAll) {
                Text("See all", color = VinylPalette.TealAccent, fontSize = 14.sp)
            }
        }

        if (items.isEmpty()) {
            // Day one for every user, so it gets a real sentence rather than a blank row.
            Text(
                text = "Nothing kept yet. Records you save will line up here.",
                color = VinylPalette.TextMuted,
                fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items.forEach { card ->
                    VinylSleeveThumbnail(
                        songName = card.trackTitle,
                        artist = card.trackArtist,
                        coverUrl = card.artworkUrl,
                        accentColor = VinylPalette.BrownAccent,
                        sleeveSize = 84.dp,
                    )
                }
            }
        }

        // The rust shelf the sleeves stand on, straight from the design.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(VinylPalette.BrownAccent),
        )
    }
}

@Composable
private fun ArrivedLine(count: Int, isLoading: Boolean) {
    val text = when {
        isLoading -> "Checking the crate…"
        count == 0 -> "Nothing has arrived yet today."
        count == 1 -> "1 Music Card arrived today."
        else -> "$count Music Cards arrived today."
    }
    Text(
        text = text,
        color = if (count > 0) VinylPalette.TextPrimary else VinylPalette.TextMuted,
        fontSize = 20.sp,
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
    )
}

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
