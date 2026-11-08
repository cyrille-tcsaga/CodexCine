package dev.codexsoft.codexcine.ui.grid

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.viewModels
import androidx.leanback.widget.DiffCallback
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.codexsoft.codexcine.R
import dev.codexsoft.codexcine.core.AppResult
import dev.codexsoft.codexcine.core.Extras
import dev.codexsoft.codexcine.domain.model.CategoryType
import dev.codexsoft.codexcine.domain.model.DiscoverQuery
import dev.codexsoft.codexcine.domain.model.Genre
import dev.codexsoft.codexcine.domain.model.GridFilters
import dev.codexsoft.codexcine.domain.model.GridSort
import dev.codexsoft.codexcine.domain.model.MediaItem
import dev.codexsoft.codexcine.ui.browse.CardPresenter
import dev.codexsoft.codexcine.ui.common.BaseGridFragment
import dev.codexsoft.codexcine.ui.common.appContainer
import dev.codexsoft.codexcine.ui.common.collectWhileStarted
import dev.codexsoft.codexcine.ui.common.openMediaItem
import dev.codexsoft.codexcine.ui.common.showErrorScreen
import kotlinx.coroutines.launch

/**
 * Every title of one home category, paged in as the viewer scrolls down. Above the grid, a "Filtres"
 * button narrows it by genre, decade and rating, or sorts it otherwise.
 */
class MediaGridFragment : BaseGridFragment(columns = 5, cardPresenter = CardPresenter()) {

    private val category: CategoryType
        get() = CategoryType.valueOf(requireActivity().intent.getStringExtra(Extras.CATEGORY).orEmpty())

    private val viewModel: GridViewModel by viewModels {
        viewModelFactory {
            initializer {
                val catalog = appContainer.catalogRepository
                val category = category
                GridViewModel { filters, page -> catalog.filteredPage(category, filters, page) }
            }
        }
    }

    private var genres: List<Genre>? = null
    private var menu: AlertDialog? = null

    override fun onInflateTitleView(inflater: LayoutInflater, parent: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.view_grid_title, parent, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        title = requireActivity().intent.getStringExtra(Extras.TITLE)
        // Trending mixes movies and shows: TMDB cannot filter it.
        if (DiscoverQuery.mediaTypeOf(category) != null) setOnSearchClickedListener { showFiltersMenu() }

        collectWhileStarted(viewModel.filters) { filters ->
            (titleView as? FilterTitleView)?.showFilters(filters.activeCount, describe(filters))
        }
        collectWhileStarted(viewModel.uiState) { state ->
            setLoading(state is GridUiState.Loading)
            when (state) {
                GridUiState.Loading -> replaceItems(emptyList(), MediaItemDiff)
                is GridUiState.Content -> when {
                    state.items.isNotEmpty() -> replaceItems(state.items, MediaItemDiff)
                    viewModel.filters.value.isActive -> {
                        // Filters too narrow: say so, and keep the button within reach to change them.
                        replaceItems(emptyList(), MediaItemDiff)
                        Toast.makeText(requireContext(), R.string.grid_filters_no_result, Toast.LENGTH_LONG).show()
                        titleView?.findViewById<View>(R.id.grid_filters)?.requestFocus()
                    }
                    else -> showErrorScreen(getString(R.string.empty_content), retryable = false)
                }
                is GridUiState.Error -> showErrorScreen(state.kind)
            }
        }
    }

    override fun onDestroyView() {
        menu?.dismiss()
        menu = null
        super.onDestroyView()
    }

    override fun onCardClicked(item: Any) {
        if (item is MediaItem) openMediaItem(item)
    }

    override fun onCardSelected(position: Int) {
        viewModel.onItemSelected(position)
    }

    private fun showFiltersMenu() {
        val filters = viewModel.filters.value
        val entries = buildList<Pair<String, () -> Unit>> {
            add(entry(R.string.filter_genre, genreName(filters.genreId)) { pickGenre() })
            if (DiscoverQuery.allowsDecade(category)) {
                add(entry(R.string.filter_decade, filters.decade?.let(::decadeLabel)) { pickDecade() })
            }
            add(entry(R.string.filter_rating, filters.minRating?.let(::ratingLabel)) { pickRating() })
            add(entry(R.string.filter_sort, filters.sort?.let(::sortLabel)) { pickSort() })
            if (filters.isActive) add(getString(R.string.filter_reset) to { viewModel.setFilters(GridFilters()) })
        }
        showList(getString(R.string.grid_filters), entries)
    }

