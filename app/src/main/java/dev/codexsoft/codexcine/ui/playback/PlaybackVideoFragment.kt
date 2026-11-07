package dev.codexsoft.codexcine.ui.playback

import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import androidx.annotation.OptIn
import androidx.fragment.app.viewModels
import androidx.leanback.app.VideoSupportFragment
import androidx.leanback.app.VideoSupportFragmentGlueHost
import androidx.leanback.media.PlaybackTransportControlGlue
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.leanback.LeanbackPlayerAdapter
import dev.codexsoft.codexcine.R
import dev.codexsoft.codexcine.core.Extras
import dev.codexsoft.codexcine.data.playback.PlaybackRequest
import dev.codexsoft.codexcine.data.playback.StreamSource
import dev.codexsoft.codexcine.domain.model.MediaType
import dev.codexsoft.codexcine.ui.common.appContainer
import dev.codexsoft.codexcine.ui.common.collectWhileStarted
import dev.codexsoft.codexcine.ui.common.showErrorScreen

/**
 * Asks the [dev.codexsoft.codexcine.data.playback.StreamSourceResolver] for a source, then either plays
 * it or shows why it cannot. The player only exists between onStart and onStop.
 */
@OptIn(UnstableApi::class)
class PlaybackVideoFragment : VideoSupportFragment() {

    private val viewModel: PlaybackViewModel by viewModels {
        viewModelFactory {
            initializer {
                PlaybackViewModel(playbackRequest(), appContainer.streamSourceResolver)
            }
        }
    }

    private var player: ExoPlayer? = null
    private var glue: PlaybackTransportControlGlue<LeanbackPlayerAdapter>? = null

    private var resumePositionMs = 0L
    private var playWhenReady = true

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            updateKeepScreenOn(player)
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.w(TAG, "Playback failed: ${error.errorCodeName}", error)
            viewModel.onPlayerError()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Replays Ready after each onStart, which is what recreates the player released in onStop.
        collectWhileStarted(viewModel.uiState, ::render)
    }

    override fun onResume() {
        super.onResume()
        if (playWhenReady) player?.play()
    }

    override fun onPause() {
        player?.let {
            playWhenReady = it.playWhenReady
            it.pause()
        }
        super.onPause()
    }

    override fun onStop() {
        releasePlayer()
        super.onStop()
    }

    private fun render(state: PlaybackUiState) {
        when (state) {
            PlaybackUiState.Loading -> progressBarManager.show()
            is PlaybackUiState.Ready -> {
                progressBarManager.hide()
                if (player == null) startPlayer(state.source)
            }
            PlaybackUiState.NoSource -> showFailure(R.string.error_no_source)
            PlaybackUiState.Failed -> showFailure(R.string.error_playback)
        }
    }

    private fun showFailure(messageRes: Int) {
        progressBarManager.hide()
        releasePlayer()
        showErrorScreen(getString(messageRes), retryable = false, title = playbackRequest().title)
    }

    private fun startPlayer(source: StreamSource) {
        val context = requireContext()
        val httpFactory = DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(source.headers)
        val exoPlayer = ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(context, httpFactory)))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .build()
        exoPlayer.addListener(playerListener)

        val request = playbackRequest()
        glue = PlaybackTransportControlGlue(
            context,
            LeanbackPlayerAdapter(context, exoPlayer, PROGRESS_UPDATE_MS),
        ).apply {
            host = VideoSupportFragmentGlueHost(this@PlaybackVideoFragment)
            title = request.title
            subtitle = episodeSubtitle(request)
            isSeekEnabled = true
        }

        exoPlayer.setMediaItem(
            MediaItem.Builder()
                .setUri(source.uri)
                .apply { source.mimeType?.let { setMimeType(it) } }
                .build(),
        )
        exoPlayer.seekTo(resumePositionMs)
        exoPlayer.playWhenReady = playWhenReady
        exoPlayer.prepare()
        player = exoPlayer
    }

    private fun releasePlayer() {
        val exoPlayer = player ?: return
        resumePositionMs = exoPlayer.currentPosition
        glue?.host = null
        glue = null
        exoPlayer.removeListener(playerListener)
        exoPlayer.release()
        player = null
        activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    /** Also covers buffering: the screen saver must not start while the video is catching up. */
    private fun updateKeepScreenOn(player: Player) {
        val window = activity?.window ?: return
        val isActive = player.playWhenReady &&
            (player.playbackState == Player.STATE_READY || player.playbackState == Player.STATE_BUFFERING)
        if (isActive) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun episodeSubtitle(request: PlaybackRequest): String? {
        val season = request.seasonNumber ?: return null
        val episode = request.episodeNumber ?: return null
        return getString(R.string.playback_episode_subtitle, season, episode)
    }

    private fun playbackRequest(): PlaybackRequest {
        val intent = requireActivity().intent
        return PlaybackRequest(
            mediaId = intent.getIntExtra(Extras.MEDIA_ID, Extras.NO_VALUE),
            mediaType = MediaType.fromValue(intent.getStringExtra(Extras.MEDIA_TYPE)) ?: MediaType.MOVIE,
            title = intent.getStringExtra(Extras.TITLE).orEmpty(),
            seasonNumber = intent.getIntExtra(Extras.SEASON_NUMBER, Extras.NO_VALUE).takeIf { it != Extras.NO_VALUE },
            episodeNumber = intent.getIntExtra(Extras.EPISODE_NUMBER, Extras.NO_VALUE).takeIf { it != Extras.NO_VALUE },
        )
    }

    private companion object {
        const val TAG = "PlaybackVideoFragment"
        const val PROGRESS_UPDATE_MS = 250
    }
}
