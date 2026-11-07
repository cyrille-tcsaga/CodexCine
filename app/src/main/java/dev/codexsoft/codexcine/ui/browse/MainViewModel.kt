package dev.codexsoft.codexcine.ui.browse

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.codexsoft.codexcine.core.AppResult
import dev.codexsoft.codexcine.core.ErrorKind
import dev.codexsoft.codexcine.data.repository.CatalogRepository
import dev.codexsoft.codexcine.data.repository.MyListRepository
import dev.codexsoft.codexcine.domain.model.CategoryType
import dev.codexsoft.codexcine.domain.model.HomeRow
import dev.codexsoft.codexcine.domain.model.MediaItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface MainUiState {
    data object Loading : MainUiState

    /** [rows] is always complete and in display order, and grows as requests finish. */
    data class Content(val rows: List<HomeRow>) : MainUiState
    data class Error(val kind: ErrorKind) : MainUiState
}

class MainViewModel(
    private val catalog: CatalogRepository,
    private val myList: MyListRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<MainUiState>(MainUiState.Loading)
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val loadedRows = sortedMapOf<CategoryType, List<MediaItem>>()
    private var myListItems: List<MediaItem> = emptyList()

    init {
        loadCatalog()
    }

    /** Called from onResume, because the list may have been edited on another screen. */
    fun refreshMyList() {
        viewModelScope.launch {
            myListItems = myList.currentItems()
            publishRows()
        }
    }

    private fun loadCatalog() {
        viewModelScope.launch {
            val failures = mutableListOf<ErrorKind>()
            catalog.homeRows().collect { (category, result) ->
                when (result) {
                    is AppResult.Success -> if (result.data.items.isNotEmpty()) {
                        loadedRows[category] = result.data.items
                        publishRows()
                    }
                    is AppResult.Error -> {
                        Log.w(TAG, "Skipping row $category: ${result.kind} ${result.message}")
                        failures += result.kind
                    }
                }
            }
            if (loadedRows.isEmpty()) {
                // The most actionable failure wins, as declared in ErrorKind: token, then network.
                _uiState.value = MainUiState.Error(failures.minOrNull() ?: ErrorKind.GENERIC)
            }
        }
    }

    private fun publishRows() {
        if (loadedRows.isEmpty()) return
        val rows = buildList {
            if (myListItems.isNotEmpty()) add(HomeRow(CategoryType.MY_LIST, myListItems))
            loadedRows.forEach { (category, items) -> add(HomeRow(category, items)) }
            add(HomeRow(CategoryType.ABOUT, emptyList()))
        }
        _uiState.value = MainUiState.Content(rows)
    }

    private companion object {
        const val TAG = "MainViewModel"
    }
}
