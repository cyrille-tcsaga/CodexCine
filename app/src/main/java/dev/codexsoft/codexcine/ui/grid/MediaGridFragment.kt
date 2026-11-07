package dev.codexsoft.codexcine.ui.grid

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
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

/** Every title of one home category, paged in as the user scrolls down. */
class MediaGridFragment : BaseGridFragment(columns = 5, cardPresenter = CardPresenter()) {

    private val viewModel: GridViewModel by viewModels {
        viewModelFactory {
            initializer {
                val catalog = appContainer.catalogRepository
                val category = CategoryType.valueOf(
                    requireActivity().intent.getStringExtra(Extras.CATEGORY).orEmpty(),
                )
                GridViewModel { page -> catalog.categoryPage(category, page) }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        title = requireActivity().intent.getStringExtra(Extras.TITLE)

        collectWhileStarted(viewModel.uiState) { state ->
            setLoading(state is GridUiState.Loading)
            when (state) {
                GridUiState.Loading -> Unit
                is GridUiState.Content ->
                    if (state.items.isEmpty()) {
                        showErrorScreen(getString(R.string.empty_content), retryable = false)
                    } else {
                        showItems(state.items)
                    }
                is GridUiState.Error -> showErrorScreen(state.kind)
            }
        }
    }

    override fun onCardClicked(item: Any) {
        if (item is MediaItem) openMediaItem(item)
    }

    override fun onCardSelected(position: Int) {
        viewModel.onItemSelected(position)
    }
}
