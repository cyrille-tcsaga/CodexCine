package dev.codexsoft.codexcine.ui.season

import android.app.AlertDialog
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.fragment.app.viewModels
import androidx.leanback.widget.DiffCallback
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.codexsoft.codexcine.R
import dev.codexsoft.codexcine.core.Extras
import dev.codexsoft.codexcine.domain.model.MediaType
import dev.codexsoft.codexcine.ui.common.BaseGridFragment
import dev.codexsoft.codexcine.ui.common.appContainer
import dev.codexsoft.codexcine.ui.common.collectWhileStarted
import dev.codexsoft.codexcine.ui.common.showErrorScreen
import dev.codexsoft.codexcine.ui.playback.PlaybackActivity

/** A season's episodes. A long press on OK marks them watched or not. */
class SeasonFragment : BaseGridFragment(columns = 4, cardPresenter = EpisodeCardPresenter()) {

    private val showId get() = requireActivity().intent.getIntExtra(Extras.MEDIA_ID, Extras.NO_VALUE)
    private val seasonNumber get() = requireActivity().intent.getIntExtra(Extras.SEASON_NUMBER, Extras.NO_VALUE)
    private val showTitle get() = requireActivity().intent.getStringExtra(Extras.TITLE).orEmpty()

    private val viewModel: SeasonViewModel by viewModels {
        viewModelFactory {
            initializer {
                val container = appContainer
                SeasonViewModel(
                    showId = showId,
                    seasonNumber = seasonNumber,
                    catalog = container.catalogRepository,
                    watched = container.watchedEpisodesRepository,
                    progress = container.watchProgressRepository,
                    persistenceScope = container.persistenceScope,
                )
            }
        }
    }

    private var menu: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        (cardPresenter as EpisodeCardPresenter).onLongPress = ::showMenu
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        title = getString(R.string.season_title, seasonNumber, showTitle)
        addHint(view)

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
                        replaceItems(state.episodes, EpisodeItemDiff)
                    }
                is SeasonUiState.Error -> showErrorScreen(state.kind)
            }
        }
    }

    override fun onDestroyView() {
        menu?.dismiss()
        menu = null
        super.onDestroyView()
    }

    override fun onDestroy() {
        (cardPresenter as EpisodeCardPresenter).onLongPress = null
        super.onDestroy()
    }

    override fun onCardClicked(item: Any) {
        val episode = (item as? EpisodeItem)?.episode ?: return
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

    /** The long press is not discoverable on its own: say so on screen. */
    private fun addHint(root: View) {
        val frame = root as? FrameLayout ?: return
        val hint = TextView(frame.context).apply {
            setText(R.string.season_hint_long_press)
            setTextAppearance(R.style.CodexCine_TextAppearance_Hint)
        }
        // Under the screen title, in the free band above the grid.
        frame.addView(
            hint,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.END,
            ).apply {
                topMargin = resources.getDimensionPixelSize(R.dimen.season_hint_top)
                marginEnd = resources.getDimensionPixelSize(R.dimen.hero_padding_start)
            },
        )
    }

    private val EpisodeAction.labelRes: Int
        get() = when (this) {
            EpisodeAction.MARK_WATCHED -> R.string.episode_mark_watched
            EpisodeAction.MARK_UNWATCHED -> R.string.episode_mark_unwatched
            EpisodeAction.MARK_WATCHED_UP_TO_HERE -> R.string.episode_mark_up_to_here
            EpisodeAction.MARK_SEASON_WATCHED -> R.string.season_mark_watched
            EpisodeAction.MARK_SEASON_UNWATCHED -> R.string.season_mark_unwatched
        }

    private object EpisodeItemDiff : DiffCallback<EpisodeItem>() {
        override fun areItemsTheSame(oldItem: EpisodeItem, newItem: EpisodeItem) = oldItem.number == newItem.number

        override fun areContentsTheSame(oldItem: EpisodeItem, newItem: EpisodeItem) = oldItem == newItem
    }
}
