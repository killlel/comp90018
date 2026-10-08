package com.example.vinyl.ui.collection

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.vinyl.data.model.RecordSource
import com.example.vinyl.data.model.VinylRecord
import com.example.vinyl.ui.components.ShelfLedge
import com.example.vinyl.ui.components.VinylSleeveThumbnail
import com.example.vinyl.ui.theme.VinylColors
import com.example.vinyl.ui.theme.VinylPalette
import com.example.vinyl.ui.theme.VinylSectionTitleStyle
import com.example.vinyl.ui.theme.VinylTheme

private val ScreenPadding = 24.dp

@Composable
fun CollectionScreen(
    modifier: Modifier = Modifier,
    viewModel: CollectionViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    // Every time the tab opens: a record kept in the receive flow should already be here.
    LaunchedEffect(Unit) { viewModel.refresh() }

    CollectionScreenContent(
        uiState = uiState,
        modifier = modifier,
        onFilterSelected = viewModel::selectFilter,
        onRefresh = viewModel::refresh,
        onRecordClick = viewModel::openRecord,
    )

    // The opened record is drawn by MainActivity as a music card, over the bottom bar too.
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CollectionScreenContent(
    uiState: CollectionUiState,
    modifier: Modifier = Modifier,
    onFilterSelected: (CollectionFilter) -> Unit = {},
    onRefresh: () -> Unit = {},
    onRecordClick: (VinylRecord) -> Unit = {},
) {
    // Three shelf tiles fill the content width exactly, as in the exported design.
    val contentWidth = LocalConfiguration.current.screenWidthDp.dp - ScreenPadding * 2
    val sleeveSize = contentWidth / 3 / 1.25f

    val isEmpty = !uiState.isLoading && uiState.sections.all { it.records.isEmpty() }

    PullToRefreshBox(
        isRefreshing = uiState.isLoading,
        onRefresh = onRefresh,
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 32.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                CollectionTopBar(modifier = Modifier.padding(horizontal = ScreenPadding))
            }

            item {
                FilterTabs(
                    selected = uiState.selectedFilter,
                    totalCount = uiState.totalCount,
                    onFilterSelected = onFilterSelected,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }

            if (uiState.error != null) {
                item {
                    CollectionMessage(uiState.error, modifier = Modifier.padding(horizontal = ScreenPadding))
                }
            } else if (isEmpty) {
                item {
                    CollectionMessage(
                        emptyMessage(uiState.selectedFilter),
                        modifier = Modifier.padding(horizontal = ScreenPadding),
                    )
                }
            }

            items(uiState.sections.filter { it.records.isNotEmpty() }, key = { it.title }) { section ->
                CollectionSectionRow(section = section, sleeveSize = sleeveSize, onRecordClick = onRecordClick)
            }
        }
    }
}

private fun emptyMessage(filter: CollectionFilter): String = when (filter) {
    CollectionFilter.SENT -> "Music cards you send will show up here."
    CollectionFilter.FAVOURITES -> "No favourites yet. Open a music card you kept and tap the star."
    else -> "Your shelf is empty. Music cards you keep will show up here."
}

@Composable
private fun CollectionTopBar(modifier: Modifier = Modifier) {
    Text(
        text = "My Collection",
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun FilterTabs(
    selected: CollectionFilter,
    totalCount: Int,
    onFilterSelected: (CollectionFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = ScreenPadding),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(CollectionFilter.entries) { filter ->
            val isSelected = filter == selected
            val label = if (filter == CollectionFilter.ALL) "${filter.label} ($totalCount)" else filter.label
            Box(
                modifier = Modifier
                    .height(28.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (isSelected) MaterialTheme.colorScheme.primary else VinylColors.Pill)
                    .clickable { onFilterSelected(filter) }
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else Color.White,
                )
            }
        }
    }
}