    private fun entry(labelRes: Int, value: String?, action: () -> Unit): Pair<String, () -> Unit> =
        getString(R.string.filter_entry, getString(labelRes), value ?: getString(R.string.filter_any)) to action

    private fun pickGenre() {
        val known = genres
        if (known != null) return showGenres(known)
        val type = DiscoverQuery.mediaTypeOf(category) ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            when (val result = appContainer.catalogRepository.genres(type)) {
                is AppResult.Success -> {
                    genres = result.data
                    showGenres(result.data)
                }
                is AppResult.Error ->
                    Toast.makeText(requireContext(), R.string.grid_filters_genres_error, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showGenres(all: List<Genre>) {
        // A genre category (Action, say) already has its genre.
        val choices = all.filterNot { it.id == category.genreId }
        val options = listOf<Genre?>(null) + choices
        pick(R.string.filter_genre, options, { it?.name ?: getString(R.string.filter_any) }, viewModel.filters.value.genreId?.let { id -> options.indexOfFirst { it?.id == id } }) {
            viewModel.setFilters(viewModel.filters.value.copy(genreId = it?.id))
        }
    }

    private fun pickDecade() {
        val options = listOf<Int?>(null) + DiscoverQuery.DECADES
        pick(R.string.filter_decade, options, { it?.let(::decadeLabel) ?: getString(R.string.filter_any) }, options.indexOf(viewModel.filters.value.decade)) {
            viewModel.setFilters(viewModel.filters.value.copy(decade = it))
        }
    }

    private fun pickRating() {
        val options = listOf<Int?>(null) + DiscoverQuery.MIN_RATINGS
        pick(R.string.filter_rating, options, { it?.let(::ratingLabel) ?: getString(R.string.filter_any) }, options.indexOf(viewModel.filters.value.minRating)) {
            viewModel.setFilters(viewModel.filters.value.copy(minRating = it))
        }
    }

    private fun pickSort() {
        val options = listOf<GridSort?>(null) + GridSort.entries
        pick(R.string.filter_sort, options, { it?.let(::sortLabel) ?: getString(R.string.filter_sort_default) }, options.indexOf(viewModel.filters.value.sort)) {
            viewModel.setFilters(viewModel.filters.value.copy(sort = it))
        }
    }

    /** A single-choice list with the current value checked; choosing applies at once and closes it. */
    private fun <T> pick(titleRes: Int, options: List<T>, label: (T) -> String, checked: Int?, onPicked: (T) -> Unit) {
        if (view == null) return
        menu?.dismiss()
        menu = AlertDialog.Builder(requireContext(), R.style.CodexCine_Dialog)
            .setTitle(titleRes)
            .setSingleChoiceItems(options.map(label).toTypedArray(), checked?.coerceAtLeast(0) ?: 0) { dialog, which ->
                dialog.dismiss()
                onPicked(options[which])
            }
            .show()
    }

    private fun showList(title: String, entries: List<Pair<String, () -> Unit>>) {
        if (view == null) return
        menu?.dismiss()
        menu = AlertDialog.Builder(requireContext(), R.style.CodexCine_Dialog)
            .setTitle(title)
            .setItems(entries.map { it.first }.toTypedArray()) { _, which -> entries[which].second() }
            .show()
    }

    /** What is filtered, for the line next to the button: "Action · Années 1990 · ★ 7 et plus". */
    private fun describe(filters: GridFilters): String = listOfNotNull(
        genreName(filters.genreId),
        filters.decade?.let(::decadeLabel),
        filters.minRating?.let(::ratingLabel),
        filters.sort?.let(::sortLabel),
    ).joinToString(getString(R.string.separator))

    private fun genreName(id: Int?): String? = id?.let { genreId -> genres?.firstOrNull { it.id == genreId }?.name }

    private fun decadeLabel(decade: Int) = getString(R.string.filter_decade_value, decade)

    private fun ratingLabel(rating: Int) = getString(R.string.filter_rating_value, rating)

    private fun sortLabel(sort: GridSort) = getString(
        when (sort) {
            GridSort.POPULARITY -> R.string.filter_sort_popularity
            GridSort.RATING -> R.string.filter_sort_rating
            GridSort.NEWEST -> R.string.filter_sort_newest
        },
    )

    private object MediaItemDiff : DiffCallback<MediaItem>() {
        override fun areItemsTheSame(oldItem: MediaItem, newItem: MediaItem) =
            oldItem.id == newItem.id && oldItem.mediaType == newItem.mediaType

        override fun areContentsTheSame(oldItem: MediaItem, newItem: MediaItem) = oldItem == newItem
    }
}
