package dev.codexsoft.codexcine.ui.grid

import android.widget.Toast
import dev.codexsoft.codexcine.domain.model.CategoryType
import dev.codexsoft.codexcine.ui.common.appContainer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.ViewModelProvider
import androidx.fragment.app.Fragment
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.codexsoft.codexcine.R
import dev.codexsoft.codexcine.core.Extras
import dev.codexsoft.codexcine.ui.common.openMediaItem
import dev.codexsoft.codexcine.ui.common.showErrorScreen
import dev.codexsoft.codexcine.ui.compose.ComposeScreenFragment
import dev.codexsoft.codexcine.ui.compose.components.CardGrid
import dev.codexsoft.codexcine.ui.compose.components.CardSizes
import dev.codexsoft.codexcine.ui.compose.components.MediaCard
import dev.codexsoft.codexcine.ui.compose.components.PillButton
import dev.codexsoft.codexcine.ui.compose.components.ScreenFrame
import dev.codexsoft.codexcine.ui.compose.theme.CodexTheme

/**
 * Every title of one home category, in Compose: five columns of posters, paged in as the viewer scrolls
 * down. The title bar has the "Filtres" button (when TMDB can filter the category), what is filtered, and
 * the screen title. The menus are the Leanback screen's, through [GridFilterController].
 */
class ComposeGridFragment : ComposeScreenFragment() {

    private val viewModel: GridViewModel by viewModels { gridViewModelFactory() }

    private val filterMenus by lazy {
        GridFilterController(this, gridCategory(), { viewModel.filters.value }, viewModel::setFilters)
    }

    @Composable
    override fun ScreenContent() {
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        val filters by viewModel.filters.collectAsStateWithLifecycle()
        val title = requireActivity().intent.getStringExtra(Extras.TITLE).orEmpty()
        val filtersButton = remember { FocusRequester() }
        val emptyMessage = stringResource(R.string.empty_content)
        val noResult = stringResource(R.string.grid_filters_no_result)

        LaunchedEffect(state) {
            when (val current = state) {
                is GridUiState.Error -> showErrorScreen(current.kind)
                is GridUiState.Content -> if (current.items.isEmpty()) {
                    if (viewModel.filters.value.isActive) {
                        // Filters too narrow: say so, and keep the button within reach to change them.
                        Toast.makeText(requireContext(), noResult, Toast.LENGTH_LONG).show()
                        runCatching { filtersButton.requestFocus() }
                    } else {
                        showErrorScreen(emptyMessage, retryable = false)
                    }
                }
                GridUiState.Loading -> Unit
            }
        }
        DisposableEffect(Unit) { onDispose { filterMenus.dismiss() } }

        ScreenFrame(
            title = title,
            loading = state is GridUiState.Loading,
            contentTop = 96.dp,
            titleBar = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = CodexTheme.dimens.heroPaddingStart, end = CodexTheme.dimens.heroPaddingStart, top = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (filterMenus.isAvailable) {
                        PillButton(
                            text = if (filters.activeCount > 0) {
                                stringResource(R.string.grid_filters_count, filters.activeCount)
                            } else {
                                stringResource(R.string.grid_filters)
                            },
                            onClick = { filterMenus.showMenu() },
                            icon = R.drawable.ic_filter,
                            height = 46.dp,
                            textStyle = MaterialTheme.typography.labelLarge.merge(TextStyle(fontSize = 18.sp)),
                            modifier = Modifier.focusRequester(filtersButton),
                        )
                        val summary = filterMenus.describe(filters)
                        Text(
                            text = summary,
                            style = MaterialTheme.typography.bodyLarge.merge(TextStyle(fontSize = 17.sp)),
                            color = CodexTheme.colors.mist,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).padding(start = 18.dp),
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.displayLarge.merge(TextStyle(fontSize = 45.sp)),
                        color = CodexTheme.colors.ivory,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 18.dp),
                    )
                }
            },
        ) {
            val items = (state as? GridUiState.Content)?.items.orEmpty()
            if (items.isNotEmpty()) {
                CardGrid(
                    items = items,
                    columns = 5,
                    cardWidth = CardSizes.POSTER_W,
                    key = { "${it.mediaType.value}:${it.id}" },
                    onSelected = viewModel::onItemSelected,
                ) { item -> MediaCard(item, onClick = { openMediaItem(item) }) }
            }
        }
    }
}

/** The category this grid shows, from the activity's intent. */
internal fun Fragment.gridCategory(): CategoryType =
    CategoryType.valueOf(requireActivity().intent.getStringExtra(Extras.CATEGORY).orEmpty())

/** The grid's view model, . */
internal fun Fragment.gridViewModelFactory(): ViewModelProvider.Factory = viewModelFactory {
    initializer {
        val catalog = appContainer.catalogRepository
        val category = gridCategory()
        GridViewModel { filters, page -> catalog.filteredPage(category, filters, page) }
    }
}
