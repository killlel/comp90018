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
)
