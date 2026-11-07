package dev.codexsoft.codexcine.ui.browse

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import androidx.leanback.app.BackgroundManager
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.ClassPresenterSelector
import androidx.leanback.widget.DiffCallback
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.OnItemViewSelectedListener
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.codexsoft.codexcine.R
import dev.codexsoft.codexcine.data.progress.WatchProgress
import dev.codexsoft.codexcine.domain.model.CategoryType
import dev.codexsoft.codexcine.domain.model.HomeRow
import dev.codexsoft.codexcine.domain.model.MediaItem
import dev.codexsoft.codexcine.domain.model.MediaType
import dev.codexsoft.codexcine.ui.common.appContainer
import dev.codexsoft.codexcine.ui.common.collectWhileStarted
import dev.codexsoft.codexcine.ui.common.loadBackgroundBitmap
import dev.codexsoft.codexcine.ui.common.openMediaItem
import dev.codexsoft.codexcine.ui.common.showErrorScreen
import dev.codexsoft.codexcine.ui.common.titleRes
import dev.codexsoft.codexcine.ui.error.AboutActivity
import dev.codexsoft.codexcine.ui.grid.GridActivity
import dev.codexsoft.codexcine.ui.playback.PlaybackActivity
import dev.codexsoft.codexcine.ui.search.SearchActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainFragment : BrowseSupportFragment() {

    private val viewModel: MainViewModel by viewModels {
        viewModelFactory {
            initializer {
                val container = appContainer
                MainViewModel(container.catalogRepository, container.myListRepository, container.watchProgressRepository)
            }
        }
    }

    private val rowsAdapter = ArrayObjectAdapter(ListRowPresenter())
    private val cardSelector = ClassPresenterSelector()
        .addClassPresenter(MediaItem::class.java, CardPresenter())
        .addClassPresenter(WatchProgress::class.java, ContinueWatchingCardPresenter())
        .addClassPresenter(ActionCard::class.java, MoreCardPresenter())

    private lateinit var backgroundManager: BackgroundManager
    private var backgroundJob: Job? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        backgroundManager = BackgroundManager.getInstance(requireActivity())
        if (!backgroundManager.isAttached) {
            backgroundManager.attach(requireActivity().window)
        }

        title = getString(R.string.app_name)
        headersState = HEADERS_ENABLED
        isHeadersTransitionOnBackEnabled = true
        brandColor = ContextCompat.getColor(requireContext(), R.color.fastlane_background)
        searchAffordanceColor = ContextCompat.getColor(requireContext(), R.color.search_opaque)
        adapter = rowsAdapter

        setOnSearchClickedListener {
            startActivity(Intent(requireContext(), SearchActivity::class.java))
        }
        onItemViewClickedListener = OnItemViewClickedListener { card, item, _, _ ->
            when (item) {
                is MediaItem -> openMediaItem(item, card)
                is WatchProgress -> resume(item)
                is ActionCard.SeeAll -> startActivity(
                    GridActivity.newIntent(requireContext(), item.category, item.categoryTitle),
                )
                ActionCard.About -> startActivity(Intent(requireContext(), AboutActivity::class.java))
            }
        }
        onItemViewSelectedListener = OnItemViewSelectedListener { _, item, _, _ ->
            updateBackground(
                when (item) {
                    is MediaItem -> item.backdropUrl
                    is WatchProgress -> item.backdropUrl
                    else -> null
                },
            )
        }

        collectWhileStarted(viewModel.uiState, ::render)
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshLocalRows()
    }

    override fun onDestroyView() {
        backgroundJob?.cancel()
        backgroundJob = null
        super.onDestroyView()
    }

    private fun render(state: MainUiState) {
        when (state) {
            MainUiState.Loading -> progressBarManager.show()
            is MainUiState.Content -> {
                progressBarManager.hide()
                renderRows(state.rows)
            }
            is MainUiState.Error -> {
                progressBarManager.hide()
                showErrorScreen(state.kind)
            }
        }
    }

    /**
     * Updates the adapter in place. Rows arrive one by one while the user may already be browsing,
     * so rebuilding it would keep throwing the focus away.
     */
    private fun renderRows(rows: List<HomeRow>) {
        val wanted = rows.map { it.category.headerId }.toSet()
        for (index in rowsAdapter.size() - 1 downTo 0) {
            if (rowAt(index).headerItem.id !in wanted) rowsAdapter.removeItems(index, 1)
        }

        rows.forEachIndexed { index, row ->
            val isAlreadyShown = index < rowsAdapter.size() && rowAt(index).headerItem.id == row.category.headerId
            if (!isAlreadyShown) {
                rowsAdapter.add(index, createRow(row))
            } else if (row.category == CategoryType.MY_LIST) {
                (rowAt(index).adapter as ArrayObjectAdapter).setItems(row.items, MediaItemDiff)
            } else if (row.category == CategoryType.CONTINUE_WATCHING) {
                (rowAt(index).adapter as ArrayObjectAdapter).setItems(row.continueWatching, WatchProgressDiff)
            }
        }
    }

    private fun rowAt(index: Int) = rowsAdapter.get(index) as ListRow

    private fun createRow(row: HomeRow): ListRow {
        val title = getString(row.category.titleRes)
        val cards = ArrayObjectAdapter(cardSelector)
        cards.addAll(0, row.items)
        cards.addAll(0, row.continueWatching)
        when {
            row.category == CategoryType.ABOUT -> cards.add(ActionCard.About)
            row.category.isRemote -> cards.add(ActionCard.SeeAll(row.category, title))
        }
        return ListRow(HeaderItem(row.category.headerId, title), cards)
    }

    private fun resume(progress: WatchProgress) {
        val type = progress.type ?: return
        startActivity(
            PlaybackActivity.newIntent(
                requireContext(),
                mediaId = progress.tmdbId,
                mediaType = type,
                title = progress.title,
                seasonNumber = progress.season.takeIf { type == MediaType.TV },
                episodeNumber = progress.episode.takeIf { type == MediaType.TV },
                autoResume = true,
            ),
        )
    }

    /** Waits for the selection to settle, so scrolling through a row does not load every backdrop. */
    private fun updateBackground(url: String?) {
        backgroundJob?.cancel()
        if (url == null) return
        backgroundJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(BACKGROUND_DEBOUNCE_MS)
            val metrics = resources.displayMetrics
            val bitmap = loadBackgroundBitmap(
                requireContext(),
                url,
                metrics.widthPixels,
                metrics.heightPixels,
            )
            if (bitmap != null) backgroundManager.setBitmap(bitmap)
        }
    }

    private val CategoryType.headerId: Long get() = ordinal.toLong()

    private object MediaItemDiff : DiffCallback<MediaItem>() {
        override fun areItemsTheSame(oldItem: MediaItem, newItem: MediaItem) =
            oldItem.id == newItem.id && oldItem.mediaType == newItem.mediaType

        override fun areContentsTheSame(oldItem: MediaItem, newItem: MediaItem) = oldItem == newItem
    }

    private object WatchProgressDiff : DiffCallback<WatchProgress>() {
        override fun areItemsTheSame(oldItem: WatchProgress, newItem: WatchProgress) = oldItem.key == newItem.key

        override fun areContentsTheSame(oldItem: WatchProgress, newItem: WatchProgress) = oldItem == newItem
    }

    private companion object {
        const val BACKGROUND_DEBOUNCE_MS = 300L
    }
}
