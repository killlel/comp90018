package com.example.vinyl.ui.collection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.vinyl.data.model.RecordSource
import com.example.vinyl.data.model.VinylRecord
import com.example.vinyl.data.repository.SupabaseVinylRepository
import com.example.vinyl.data.repository.VinylRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CollectionViewModel(
    private val repository: VinylRepository = SupabaseVinylRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(CollectionUiState())
    val uiState: StateFlow<CollectionUiState> = _uiState.asStateFlow()

    private var allRecords: List<VinylRecord> = emptyList()

    /**
     * Reloads the shelf. Called every time the Collection tab opens, since records are kept
     * elsewhere (the receive flow) and this view model outlives the screen.
     */
    fun refresh() {
        viewModelScope.launch {
            // Only the very first load blanks the screen; later ones refresh in place.
            _uiState.update { it.copy(isLoading = allRecords.isEmpty(), error = null) }
            repository.getCollection()
                .onSuccess { records ->
                    allRecords = records
                    publish()
                }
                .onFailure {
                    _uiState.update {
                        it.copy(isLoading = false, error = "Couldn't load your collection. Pull down to try again.")
                    }
                }
        }
    }

    fun selectFilter(filter: CollectionFilter) {
        _uiState.update { it.copy(selectedFilter = filter, sections = buildSections(allRecords, filter)) }
    }

    fun openRecord(record: VinylRecord) = _uiState.update { it.copy(openRecord = record, actionError = null) }

    fun closeRecord() = _uiState.update { it.copy(openRecord = null, actionError = null) }

    /** Optimistic: the star flips at once and flips back if the write fails. */
    fun toggleFavourite(record: VinylRecord) {
        if (record.source != RecordSource.RECEIVED) return
        val favourite = !record.isFavourite
        replace(record.id) { it.copy(isFavourite = favourite) }

        viewModelScope.launch {
            repository.setFavourite(record.id, favourite).onFailure {
                replace(record.id) { it.copy(isFavourite = !favourite) }
                _uiState.update { it.copy(actionError = "Couldn't update favourites. Try again.") }
            }
        }
    }

    /** Takes a received record off the shelf and closes it. Not optimistic: it's destructive. */
    fun remove(record: VinylRecord) {
        if (record.source != RecordSource.RECEIVED) return
        viewModelScope.launch {
            repository.remove(record.id)
                .onSuccess {
                    allRecords = allRecords.filterNot { it.id == record.id }
                    _uiState.update { it.copy(openRecord = null) }
                    publish()
                }
                .onFailure {
                    _uiState.update { it.copy(actionError = "Couldn't remove this record. Try again.") }
                }
        }
    }

    private fun replace(id: String, change: (VinylRecord) -> VinylRecord) {
        allRecords = allRecords.map { if (it.id == id) change(it) else it }
        _uiState.update { state -> state.copy(openRecord = state.openRecord?.let { if (it.id == id) change(it) else it }) }
        publish()
    }

    private fun publish() {
        _uiState.update {
            it.copy(
                isLoading = false,
                error = null,
                totalCount = allRecords.size,
                sections = buildSections(allRecords, it.selectedFilter),
            )
        }
    }
}

/** The shelf rows for one filter tab. Empty rows are left out, except the Favourites tab's own. */
internal fun buildSections(records: List<VinylRecord>, filter: CollectionFilter): List<CollectionSection> {
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
