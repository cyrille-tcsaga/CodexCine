package dev.codexsoft.codexcine.ui.browse

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.codexsoft.codexcine.core.AppResult
import dev.codexsoft.codexcine.core.ErrorKind
import dev.codexsoft.codexcine.data.progress.WatchProgress
import dev.codexsoft.codexcine.data.progress.WatchProgressRepository
import dev.codexsoft.codexcine.data.progress.WatchedEpisodesRepository
import dev.codexsoft.codexcine.data.repository.CatalogRepository
import dev.codexsoft.codexcine.data.repository.MyListRepository
import dev.codexsoft.codexcine.domain.model.CategoryType
import dev.codexsoft.codexcine.domain.model.Episode
import dev.codexsoft.codexcine.domain.model.HomeRow
import dev.codexsoft.codexcine.domain.model.MediaItem
import dev.codexsoft.codexcine.domain.model.MediaType
import dev.codexsoft.codexcine.domain.model.NewEpisode
import dev.codexsoft.codexcine.domain.model.NewEpisodeRules
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.time.LocalDate

sealed interface MainUiState {
    data object Loading : MainUiState

    /** [rows] is always complete and in display order, and grows as requests finish. */
    data class Content(val rows: List<HomeRow>) : MainUiState
    data class Error(val kind: ErrorKind) : MainUiState
}

class MainViewModel(
    private val catalog: CatalogRepository,
    private val myList: MyListRepository,
    private val watchProgress: WatchProgressRepository,
    private val watchedEpisodes: WatchedEpisodesRepository,
    private val today: () -> LocalDate = LocalDate::now,
) : ViewModel() {

    private val _uiState = MutableStateFlow<MainUiState>(MainUiState.Loading)
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val loadedRows = sortedMapOf<CategoryType, List<MediaItem>>()
    private var myListItems: List<MediaItem> = emptyList()
    private var continueWatching: List<WatchProgress> = emptyList()
    private var newEpisodes: List<NewEpisode> = emptyList()

    /** By show id, kept for the screen's lifetime. A failed lookup is left out, to be retried on resume. */
    private val lastEpisodes = HashMap<Int, Pair<MediaItem, Episode?>>()
    private var newEpisodesJob: Job? = null

    init {
        loadCatalog()
    }

    /** Called from onResume, because another screen may have edited the list or saved progress. */
    fun refreshLocalRows() {
        viewModelScope.launch {
            myListItems = myList.currentItems()
            // The debug harness saves under an id that does not exist on TMDB.
            continueWatching = watchProgress.currentItems().filter { it.tmdbId > 0 }
            publishRows()
            refreshNewEpisodes()
        }
    }

    /**
     * Followed shows are those in My List, then those being watched. Each costs one TMDB request, made
     * once per screen; on resume only the local state (what was watched since) is applied again.
     */
    private fun refreshNewEpisodes() {
        newEpisodesJob?.cancel()
        newEpisodesJob = viewModelScope.launch {
            val watched = watchedEpisodes.currentShows()
            val inProgressShows = continueWatching.filter { it.type == MediaType.TV }
            val followed = (
                myListItems.filter { it.mediaType == MediaType.TV }.map { it.id } +
                    inProgressShows.map { it.tmdbId } +
                    watched.map { it.showId }
                )
                .distinct()
                .take(NewEpisodeRules.MAX_SHOWS)

            val permits = Semaphore(MAX_PARALLEL_LOOKUPS)
            followed.filterNot { it in lastEpisodes }
                .map { id -> async { permits.withPermit { id to catalog.showWithLastEpisode(id) } } }
                .awaitAll()
                .forEach { (id, result) -> if (result is AppResult.Success) lastEpisodes[id] = result.data }

            newEpisodes = NewEpisodeRules.select(
                followed = followed.mapNotNull { lastEpisodes[it] },
                furthestWatched = watched.mapNotNull { show ->
                    show.furthest?.let { show.showId to (it.season to it.episode) }
                }.toMap(),
                inProgress = inProgressShows.map { NewEpisodeRules.key(it.tmdbId, it.season, it.episode) }.toSet(),
                startedShows = watched.map { it.showId }.toSet() + inProgressShows.map { it.tmdbId },
                today = today(),
            )
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
            if (continueWatching.isNotEmpty()) {
                add(HomeRow(CategoryType.CONTINUE_WATCHING, emptyList(), continueWatching))
            }
            if (newEpisodes.isNotEmpty()) {
                add(HomeRow(CategoryType.NEW_EPISODES, emptyList(), newEpisodes = newEpisodes))
            }
            if (myListItems.isNotEmpty()) add(HomeRow(CategoryType.MY_LIST, myListItems))
            loadedRows.forEach { (category, items) -> add(HomeRow(category, items)) }
            add(HomeRow(CategoryType.ABOUT, emptyList()))
        }
        _uiState.value = MainUiState.Content(rows)
    }

    private companion object {
        const val TAG = "MainViewModel"
        const val MAX_PARALLEL_LOOKUPS = 4
    }
}
