package dev.codexsoft.codexcine.ui.playback

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.codexsoft.codexcine.data.playback.PlaybackRequest
import dev.codexsoft.codexcine.data.playback.StreamSource
import dev.codexsoft.codexcine.data.playback.StreamSourceResolver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface PlaybackUiState {
    data object Loading : PlaybackUiState
    data class Ready(val source: StreamSource) : PlaybackUiState

    /** The resolver has nothing to play for this title. */
    data object NoSource : PlaybackUiState

    /** Resolving the source or playing it failed. */
    data object Failed : PlaybackUiState
}

class PlaybackViewModel(
    request: PlaybackRequest,
    resolver: StreamSourceResolver,
) : ViewModel() {

    private val _uiState = MutableStateFlow<PlaybackUiState>(PlaybackUiState.Loading)
    val uiState: StateFlow<PlaybackUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _uiState.value = try {
                resolver.resolve(request)?.let { PlaybackUiState.Ready(it) } ?: PlaybackUiState.NoSource
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Could not resolve a source for ${request.title}", e)
                PlaybackUiState.Failed
            }
        }
    }

    fun onPlayerError() {
        _uiState.value = PlaybackUiState.Failed
    }

    private companion object {
        const val TAG = "PlaybackViewModel"
    }
}
