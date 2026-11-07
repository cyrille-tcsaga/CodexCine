package dev.codexsoft.codexcine.ui.season

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.codexsoft.codexcine.core.AppResult
import dev.codexsoft.codexcine.core.ErrorKind
import dev.codexsoft.codexcine.data.progress.EpisodeNumber
import dev.codexsoft.codexcine.data.progress.WatchProgress
import dev.codexsoft.codexcine.data.progress.WatchProgressRepository
import dev.codexsoft.codexcine.data.progress.WatchProgressRules
import dev.codexsoft.codexcine.data.progress.WatchedEpisodesRepository
import dev.codexsoft.codexcine.data.repository.CatalogRepository
import dev.codexsoft.codexcine.domain.model.Episode
import dev.codexsoft.codexcine.domain.model.MediaType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

sealed interface SeasonUiState {
    data object Loading : SeasonUiState
    data class Content(val episodes: List<EpisodeItem>) : SeasonUiState
    data class Error(val kind: ErrorKind) : SeasonUiState
}

/**
 * @param persistenceScope where the watched marks are written, so that leaving the screen right after
 *   choosing does not cancel the write
 */
class SeasonViewModel(
    private val showId: Int,
    private val seasonNumber: Int,
    catalog: CatalogRepository,
    private val watched: WatchedEpisodesRepository,
    private val progress: WatchProgressRepository,
    private val persistenceScope: CoroutineScope,
    private val today: () -> LocalDate = LocalDate::now,
) : ViewModel() {

    private val loaded = MutableStateFlow<AppResult<List<Episode>>?>(null)

    val uiState: StateFlow<SeasonUiState> =
        combine(loaded, watched.watchedIn(showId), progress.items) { result, watchedEpisodes, entries ->
            when (result) {
                null -> SeasonUiState.Loading
                is AppResult.Error -> SeasonUiState.Error(result.kind)
                is AppResult.Success -> SeasonUiState.Content(items(result.data, watchedEpisodes, entries))
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SeasonUiState.Loading)

    init {
        viewModelScope.launch { loaded.value = catalog.season(showId, seasonNumber) }
    }

    fun actionsFor(item: EpisodeItem): List<EpisodeAction> =
        EpisodeMenu.actions(item, currentItems(), today())

    fun apply(action: EpisodeAction, item: EpisodeItem) {
        val targets = EpisodeMenu.episodesFor(action, item, currentItems(), today())
        persistenceScope.launch {
            watched.setWatched(showId, targets, action.isWatched)
            // A watched episode has nothing left to resume.
            if (action.isWatched) targets.forEach { progress.remove(progressKey(it)) }
        }
    }

    private fun currentItems(): List<EpisodeItem> = (uiState.value as? SeasonUiState.Content)?.episodes.orEmpty()

    private fun items(
        episodes: List<Episode>,
        watchedEpisodes: Set<EpisodeNumber>,
        entries: List<WatchProgress>,
    ): List<EpisodeItem> {
        val partWatched = entries
            .filter { it.tmdbId == showId && it.type == MediaType.TV && it.season == seasonNumber }
            .associate { it.episode to it.fraction }
        return episodes.map { episode ->
            val number = EpisodeNumber(episode.seasonNumber, episode.episodeNumber)
            val isWatched = number in watchedEpisodes
            EpisodeItem(
                episode = episode,
                isWatched = isWatched,
                progress = partWatched[episode.episodeNumber]?.takeUnless { isWatched || it <= 0f },
            )
        }
    }

    private fun progressKey(number: EpisodeNumber) =
        WatchProgressRules.key(MediaType.TV.value, showId, number.season, number.episode)

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
