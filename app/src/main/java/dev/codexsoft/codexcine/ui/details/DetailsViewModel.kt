package dev.codexsoft.codexcine.ui.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.codexsoft.codexcine.core.AppResult
import dev.codexsoft.codexcine.core.ErrorKind
import dev.codexsoft.codexcine.data.progress.WatchProgressRepository
import dev.codexsoft.codexcine.data.repository.CatalogRepository
import dev.codexsoft.codexcine.data.repository.MyListRepository
import dev.codexsoft.codexcine.data.vidzy.VidzyAvailability
import dev.codexsoft.codexcine.data.vidzy.VidzyRepository
import dev.codexsoft.codexcine.domain.model.MediaDetails
import dev.codexsoft.codexcine.domain.model.MediaItem
import dev.codexsoft.codexcine.domain.model.MediaType
import dev.codexsoft.codexcine.domain.model.SagaRow
import dev.codexsoft.codexcine.domain.model.SagaRules
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface DetailsUiState {
    data object Loading : DetailsUiState
    data class Content(val details: MediaDetails, val isInMyList: Boolean) : DetailsUiState
    data class Error(val kind: ErrorKind) : DetailsUiState
}

/** What the provider said about this title. Unknown also covers a failed request. */
sealed interface PlayAvailability {
    data object Unknown : PlayAvailability
    data class Available(val languages: List<String>) : PlayAvailability
    data object Unavailable : PlayAvailability
}

class DetailsViewModel(
    private val mediaId: Int,
    private val mediaType: MediaType,
    private val catalog: CatalogRepository,
    private val myList: MyListRepository,
    private val vidzy: VidzyRepository,
    private val watchProgress: WatchProgressRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<DetailsUiState>(DetailsUiState.Loading)
    val uiState: StateFlow<DetailsUiState> = _uiState.asStateFlow()

    private val _playAvailability = MutableStateFlow<PlayAvailability>(PlayAvailability.Unknown)
    val playAvailability: StateFlow<PlayAvailability> = _playAvailability.asStateFlow()

    /** The film's saga, once loaded. Null for a show, a film outside any saga, or a failed request. */
    private val _saga = MutableStateFlow<SagaRow?>(null)
    val saga: StateFlow<SagaRow?> = _saga.asStateFlow()

    init {
        viewModelScope.launch {
            _playAvailability.value = when (val result = vidzy.availability(mediaId)) {
                is AppResult.Error -> PlayAvailability.Unknown
                is AppResult.Success -> result.data.toPlayAvailability()
            }
        }
        viewModelScope.launch {
            when (val result = catalog.details(mediaId, mediaType)) {
                is AppResult.Error -> _uiState.value = DetailsUiState.Error(result.kind)
                is AppResult.Success -> {
                    result.data.collectionId?.let(::loadSaga)
                    myList.contains(mediaId, mediaType).collect { isInList ->
                        _uiState.value = DetailsUiState.Content(result.data, isInList)
                    }
                }
            }
        }
    }

    private fun loadSaga(collectionId: Int) {
        viewModelScope.launch {
            val result = catalog.saga(collectionId)
            if (result is AppResult.Success) _saga.value = SagaRules.rowFor(result.data, mediaId)
        }
    }

    /** The (season, episode) a show's Play button starts: the last one watched, or else the first. */
    suspend fun episodeToStart(): Pair<Int, Int> {
        val saved = watchProgress.latestEpisode(mediaId) ?: return 1 to 1
        return saved.season to saved.episode
    }

    private fun VidzyAvailability.toPlayAvailability() =
        if (isAvailable) PlayAvailability.Available(languages) else PlayAvailability.Unavailable

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
