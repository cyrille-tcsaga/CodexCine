package dev.codexsoft.codexcine.ui.browse

import dev.codexsoft.codexcine.ui.common.cardRowPresenter
import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.leanback.app.BackgroundManager
import androidx.leanback.app.RowsSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.ClassPresenterSelector
import androidx.leanback.widget.DiffCallback
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.OnItemViewSelectedListener
import androidx.lifecycle.lifecycleScope
import dev.codexsoft.codexcine.R
import dev.codexsoft.codexcine.data.progress.WatchProgress
import dev.codexsoft.codexcine.domain.model.CategoryType
import dev.codexsoft.codexcine.domain.model.HomeRow
import dev.codexsoft.codexcine.domain.model.MediaItem
import dev.codexsoft.codexcine.domain.model.MediaType
import dev.codexsoft.codexcine.domain.model.NewEpisode
import dev.codexsoft.codexcine.ui.common.appContainer
import dev.codexsoft.codexcine.ui.common.collectWhileStarted
import dev.codexsoft.codexcine.ui.common.loadBackgroundBitmap
import dev.codexsoft.codexcine.ui.common.openMediaItem
import dev.codexsoft.codexcine.ui.common.titleRes
import dev.codexsoft.codexcine.ui.details.DetailsActivity
import dev.codexsoft.codexcine.ui.playback.PlaybackActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** One sidebar page: its rows from the shared catalogue, and on the home page the billboard on top. */
class HomePageFragment : RowsSupportFragment() {

    private val page: HomePage
        get() = HomePage.entries[requireArguments().getInt(ARG_PAGE)]

    private val viewModel: MainViewModel by viewModels(
        ownerProducer = { requireParentFragment() },
        factoryProducer = { mainViewModelFactory() },
    )

    private val rowsAdapter = ArrayObjectAdapter(
        ClassPresenterSelector()
            .addClassPresenter(ListRow::class.java, cardRowPresenter())
            .addClassPresenter(BillboardRow::class.java, BillboardRowPresenter())
            .addClassPresenter(MessageRow::class.java, MessageRowPresenter()),
    )
    private val continueWatchingPresenter = ContinueWatchingCardPresenter()
    private val cardSelector = ClassPresenterSelector()
        .addClassPresenter(MediaItem::class.java, CardPresenter())
        .addClassPresenter(WatchProgress::class.java, continueWatchingPresenter)
        .addClassPresenter(NewEpisode::class.java, NewEpisodeCardPresenter())
        .addClassPresenter(ActionCard::class.java, MoreCardPresenter())

