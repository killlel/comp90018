package com.example.vinyl.ui.collection

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.vinyl.data.model.RecordSource
import com.example.vinyl.data.model.VinylRecord
import com.example.vinyl.ui.components.VinylSleeveThumbnail
import com.example.vinyl.ui.theme.VinylColors
import com.example.vinyl.ui.theme.VinylSectionTitleStyle
import com.example.vinyl.ui.theme.VinylTheme

private val ScreenPadding = 24.dp

@Composable
fun CollectionScreen(
    modifier: Modifier = Modifier,
    onProfileClick: () -> Unit = {},
    viewModel: CollectionViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    CollectionScreenContent(
        uiState = uiState,
        modifier = modifier,
        onFilterSelected = viewModel::selectFilter,
        onEditClick = onProfileClick,
    )
}

@Composable
private fun CollectionScreenContent(
    uiState: CollectionUiState,
    modifier: Modifier = Modifier,
    onFilterSelected: (CollectionFilter) -> Unit = {},
    onEditClick: () -> Unit = {},
) {
    // Three shelf tiles fill the content width exactly, as in the exported design.
    val contentWidth = LocalConfiguration.current.screenWidthDp.dp - ScreenPadding * 2
    val sleeveSize = contentWidth / 3 / 1.25f

    val isEmpty = !uiState.isLoading && uiState.sections.all { it.records.isEmpty() }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(top = 32.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            CollectionTopBar(
                onEditClick = onEditClick,
                modifier = Modifier.padding(horizontal = ScreenPadding),
            )
        }

        item {
            FilterTabs(
                selected = uiState.selectedFilter,
                totalCount = uiState.totalCount,
                onFilterSelected = onFilterSelected,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }

        if (isEmpty) {
            item {
                EmptyCollectionMessage(modifier = Modifier.padding(horizontal = ScreenPadding))
            }
        }

        items(uiState.sections, key = { it.title }) { section ->
            CollectionSectionRow(section = section, sleeveSize = sleeveSize)
        }
    }
}

@Composable
private fun CollectionTopBar(onEditClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "My Collections",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "Edit",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable(onClick = onEditClick),
        )
    }
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
private fun CollectionSectionRow(section: CollectionSection, sleeveSize: Dp) {
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
        LazyRow(contentPadding = PaddingValues(horizontal = ScreenPadding)) {
            items(section.records, key = { it.id }) { record ->
                VinylSleeveThumbnail(
                    songName = record.songName,
                    artist = record.artist,
                    coverUrl = record.coverUrl,
                    accentColor = Color(record.accentColor),
                    sleeveSize = sleeveSize,
                    localCoverRes = record.localCoverRes,
                )
            }
        }
        ShelfLedge()
    }
}

/** The wooden ledge each row of records stands on: a lit top face over a shadowed front edge. */
@Composable
private fun ShelfLedge() {
    Column(modifier = Modifier.padding(horizontal = ScreenPadding)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(VinylColors.ShelfTop, VinylColors.ShelfFade),
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(VinylColors.ShelfEdge),
        )
    }
}

@Composable
private fun EmptyCollectionMessage(modifier: Modifier = Modifier) {
    Text(
        text = "Your shelf is empty. Music cards you keep will show up here.",
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
