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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.vinyl.data.model.VinylRecord
import com.example.vinyl.ui.components.VinylCoverTile
import com.example.vinyl.ui.theme.VinylColors
import com.example.vinyl.ui.theme.VinylTheme

/**
 * "See all": every record in the collection as a two-column grid (collection-grid.png), under the
 * same filter pills as the shelves, with a search over song, artist and mood. Tapping a cover
 * opens the music card, drawn by MainActivity over this page.
 */
@Composable
internal fun CollectionGridScreen(
    uiState: CollectionUiState,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onFilterSelected: (CollectionFilter) -> Unit = {},
    onOpenSearch: () -> Unit = {},
    onCloseSearch: () -> Unit = {},
    onSearch: (String) -> Unit = {},
    onRecordClick: (VinylRecord) -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(top = 20.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(start = 8.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back to shelves",
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
            if (uiState.isSearching) {
                SearchField(
                    query = uiState.searchQuery,
                    onQueryChange = onSearch,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onCloseSearch) {
                    Icon(Icons.Filled.Close, contentDescription = "Close search", tint = MaterialTheme.colorScheme.onBackground)
                }
            } else {
                Text(
                    text = "Your Collection",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onOpenSearch) {
                    Icon(Icons.Filled.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.onBackground)
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        FilterTabs(
            selected = uiState.selectedFilter,
            totalCount = uiState.totalCount,
            onFilterSelected = onFilterSelected,
        )
        Spacer(Modifier.height(24.dp))

        val query = uiState.searchQuery.trim()
        when {
            uiState.error != null -> CollectionMessage(uiState.error, Modifier.padding(horizontal = ScreenPadding))

            uiState.gridRecords.isEmpty() && !uiState.isLoading -> CollectionMessage(
                // Removing the last record here lands on this too, rather than leaving the page.
                text = if (query.isNotEmpty()) "No music cards match “$query”." else emptyMessage(uiState.selectedFilter),
                modifier = Modifier.padding(horizontal = ScreenPadding),
            )

            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                items(uiState.gridRecords, key = { it.id }) { record ->
                    GridRecord(record = record, onClick = { onRecordClick(record) })
                }
            }
        }
    }
}

@Composable
private fun GridRecord(record: VinylRecord, onClick: () -> Unit) {
    Column(modifier = Modifier.clickable(onClick = onClick)) {
        VinylCoverTile(
            songName = record.songName,
            artist = record.artist,
            coverUrl = record.coverUrl,
            accentColor = Color(record.accentColor),
            localCoverRes = record.localCoverRes,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = record.songName,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = record.artist,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A pill-shaped search box that takes focus, and the keyboard, as soon as it appears. */
@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Box(
        modifier = modifier
            .height(40.dp)
            .background(VinylColors.Pill, RoundedCornerShape(50))
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (query.isEmpty()) {
            Text(
                "Search songs, artists or moods",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
            )
        }
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onBackground),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            // Results already update as you type, so "search" on the keyboard just puts it away.
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0D0D)
@Composable
private fun CollectionGridScreenPreview() {
    VinylTheme {
        CollectionGridScreen(
            uiState = CollectionUiState(
                isLoading = false,
                isGridOpen = true,
                totalCount = 2,
                gridRecords = listOf(
                    VinylRecord(id = "1", songName = "Midnight Drive", artist = "Various Artists", coverUrl = null, accentColor = 0xFF6B3B32),
                    VinylRecord(id = "2", songName = "Plastic Dreams", artist = "Luna Vale", coverUrl = null, accentColor = 0xFFF4F0EA),
                ),
            ),
        )
    }
}