    private var billboard: BillboardRow? = null
    private var featured: MediaItem? = null
    private var isBillboardSelected = true
    private var isDataReadyNotified = false
    private var backgroundJob: Job? = null
    private val continueWatchingMenu = ContinueWatchingMenu(this) { viewModel.removeFromContinueWatching(it) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        adapter = rowsAdapter
        continueWatchingPresenter.onLongPress = continueWatchingMenu::show
        onItemViewClickedListener = OnItemViewClickedListener { _, item, _, _ ->
            when (item) {
                is MediaItem -> openMediaItem(item)
                is WatchProgress -> playProgress(item, autoResume = true)
                is NewEpisode -> openNewEpisode(item)
                is ActionCard.SeeAll -> openSeeAll(item)
                ActionCard.About -> openAbout()
            }
        }
        onItemViewSelectedListener = OnItemViewSelectedListener { _, item, _, row ->
            isBillboardSelected = row is BillboardRow
            when {
                row is BillboardRow -> updateBackground(featured?.backdropUrl)
                item is MediaItem -> updateBackground(item.backdropUrl)
                item is WatchProgress -> updateBackground(item.backdropUrl)
                item is NewEpisode -> updateBackground(item.show.backdropUrl)
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        collectWhileStarted(viewModel.uiState) { state ->
            if (state is MainUiState.Content) render(state.rows)
        }
    }

    override fun onDestroyView() {
        backgroundJob?.cancel()
        backgroundJob = null
        continueWatchingMenu.dismiss()
        super.onDestroyView()
    }

    override fun onDestroy() {
        continueWatchingPresenter.onLongPress = null
        super.onDestroy()
    }

    private fun render(allRows: List<HomeRow>) {
        val byCategory = allRows.associateBy { it.category }
        val rows = page.categories.mapNotNull { byCategory[it] }
        // Built with the first rows, so that it is in place first and the page opens on it: inserted
        // later, above rows already shown, it would leave the selection on the row below it.
        if (page.hasBillboard && billboard == null) {
            addBillboard(
                byCategory[CategoryType.TRENDING]
                    ?: byCategory[CategoryType.POPULAR_MOVIES]
                    ?: allRows.firstOrNull { it.category.isRemote },
            )
        }
        syncRows(rows)
        if (!isDataReadyNotified) {
            isDataReadyNotified = true
            mainFragmentAdapter.fragmentHost?.notifyDataReady(mainFragmentAdapter)
        }
    }

    /** Titles that have both artwork and a summary make the best billboards. */
    private fun addBillboard(source: HomeRow?) {
        val items = source?.items.orEmpty()
            .filter { it.backdropUrl != null && !it.overview.isNullOrBlank() }
            .take(BILLBOARD_SIZE)
        if (items.isEmpty()) return
        val row = BillboardRow(items, billboardListener)
        billboard = row
        rowsAdapter.add(0, row)
    }

    /**
     * Updates the adapter in place. Rows arrive one by one while the viewer may already be browsing,
     * so rebuilding it would keep throwing the focus away.
     */
    private fun syncRows(rows: List<HomeRow>) {
        val offset = if (billboard != null) 1 else 0
        val wanted = rows.map { it.category.headerId }.toSet()
        for (index in rowsAdapter.size() - 1 downTo offset) {
            val row = rowsAdapter.get(index)
            if (row is MessageRow || (row is ListRow && row.headerItem.id !in wanted)) {
                rowsAdapter.removeItems(index, 1)
            }
        }

        rows.forEachIndexed { index, row ->
            val position = index + offset
            val shown = (rowsAdapter.takeIf { position < it.size() }?.get(position) as? ListRow)
                ?.takeIf { it.headerItem.id == row.category.headerId }
            when {
                shown == null -> rowsAdapter.add(position, createRow(row))
                row.category == CategoryType.MY_LIST ->
                    (shown.adapter as ArrayObjectAdapter).setItems(row.items, MediaItemDiff)
                row.category == CategoryType.CONTINUE_WATCHING ->
                    (shown.adapter as ArrayObjectAdapter).setItems(row.continueWatching, WatchProgressDiff)
                row.category == CategoryType.NEW_EPISODES ->
                    (shown.adapter as ArrayObjectAdapter).setItems(row.newEpisodes, NewEpisodeDiff)
            }
        }

        val emptyMessage = page.emptyMessageRes
        if (rows.isEmpty() && emptyMessage != null) rowsAdapter.add(MessageRow(getString(emptyMessage)))
    }

    private fun createRow(row: HomeRow): ListRow {
        val title = getString(row.category.titleRes)
        val cards = ArrayObjectAdapter(cardSelector)
        cards.addAll(0, row.items)
        cards.addAll(0, row.continueWatching)
        cards.addAll(0, row.newEpisodes)
        when {
            row.category == CategoryType.ABOUT -> cards.add(ActionCard.About)
            row.category.isRemote -> cards.add(ActionCard.SeeAll(row.category, title))
        }
        return ListRow(HeaderItem(row.category.headerId, title), cards)
    }

    private val billboardListener = object : BillboardRow.Listener {
        override fun onPlay(item: MediaItem) = playFeatured(item)

        override fun onMoreInfo(item: MediaItem) = openDetails(item)

        override fun onFeatured(item: MediaItem) {
            featured = item
            if (isBillboardSelected) updateBackground(item.backdropUrl)
        }
    }

    /** Waits for the selection to settle, so scrolling through a row does not load every backdrop. */
    private fun updateBackground(url: String?) {
        backgroundJob?.cancel()
        if (url == null || view == null) return
        backgroundJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(BACKGROUND_DEBOUNCE_MS)
            val metrics = resources.displayMetrics
            val bitmap = loadBackgroundBitmap(requireContext(), url, metrics.widthPixels, metrics.heightPixels)
            if (bitmap != null) BackgroundManager.getInstance(requireActivity()).setBitmap(bitmap)
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

    private object NewEpisodeDiff : DiffCallback<NewEpisode>() {
        override fun areItemsTheSame(oldItem: NewEpisode, newItem: NewEpisode) = oldItem.show.id == newItem.show.id

        override fun areContentsTheSame(oldItem: NewEpisode, newItem: NewEpisode) = oldItem == newItem
    }

    companion object {
        private const val ARG_PAGE = "page"
        private const val BILLBOARD_SIZE = 6
        private const val BACKGROUND_DEBOUNCE_MS = 300L

        fun newInstance(page: HomePage) = HomePageFragment().apply {
            arguments = Bundle().apply { putInt(ARG_PAGE, page.ordinal) }
        }
    }
}
