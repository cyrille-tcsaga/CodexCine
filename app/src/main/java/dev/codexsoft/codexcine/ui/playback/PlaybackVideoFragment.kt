package dev.codexsoft.codexcine.ui.playback

import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.annotation.OptIn
import androidx.leanback.app.VideoSupportFragment
import androidx.leanback.app.VideoSupportFragmentGlueHost
import androidx.leanback.media.PlaybackTransportControlGlue
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
import dev.codexsoft.codexcine.data.playback.PlaybackRequest
import dev.codexsoft.codexcine.data.playback.StreamSource
import dev.codexsoft.codexcine.ui.common.showErrorScreen

/** Plays a [StreamSource.Direct] natively. The player only exists between onStart and onStop. */
@OptIn(UnstableApi::class)
class PlaybackVideoFragment : VideoSupportFragment() {

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
            showFailure(R.string.error_playback)
        }
    }

    override fun onStart() {
        super.onStart()
        if (player == null) startPlayer(sourceFromArguments())
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

    private fun showFailure(messageRes: Int) {
        releasePlayer()
        showErrorScreen(getString(messageRes), retryable = false, title = requireActivity().intent.toPlaybackRequest().title)
    }

    private fun startPlayer(source: StreamSource.Direct) {
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

        val request = requireActivity().intent.toPlaybackRequest()
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

    private fun sourceFromArguments(): StreamSource.Direct {
        val args = requireArguments()
        val headers = args.getBundle(ARG_HEADERS) ?: Bundle.EMPTY
        return StreamSource.Direct(
            uri = args.getString(ARG_URI).orEmpty(),
            mimeType = args.getString(ARG_MIME_TYPE),
            headers = headers.keySet().associateWith { headers.getString(it).orEmpty() },
        )
    }

    companion object {
        private const val TAG = "PlaybackVideoFragment"
        private const val PROGRESS_UPDATE_MS = 250
        private const val ARG_URI = "uri"
        private const val ARG_MIME_TYPE = "mime_type"
        private const val ARG_HEADERS = "headers"

        fun newInstance(source: StreamSource.Direct) = PlaybackVideoFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_URI, source.uri)
                putString(ARG_MIME_TYPE, source.mimeType)
                putBundle(
                    ARG_HEADERS,
                    Bundle().apply { source.headers.forEach { (name, value) -> putString(name, value) } },
                )
            }
        }
    }
}
