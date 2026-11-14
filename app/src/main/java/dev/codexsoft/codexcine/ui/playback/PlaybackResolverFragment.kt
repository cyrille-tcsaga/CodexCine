package dev.codexsoft.codexcine.ui.playback

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ProgressBar
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.fragment.app.viewModels
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.codexsoft.codexcine.R
import dev.codexsoft.codexcine.data.playback.StreamSource
import dev.codexsoft.codexcine.ui.common.appContainer
import dev.codexsoft.codexcine.ui.common.collectWhileStarted
import dev.codexsoft.codexcine.ui.common.showErrorScreen
import dev.codexsoft.codexcine.ui.playback.web.WebPlayerFragment

/** Asks the resolver for a source, then hands over to the player that matches it. */
class PlaybackResolverFragment : Fragment() {

    private val request get() = requireActivity().intent.toPlaybackRequest()

    private val viewModel: PlaybackViewModel by viewModels {
        viewModelFactory {
            initializer {
                PlaybackViewModel(request, appContainer.streamSourceResolver)
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val context = inflater.context
        return FrameLayout(context).apply {
            addView(
                ProgressBar(context),
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER,
                ),
            )
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        collectWhileStarted(viewModel.uiState, ::render)
    }

    private fun render(state: PlaybackUiState) {
        val title = request.title
        when (state) {
            PlaybackUiState.Loading -> Unit
            is PlaybackUiState.Ready -> openPlayer(state.source)
            PlaybackUiState.NoSource ->
                showErrorScreen(getString(R.string.error_no_source), retryable = false, title = title)
            PlaybackUiState.ServiceUnreachable ->
                showErrorScreen(getString(R.string.error_service_unreachable), retryable = true, title = title)
            PlaybackUiState.Failed ->
                showErrorScreen(getString(R.string.error_playback), retryable = false, title = title)
        }
    }

    private fun openPlayer(source: StreamSource) {
        val player = when (source) {
            is StreamSource.WebEmbed -> WebPlayerFragment.newInstance(source)
        }
        parentFragmentManager.commit {
            replace(android.R.id.content, player)
        }
    }
}
