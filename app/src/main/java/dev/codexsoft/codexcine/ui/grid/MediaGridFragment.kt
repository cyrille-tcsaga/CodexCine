package dev.codexsoft.codexcine.ui.grid

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.leanback.widget.DiffCallback
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.codexsoft.codexcine.R
import dev.codexsoft.codexcine.core.Extras
import dev.codexsoft.codexcine.domain.model.CategoryType
import dev.codexsoft.codexcine.domain.model.MediaItem
import dev.codexsoft.codexcine.ui.browse.CardPresenter
import dev.codexsoft.codexcine.ui.common.BaseGridFragment
import dev.codexsoft.codexcine.ui.common.appContainer
import dev.codexsoft.codexcine.ui.common.collectWhileStarted
import dev.codexsoft.codexcine.ui.common.openMediaItem
import dev.codexsoft.codexcine.ui.common.showErrorScreen

/**
 * Every title of one home category, paged in as the viewer scrolls down. Above the grid, a "Filtres"
 * button narrows it by genre, decade and rating, or sorts it otherwise.
 */
class MediaGridFragment : BaseGridFragment(columns = 5, cardPresenter = CardPresenter()) {

    private val viewModel: GridViewModel by viewModels { gridViewModelFactory() }

    private val filterMenus by lazy {
        GridFilterController(this, gridCategory(), { viewModel.filters.value }, viewModel::setFilters)
    }

    override fun onInflateTitleView(inflater: LayoutInflater, parent: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.view_grid_title, parent, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        title = requireActivity().intent.getStringExtra(Extras.TITLE)
        if (filterMenus.isAvailable) setOnSearchClickedListener { filterMenus.showMenu() }

        collectWhileStarted(viewModel.filters) { filters ->
            (titleView as? FilterTitleView)?.showFilters(filters.activeCount, filterMenus.describe(filters))
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
        filterMenus.dismiss()
        super.onDestroyView()
    }

    override fun onCardClicked(item: Any) {
        if (item is MediaItem) openMediaItem(item)
    }

    override fun onCardSelected(position: Int) {
        viewModel.onItemSelected(position)
    }

    private object MediaItemDiff : DiffCallback<MediaItem>() {
        override fun areItemsTheSame(oldItem: MediaItem, newItem: MediaItem) =
            oldItem.id == newItem.id && oldItem.mediaType == newItem.mediaType

        override fun areContentsTheSame(oldItem: MediaItem, newItem: MediaItem) = oldItem == newItem
    }
}

/** The category this grid shows, from the activity's intent. */
internal fun Fragment.gridCategory(): CategoryType =
    CategoryType.valueOf(requireActivity().intent.getStringExtra(Extras.CATEGORY).orEmpty())

/** The grid's view model, for the Leanback and the Compose fragments alike. */
internal fun Fragment.gridViewModelFactory(): ViewModelProvider.Factory = viewModelFactory {
    initializer {
        val catalog = appContainer.catalogRepository
        val category = gridCategory()
        GridViewModel { filters, page -> catalog.filteredPage(category, filters, page) }
    }
}
