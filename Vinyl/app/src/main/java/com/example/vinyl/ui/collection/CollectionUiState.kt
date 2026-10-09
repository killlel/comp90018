package com.example.vinyl.ui.collection

import com.example.vinyl.data.model.VinylRecord

enum class CollectionFilter(val label: String) {
    ALL("All"),
    RECEIVED("Received"),
    SENT("Sent"),
    FAVOURITES("Favourites"),
}

data class CollectionSection(
    val title: String,
    val records: List<VinylRecord>,
)

data class CollectionUiState(
    val isLoading: Boolean = true,
    val totalCount: Int = 0,
    val selectedFilter: CollectionFilter = CollectionFilter.ALL,
    val sections: List<CollectionSection> = emptyList(),
    /** Set when nothing could be loaded. A shelf that's merely empty is not an error. */
    val error: String? = null,
    /** The record opened from the shelf, or null when none is. */
    val openRecord: VinylRecord? = null,
    /** A favourite or remove that didn't go through, shown on the opened record. */
    val actionError: String? = null,
    /** "See all": every record as one grid, under the same filter pills, with a search box. */
    val isGridOpen: Boolean = false,
    /** The grid's search box is showing. */
    val isSearching: Boolean = false,
    val searchQuery: String = "",
    /** The grid's contents: [selectedFilter] and [searchQuery] applied, newest first. */
    val gridRecords: List<VinylRecord> = emptyList(),
)
