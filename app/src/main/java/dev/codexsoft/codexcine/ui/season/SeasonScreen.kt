package dev.codexsoft.codexcine.ui.season

import android.app.AlertDialog
import dev.codexsoft.codexcine.ui.common.appContainer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.ViewModelProvider
import androidx.fragment.app.Fragment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.codexsoft.codexcine.R
import dev.codexsoft.codexcine.core.Extras
import dev.codexsoft.codexcine.domain.model.MediaType
import dev.codexsoft.codexcine.ui.common.showErrorScreen
import dev.codexsoft.codexcine.ui.compose.ComposeScreenFragment
import dev.codexsoft.codexcine.ui.compose.components.CardGrid
import dev.codexsoft.codexcine.ui.compose.components.CardSizes
import dev.codexsoft.codexcine.ui.compose.components.EpisodeCard
import dev.codexsoft.codexcine.ui.compose.components.ScreenFrame
import dev.codexsoft.codexcine.ui.playback.PlaybackActivity

/**
 * A season's episodes, in Compose: three cards per row, watched ones dimmed and checked. A long press on OK
 * opens the same menu as the Leanback screen (`AlertDialog` in the app's dialog style) to mark episodes.
 */
class ComposeSeasonFragment : ComposeScreenFragment() {

    private val viewModel: SeasonViewModel by viewModels { seasonViewModelFactory() }

    private var menu: AlertDialog? = null

    private val showId get() = requireActivity().intent.getIntExtra(Extras.MEDIA_ID, Extras.NO_VALUE)
    private val seasonNumber get() = requireActivity().intent.getIntExtra(Extras.SEASON_NUMBER, Extras.NO_VALUE)
    private val showTitle get() = requireActivity().intent.getStringExtra(Extras.TITLE).orEmpty()

    @Composable
    override fun ScreenContent() {
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        val title = stringResource(R.string.season_title, seasonNumber, showTitle)
        val emptyMessage = stringResource(R.string.empty_content)

        LaunchedEffect(state) {
            when (val current = state) {
                is SeasonUiState.Error -> showErrorScreen(current.kind)
                is SeasonUiState.Content ->
                    if (current.episodes.isEmpty()) showErrorScreen(emptyMessage, retryable = false, title = title)
                SeasonUiState.Loading -> Unit
            }
        }
        DisposableEffect(Unit) {
            onDispose {
                menu?.dismiss()
                menu = null
            }
        }

        ScreenFrame(
            title = title,
            hint = stringResource(R.string.season_hint_long_press),
            loading = state is SeasonUiState.Loading,
        ) {
            val episodes = (state as? SeasonUiState.Content)?.episodes.orEmpty()
            if (episodes.isNotEmpty()) {
                CardGrid(episodes, columns = 3, cardWidth = CardSizes.STILL_W, key = { "${it.number.season}:${it.number.episode}" }) { item ->
                    EpisodeCard(item, onClick = { play(item) }, onLongClick = { showMenu(item) })
                }
            }
        }
    }

    private fun play(item: EpisodeItem) {
        val episode = item.episode
        startActivity(
            PlaybackActivity.newIntent(
                requireContext(),
                mediaId = showId,
                mediaType = MediaType.TV,
                title = showTitle,
                seasonNumber = episode.seasonNumber,
                episodeNumber = episode.episodeNumber,
            ),
        )
    }

    private fun showMenu(item: EpisodeItem) {
        if (view == null) return
        val actions = viewModel.actionsFor(item)
        if (actions.isEmpty()) return
        menu?.dismiss()
        menu = AlertDialog.Builder(requireContext(), R.style.CodexCine_Dialog)
            .setTitle(getString(R.string.episode_menu_title, item.episode.episodeNumber))
            .setItems(actions.map { getString(it.labelRes) }.toTypedArray()) { _, which ->
                viewModel.apply(actions[which], item)
            }
            .show()
    }
}

/** The season screen's view model, . */
internal fun Fragment.seasonViewModelFactory(): ViewModelProvider.Factory = viewModelFactory {
    initializer {
        val container = appContainer
        val intent = requireActivity().intent
        SeasonViewModel(
            showId = intent.getIntExtra(Extras.MEDIA_ID, Extras.NO_VALUE),
            seasonNumber = intent.getIntExtra(Extras.SEASON_NUMBER, Extras.NO_VALUE),
            catalog = container.catalogRepository,
            watched = container.watchedEpisodesRepository,
            progress = container.watchProgressRepository,
            persistenceScope = container.persistenceScope,
        )
    }
}
