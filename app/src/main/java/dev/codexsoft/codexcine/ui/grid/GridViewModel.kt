package dev.codexsoft.codexcine.ui.grid

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.codexsoft.codexcine.core.AppResult
import dev.codexsoft.codexcine.core.ErrorKind
import dev.codexsoft.codexcine.domain.model.MediaItem
import dev.codexsoft.codexcine.domain.model.MediaPage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface GridUiState {
    data object Loading : GridUiState
    data class Content(val items: List<MediaItem>) : GridUiState
    data class Error(val kind: ErrorKind) : GridUiState
}

class GridViewModel(
    private val loadPage: suspend (page: Int) -> AppResult<MediaPage>,
) : ViewModel() {

    private val _uiState = MutableStateFlow<GridUiState>(GridUiState.Loading)
    val uiState: StateFlow<GridUiState> = _uiState.asStateFlow()

    private val items = mutableListOf<MediaItem>()
    private var nextPage = 1
    private var totalPages = Int.MAX_VALUE
    private var isLoading = false

    init {
        loadNextPage()
    }

    fun onItemSelected(position: Int) {
        if (position >= items.size - PRELOAD_THRESHOLD) loadNextPage()
    }

    private fun loadNextPage() {
        if (isLoading || nextPage > totalPages) return
        isLoading = true
        viewModelScope.launch {
            when (val result = loadPage(nextPage)) {
                is AppResult.Success -> {
                    // TMDB reshuffles popularity lists between requests, so pages can overlap.
                    val known = items.mapTo(HashSet()) { it.mediaType to it.id }
                    items += result.data.items.filter { known.add(it.mediaType to it.id) }
                    totalPages = result.data.totalPages
                    nextPage++
                    _uiState.value = GridUiState.Content(items.toList())
                }
                is AppResult.Error -> {
                    if (items.isEmpty()) {
                        _uiState.value = GridUiState.Error(result.kind)
                    } else {
                        // Keep what is on screen. The next selection tries this page again.
                        Log.w(TAG, "Could not load page $nextPage: ${result.kind}")
                    }
                }
            }
            isLoading = false
        }
    }

    private companion object {
        const val TAG = "GridViewModel"
        const val PRELOAD_THRESHOLD = 10
    }
}
