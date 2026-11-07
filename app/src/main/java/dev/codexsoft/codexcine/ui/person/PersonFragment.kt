package dev.codexsoft.codexcine.ui.person

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.codexsoft.codexcine.R
import dev.codexsoft.codexcine.core.Extras
import dev.codexsoft.codexcine.domain.model.MediaItem
import dev.codexsoft.codexcine.ui.browse.CardPresenter
import dev.codexsoft.codexcine.ui.common.BaseGridFragment
import dev.codexsoft.codexcine.ui.common.appContainer
import dev.codexsoft.codexcine.ui.common.collectWhileStarted
import dev.codexsoft.codexcine.ui.common.openMediaItem
import dev.codexsoft.codexcine.ui.common.showErrorScreen

/** A person's filmography. */
class PersonFragment : BaseGridFragment(columns = 5, cardPresenter = CardPresenter()) {

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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        title = requireActivity().intent.getStringExtra(Extras.TITLE)

        collectWhileStarted(viewModel.uiState) { state ->
            setLoading(state is PersonUiState.Loading)
            when (state) {
                PersonUiState.Loading -> Unit
                is PersonUiState.Content -> {
                    if (state.person.name.isNotBlank()) title = state.person.name
                    if (state.person.filmography.isEmpty()) {
                        showErrorScreen(
                            getString(R.string.empty_content),
                            retryable = false,
                            title = state.person.name,
                        )
                    } else {
                        showItems(state.person.filmography)
                    }
                }
                is PersonUiState.Error -> showErrorScreen(state.kind)
            }
        }
    }

    override fun onCardClicked(item: Any) {
        if (item is MediaItem) openMediaItem(item)
    }
}
