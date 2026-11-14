package dev.codexsoft.codexcine.ui.playback.web

import android.view.View
import androidx.annotation.DrawableRes

enum class PlayerPanel { NONE, SPEED, EXIT, RESUME, NEXT, ERROR }

enum class OverlayAction {
    REWIND,
    PLAY_PAUSE,
    FORWARD,
    SPEED,
    MUTE,
    NEXT,
    RELOAD,
    QUIT,
    EXIT_CONTINUE,
    EXIT_QUIT,
    RESUME_YES,
    RESUME_NO,
    NEXT_NOW,
    NEXT_CANCEL,
    ERROR_RETRY,
    ERROR_BACK,
}

/**
 * The native controls above the WebView, as the player fragment sees them: it sends state, they report
 * presses and decide nothing. Two implementations exist while the migration lasts: [PlayerOverlayView]
 * (views) and `ComposePlayerOverlay`, chosen by the `PLAYER_OVERLAY` flag.
 */
interface PlayerControls {
    /** The view to put above the WebView. */
    val view: View

    var onAction: ((OverlayAction) -> Unit)?
    var onRateSelected: ((Double) -> Unit)?

    /** Called with the sign of a scroll requested by an accessibility service on the progress bar. */
    var onProgressAccessibilitySeek: ((Int) -> Unit)?

    val isControlsVisible: Boolean
    val panel: PlayerPanel
    val isProgressFocused: Boolean

    fun setTitles(titleText: String, subtitleText: String, languages: List<String>)
    fun setProviderName(name: String)
    fun setClock(nowMs: Long)

    /** Basic mode has no data: only the title, a way out and the clock remain. */
    fun setBasicMode(isBasic: Boolean)

    fun showPlayback(video: VideoState, seekPreviewSeconds: Double?, nowMs: Long)
    fun setNextEpisodeAvailable(isAvailable: Boolean)
    fun setLoading(isLoading: Boolean, isBuffering: Boolean)
    fun setBanner(text: String?)
    fun showFeedback(@DrawableRes icon: Int?, text: String)

    fun revealControls(focus: RevealFocus)
    fun hideControls()
    fun showPanel(new: PlayerPanel)

    fun setResumePrompt(seconds: Double)
    fun setNextCard(episode: EpisodeRef, secondsLeft: Int)
    fun setError(titleText: String, message: String)
}
