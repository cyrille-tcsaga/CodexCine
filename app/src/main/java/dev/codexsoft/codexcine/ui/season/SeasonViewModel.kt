package dev.codexsoft.codexcine.ui.season

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.codexsoft.codexcine.core.AppResult
import dev.codexsoft.codexcine.core.ErrorKind
import dev.codexsoft.codexcine.data.repository.CatalogRepository
import dev.codexsoft.codexcine.domain.model.Episode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface SeasonUiState {
    data object Loading : SeasonUiState
    data class Content(val episodes: List<Episode>) : SeasonUiState
    data class Error(val kind: ErrorKind) : SeasonUiState
}

class SeasonViewModel(
    showId: Int,
    seasonNumber: Int,
    catalog: CatalogRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<SeasonUiState>(SeasonUiState.Loading)
    val uiState: StateFlow<SeasonUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _uiState.value = when (val result = catalog.season(showId, seasonNumber)) {
                is AppResult.Success -> SeasonUiState.Content(result.data)
                is AppResult.Error -> SeasonUiState.Error(result.kind)
            }
        }
    }
}
