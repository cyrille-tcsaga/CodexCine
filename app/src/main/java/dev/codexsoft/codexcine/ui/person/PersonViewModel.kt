package dev.codexsoft.codexcine.ui.person

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.codexsoft.codexcine.core.AppResult
import dev.codexsoft.codexcine.core.ErrorKind
import dev.codexsoft.codexcine.data.repository.CatalogRepository
import dev.codexsoft.codexcine.domain.model.PersonDetails
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface PersonUiState {
    data object Loading : PersonUiState
    data class Content(val person: PersonDetails) : PersonUiState
    data class Error(val kind: ErrorKind) : PersonUiState
}

class PersonViewModel(personId: Int, catalog: CatalogRepository) : ViewModel() {

    private val _uiState = MutableStateFlow<PersonUiState>(PersonUiState.Loading)
    val uiState: StateFlow<PersonUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _uiState.value = when (val result = catalog.person(personId)) {
                is AppResult.Success -> PersonUiState.Content(result.data)
                is AppResult.Error -> PersonUiState.Error(result.kind)
            }
        }
    }
}
