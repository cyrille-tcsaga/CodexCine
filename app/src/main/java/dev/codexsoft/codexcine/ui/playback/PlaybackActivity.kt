package dev.codexsoft.codexcine.ui.playback

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.view.KeyEvent
import androidx.fragment.app.Fragment
import dev.codexsoft.codexcine.BuildConfig
import dev.codexsoft.codexcine.core.Extras
import dev.codexsoft.codexcine.data.playback.PlaybackRequest
import dev.codexsoft.codexcine.data.playback.StreamSource
import dev.codexsoft.codexcine.domain.model.MediaType
import dev.codexsoft.codexcine.ui.common.FragmentHostActivity
import dev.codexsoft.codexcine.ui.playback.web.WebPlayerFragment

/** Implemented by a player that wants the remote's keys before the view hierarchy sees them. */
interface RemoteKeyHandler {
    fun onRemoteKey(event: KeyEvent): Boolean
}

class PlaybackActivity : FragmentHostActivity() {

    override fun createContentFragment(): Fragment {
        val debugEmbedUrl = intent.getStringExtra(Extras.DEBUG_EMBED_URL)
        if (BuildConfig.DEBUG && debugEmbedUrl != null) {
            return WebPlayerFragment.newInstance(
                StreamSource.WebEmbed(debugEmbedUrl, listOf("vf"), providerName = "Test local"),
                forceBasicMode = intent.getBooleanExtra(Extras.DEBUG_FORCE_BASIC_MODE, false),
            )
        }
        return PlaybackResolverFragment()
    }

    // Public Activity API. Lint only objects because ComponentActivity redeclares it with a restricted annotation.
    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val handler = supportFragmentManager.findFragmentById(android.R.id.content) as? RemoteKeyHandler
        return handler?.onRemoteKey(event) == true || super.dispatchKeyEvent(event)
    }

    companion object {
        fun newIntent(
            context: Context,
            mediaId: Int,
            mediaType: MediaType,
            title: String,
            seasonNumber: Int? = null,
            episodeNumber: Int? = null,
            autoResume: Boolean = false,
        ): Intent = Intent(context, PlaybackActivity::class.java)
            .putExtra(Extras.MEDIA_ID, mediaId)
            .putExtra(Extras.MEDIA_TYPE, mediaType.value)
            .putExtra(Extras.TITLE, title)
            .putExtra(Extras.AUTO_RESUME, autoResume)
            .apply {
                seasonNumber?.let { putExtra(Extras.SEASON_NUMBER, it) }
                episodeNumber?.let { putExtra(Extras.EPISODE_NUMBER, it) }
            }
    }
}

fun Intent.toPlaybackRequest() = PlaybackRequest(
    mediaId = getIntExtra(Extras.MEDIA_ID, Extras.NO_VALUE),
    mediaType = MediaType.fromValue(getStringExtra(Extras.MEDIA_TYPE)) ?: MediaType.MOVIE,
    title = getStringExtra(Extras.TITLE).orEmpty(),
    seasonNumber = getIntExtra(Extras.SEASON_NUMBER, Extras.NO_VALUE).takeIf { it != Extras.NO_VALUE },
    episodeNumber = getIntExtra(Extras.EPISODE_NUMBER, Extras.NO_VALUE).takeIf { it != Extras.NO_VALUE },
)
