package dev.codexsoft.codexcine.ui.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.codexsoft.codexcine.core.AppResult
import dev.codexsoft.codexcine.core.ErrorKind
import dev.codexsoft.codexcine.data.repository.CatalogRepository
import dev.codexsoft.codexcine.data.repository.MyListRepository
import dev.codexsoft.codexcine.domain.model.MediaDetails
import dev.codexsoft.codexcine.domain.model.MediaItem
import dev.codexsoft.codexcine.domain.model.MediaType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface DetailsUiState {
    data object Loading : DetailsUiState
    data class Content(val details: MediaDetails, val isInMyList: Boolean) : DetailsUiState
    data class Error(val kind: ErrorKind) : DetailsUiState
}

class DetailsViewModel(
    private val mediaId: Int,
    private val mediaType: MediaType,
    private val catalog: CatalogRepository,
    private val myList: MyListRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<DetailsUiState>(DetailsUiState.Loading)
    val uiState: StateFlow<DetailsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            when (val result = catalog.details(mediaId, mediaType)) {
                is AppResult.Error -> _uiState.value = DetailsUiState.Error(result.kind)
                is AppResult.Success -> myList.contains(mediaId, mediaType).collect { isInList ->
                    _uiState.value = DetailsUiState.Content(result.data, isInList)
                }
            }
        }
    }

    fun toggleMyList() {
        val details = (uiState.value as? DetailsUiState.Content)?.details ?: return
        viewModelScope.launch {
            myList.toggle(
                MediaItem(
                    id = details.id,
                    title = details.title,
                    posterUrl = details.posterUrl,
                    backdropUrl = details.backdropUrl,
                    releaseYear = details.releaseYear,
                    voteAverage = details.voteAverage,
                    mediaType = details.mediaType,
                ),
            )
        }
    }
}
