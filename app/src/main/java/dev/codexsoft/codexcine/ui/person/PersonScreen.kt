package dev.codexsoft.codexcine.ui.person

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.codexsoft.codexcine.R
import dev.codexsoft.codexcine.core.Extras
import dev.codexsoft.codexcine.ui.common.appContainer
import dev.codexsoft.codexcine.ui.common.openMediaItem
import dev.codexsoft.codexcine.ui.common.showErrorScreen
import dev.codexsoft.codexcine.ui.compose.ComposeScreenFragment
import dev.codexsoft.codexcine.ui.compose.components.CardGrid
import dev.codexsoft.codexcine.ui.compose.components.CardSizes
import dev.codexsoft.codexcine.ui.compose.components.MediaCard
import dev.codexsoft.codexcine.ui.compose.components.ScreenFrame

/** A person's filmography, in Compose: five columns of posters under the person's name. */
class ComposePersonFragment : ComposeScreenFragment() {

    private val viewModel: PersonViewModel by viewModels {
        viewModelFactory {
            initializer {
                PersonViewModel(
                    personId = requireActivity().intent.getIntExtra(Extras.MEDIA_ID, Extras.NO_VALUE),
                    catalog = appContainer.catalogRepository,
                )
            }
        }
    }

    @Composable
    override fun ScreenContent() {
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        val intentTitle = requireActivity().intent.getStringExtra(Extras.TITLE).orEmpty()
        val content = state as? PersonUiState.Content
        val title = content?.person?.name?.takeIf { it.isNotBlank() } ?: intentTitle
        val emptyMessage = stringResource(R.string.empty_content)

        LaunchedEffect(state) {
            when (val current = state) {
                is PersonUiState.Error -> showErrorScreen(current.kind)
                is PersonUiState.Content ->
                    if (current.person.filmography.isEmpty()) {
                        showErrorScreen(emptyMessage, retryable = false, title = current.person.name)
                    }
                PersonUiState.Loading -> Unit
            }
        }

        ScreenFrame(title = title, loading = state is PersonUiState.Loading) {
            content?.person?.filmography?.takeIf { it.isNotEmpty() }?.let { films ->
                CardGrid(films, columns = 5, cardWidth = CardSizes.POSTER_W, key = { it.id }) { film ->
                    MediaCard(film, onClick = { openMediaItem(film) })
                }
            }
        }
    }
}
