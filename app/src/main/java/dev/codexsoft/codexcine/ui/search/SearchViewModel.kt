package dev.codexsoft.codexcine.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.codexsoft.codexcine.core.AppResult
import dev.codexsoft.codexcine.core.ErrorKind
import dev.codexsoft.codexcine.domain.model.SearchResults
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface SearchUiState {
    data object Idle : SearchUiState
    data class Results(val query: String, val results: SearchResults) : SearchUiState
    data class Failed(val kind: ErrorKind) : SearchUiState
}

class SearchViewModel(
    private val search: suspend (query: String) -> AppResult<SearchResults>,
) : ViewModel() {

    private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null
    private var currentQuery = ""
    private var answeredQuery: String? = null

    /** The user is still typing: wait for a pause before searching. */
    fun onQueryChanged(rawQuery: String) {
        val query = rawQuery.trim()
        if (query != currentQuery) schedule(query, DEBOUNCE_MS)
    }

    /** The user confirmed the query: search right away, unless it was already answered. */
    fun onQuerySubmitted(rawQuery: String) {
        val query = rawQuery.trim()
        if (query != answeredQuery) schedule(query, delayMs = 0)
    }

    private fun schedule(query: String, delayMs: Long) {
        searchJob?.cancel()
        currentQuery = query
        answeredQuery = null
        if (query.length < MIN_QUERY_LENGTH) {
            _uiState.value = SearchUiState.Idle
            return
        }
        searchJob = viewModelScope.launch {
            delay(delayMs)
            _uiState.value = when (val result = search(query)) {
                is AppResult.Success -> SearchUiState.Results(query, result.data)
                is AppResult.Error -> SearchUiState.Failed(result.kind)
            }
            answeredQuery = query
        }
    }

    private companion object {
        const val DEBOUNCE_MS = 400L
        const val MIN_QUERY_LENGTH = 2
    }
}
