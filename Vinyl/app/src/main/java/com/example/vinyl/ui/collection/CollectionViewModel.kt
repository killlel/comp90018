package com.example.vinyl.ui.collection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.vinyl.data.model.RecordSource
import com.example.vinyl.data.model.VinylRecord
import com.example.vinyl.data.repository.FakeVinylRepository
import com.example.vinyl.data.repository.VinylRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CollectionViewModel(
    private val repository: VinylRepository = FakeVinylRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(CollectionUiState())
    val uiState: StateFlow<CollectionUiState> = _uiState.asStateFlow()

    private var allRecords: List<VinylRecord> = emptyList()

    init {
        viewModelScope.launch {
            allRecords = repository.getCollection()
            _uiState.value = CollectionUiState(
                isLoading = false,
                totalCount = allRecords.size,
                selectedFilter = CollectionFilter.ALL,
                sections = buildSections(allRecords, CollectionFilter.ALL),
            )
        }
    }

    fun selectFilter(filter: CollectionFilter) {
        _uiState.value = _uiState.value.copy(
            selectedFilter = filter,
            sections = buildSections(allRecords, filter),
        )
    }

    private fun buildSections(records: List<VinylRecord>, filter: CollectionFilter): List<CollectionSection> {
        if (filter == CollectionFilter.FAVOURITES) {
            return listOf(CollectionSection("Favourites", records.filter { it.isFavourite }))
        }

        val scoped = when (filter) {
            CollectionFilter.RECEIVED -> records.filter { it.source == RecordSource.RECEIVED }
            CollectionFilter.SENT -> records.filter { it.source == RecordSource.SENT }
            else -> records
        }

        val sections = mutableListOf<CollectionSection>()
        if (scoped.isNotEmpty()) {
            sections += CollectionSection("Recently collected", scoped)
        }

        val favourites = scoped.filter { it.isFavourite }
        if (favourites.isNotEmpty()) {
            sections += CollectionSection("Favourites", favourites)
        }

        val moods = scoped.mapNotNull { it.mood }.distinct()
        for (mood in moods) {
            sections += CollectionSection(mood, scoped.filter { it.mood == mood })
        }

        return sections
    }
}