@Composable
private fun CollectionSectionRow(
    section: CollectionSection,
    sleeveSize: Dp,
    onRecordClick: (VinylRecord) -> Unit,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ScreenPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${section.title} (${section.records.size})",
                style = VinylSectionTitleStyle,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "See all",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        val listState = rememberLazyListState()
        // Each end fades only while there's more to scroll that way; animated so the edge eases
        // in and out instead of popping.
        val startFade by animateFloatAsState(if (listState.canScrollBackward) 1f else 0f, label = "startFade")
        val endFade by animateFloatAsState(if (listState.canScrollForward) 1f else 0f, label = "endFade")
        LazyRow(
            state = listState,
            modifier = Modifier
                // Same inset as the ShelfLedge below, so records never pass the shelf's ends.
                .padding(horizontal = ScreenPadding)
                .shelfEdgeFade(startFade, endFade),
        ) {
            items(section.records, key = { it.id }) { record ->
                VinylSleeveThumbnail(
                    songName = record.songName,
                    artist = record.artist,
                    coverUrl = record.coverUrl,
                    accentColor = Color(record.accentColor),
                    sleeveSize = sleeveSize,
                    localCoverRes = record.localCoverRes,
                    modifier = Modifier.clickable { onRecordClick(record) },
                )
            }
        }
        ShelfLedge(modifier = Modifier.padding(horizontal = ScreenPadding))
    }
}

private val ShelfFadeWidth = 24.dp

/**
 * Fades the row's content out over [ShelfFadeWidth] at each end. [start]/[end] are 0..1:
 * 0 leaves that edge crisp, 1 is a full fade.
 */
private fun Modifier.shelfEdgeFade(start: Float, end: Float): Modifier = this
    // Offscreen so the DstIn mask cuts this row only, not the screen behind it.
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val fade = (ShelfFadeWidth.toPx() / size.width).coerceIn(0f, 0.5f)
        drawRect(
            brush = Brush.horizontalGradient(
                0f to Color.Black.copy(alpha = 1f - start),
                fade to Color.Black,
                1f - fade to Color.Black,
                1f to Color.Black.copy(alpha = 1f - end),
            ),
            blendMode = BlendMode.DstIn,
        )
    }


@Composable
private fun CollectionMessage(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
        modifier = modifier.padding(vertical = 24.dp),
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0D0D, heightDp = 900)
@Composable
private fun CollectionScreenPreview() {
    VinylTheme {
        val records = listOf(
            VinylRecord(id = "1", songName = "Midnight Drive", artist = "Various Artists", coverUrl = null, accentColor = 0xFF6B3B32, localCoverRes = com.example.vinyl.R.drawable.cover_midnight_drive, isFavourite = true, isNew = true, mood = "Sad Mood"),
            VinylRecord(id = "2", songName = "Plastic Dreams", artist = "Luna Vale", coverUrl = null, accentColor = 0xFFF4F0EA),
            VinylRecord(id = "3", songName = "Solstice", artist = "Kai & Sun", coverUrl = null, accentColor = 0xFF77EDE5, isFavourite = true, mood = "Time to chill"),
            VinylRecord(id = "4", songName = "A Brighter Day", artist = "Nora Fields", coverUrl = null, accentColor = 0xFF8A4A3E, localCoverRes = com.example.vinyl.R.drawable.cover_a_brighter_day, source = RecordSource.SENT, mood = "Sad Mood"),
        )
        CollectionScreenContent(
            uiState = CollectionUiState(
                isLoading = false,
                totalCount = records.size,
                sections = listOf(
                    CollectionSection("Recently collected", records),
                    CollectionSection("Favourites", records.filter { it.isFavourite }),
                    CollectionSection("Sad Mood", records.filter { it.mood == "Sad Mood" }),
                    CollectionSection("Time to chill", records.filter { it.mood == "Time to chill" }),
                ),
            ),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0D0D, name = "Empty state")
@Composable
private fun CollectionScreenEmptyPreview() {
    VinylTheme {
        CollectionScreenContent(uiState = CollectionUiState(isLoading = false))
    }
}
