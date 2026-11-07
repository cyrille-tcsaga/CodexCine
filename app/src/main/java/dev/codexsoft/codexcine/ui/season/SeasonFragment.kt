package dev.codexsoft.codexcine.ui.season

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.codexsoft.codexcine.R
import dev.codexsoft.codexcine.core.Extras
import dev.codexsoft.codexcine.domain.model.Episode
import dev.codexsoft.codexcine.domain.model.MediaType
import dev.codexsoft.codexcine.ui.common.BaseGridFragment
import dev.codexsoft.codexcine.ui.common.appContainer
import dev.codexsoft.codexcine.ui.common.collectWhileStarted
import dev.codexsoft.codexcine.ui.common.showErrorScreen
import dev.codexsoft.codexcine.ui.playback.PlaybackActivity

class SeasonFragment : BaseGridFragment(columns = 4, cardPresenter = EpisodeCardPresenter()) {

    private val showId get() = requireActivity().intent.getIntExtra(Extras.MEDIA_ID, Extras.NO_VALUE)
    private val seasonNumber get() = requireActivity().intent.getIntExtra(Extras.SEASON_NUMBER, Extras.NO_VALUE)
    private val showTitle get() = requireActivity().intent.getStringExtra(Extras.TITLE).orEmpty()

    private val viewModel: SeasonViewModel by viewModels {
        viewModelFactory {
            initializer {
                SeasonViewModel(showId, seasonNumber, appContainer.catalogRepository)
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        title = getString(R.string.season_title, seasonNumber, showTitle)

        collectWhileStarted(viewModel.uiState) { state ->
            setLoading(state is SeasonUiState.Loading)
            when (state) {
                SeasonUiState.Loading -> Unit
                is SeasonUiState.Content ->
                    if (state.episodes.isEmpty()) {
                        showErrorScreen(
                            getString(R.string.empty_content),
                            retryable = false,
                            title = getString(R.string.season_title, seasonNumber, showTitle),
                        )
                    } else {
                        showItems(state.episodes)
                    }
                is SeasonUiState.Error -> showErrorScreen(state.kind)
            }
        }
    }

    override fun onCardClicked(item: Any) {
        val episode = item as? Episode ?: return
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
}
