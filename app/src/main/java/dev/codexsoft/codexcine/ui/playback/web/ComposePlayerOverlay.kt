package dev.codexsoft.codexcine.ui.playback.web

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import androidx.annotation.DrawableRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import dev.codexsoft.codexcine.R
import dev.codexsoft.codexcine.core.Formatters
import dev.codexsoft.codexcine.ui.compose.theme.CodexCineTheme

/** What the overlay should focus once it is composed (a request made before that cannot be honoured). */
internal enum class PlayerFocusTarget { PROGRESS, PLAY_PAUSE, ROOT, SPEED_OPTION, EXIT, RESUME, NEXT, ERROR }

/** Everything the Compose controls draw. Plain Compose state: the fragment writes it, the content reads it. */
internal class PlayerOverlayState {
    var title by mutableStateOf("")
    var subtitle by mutableStateOf("")
    var languages by mutableStateOf<List<String>>(emptyList())
    var clock by mutableStateOf("")
    var providerName by mutableStateOf("")
    var isBasic by mutableStateOf(false)

    var hasVideo by mutableStateOf(false)
    var position by mutableStateOf(0.0)
    var buffered by mutableStateOf(0.0)
    var duration by mutableStateOf(0.0)
    var progressDescription by mutableStateOf("")
    var elapsed by mutableStateOf("")
    var remaining by mutableStateOf("")
    var endTime by mutableStateOf("")
    var isPaused by mutableStateOf(false)
    var isMuted by mutableStateOf(false)
    var rate by mutableStateOf(1.0)
    var seekPreview by mutableStateOf<Double?>(null)
    var seekPreviewText by mutableStateOf("")
    var hasNext by mutableStateOf(false)

    var isLoading by mutableStateOf(false)
    var isBuffering by mutableStateOf(false)
    var banner by mutableStateOf<String?>(null)

    var feedbackIcon by mutableStateOf<Int?>(null)
    var feedbackText by mutableStateOf("")
    var feedbackSerial by mutableStateOf(0)

    var controlsVisible by mutableStateOf(true)
    var panel by mutableStateOf(PlayerPanel.NONE)

    var resumeTitle by mutableStateOf("")
    var nextTitle by mutableStateOf("")
    var nextSecondsLeft by mutableStateOf(0)
    var errorTitle by mutableStateOf("")
    var errorMessage by mutableStateOf("")

    var progressFocused by mutableStateOf(false)
    var bottomBarFocused by mutableStateOf(false)

    var focusRequest by mutableStateOf<PlayerFocusTarget?>(null)
}

/**
 * The player's controls in Compose, with the same contract as [PlayerOverlayView]: the fragment sends state
 * and receives presses, and every decision stays in `WebPlayerViewModel`. Remote keys still go through
 * the fragment's `onRemoteKey` first; what it lets through (arrows between controls, OK) reaches the
 * Compose focus system.
 */
class ComposePlayerOverlay(context: Context) : FrameLayout(context), PlayerControls {

    private val state = PlayerOverlayState()

    override val view: View get() = this

    override var onAction: ((OverlayAction) -> Unit)? = null
    override var onRateSelected: ((Double) -> Unit)? = null
    override var onProgressAccessibilitySeek: ((Int) -> Unit)? = null

    override val isControlsVisible: Boolean get() = state.controlsVisible
    override val panel: PlayerPanel get() = state.panel
    override val isProgressFocused: Boolean get() = state.progressFocused

    init {
        val compose = ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                CodexCineTheme {
                    PlayerOverlayContent(
                        state = state,
                        onAction = { onAction?.invoke(it) },
                        onRate = { onRateSelected?.invoke(it) },
                        onAccessibilitySeek = { onProgressAccessibilitySeek?.invoke(it) },
                    )
                }
            }
        }
        addView(compose, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        // Same as the view version: the player keeps the focus even when every control is hidden.
        isFocusable = false
    }

    override fun setTitles(titleText: String, subtitleText: String, languages: List<String>) {
        state.title = titleText
        state.subtitle = subtitleText
        state.languages = languages.map { it.uppercase() }
    }

    override fun setProviderName(name: String) {
        state.providerName = name
    }

    override fun setClock(nowMs: Long) {
        state.clock = Formatters.clock(nowMs)
    }

    override fun setBasicMode(isBasic: Boolean) {
        state.isBasic = isBasic
        if (isBasic) state.subtitle = context.getString(R.string.player_basic_back_hint)
    }

    override fun showPlayback(video: VideoState, seekPreviewSeconds: Double?, nowMs: Long) {
        val shown = seekPreviewSeconds ?: video.currentTime
        state.hasVideo = true
        state.position = shown
        state.buffered = video.bufferedEnd
        state.duration = video.duration
        state.progressDescription = context.getString(R.string.player_cd_position, spoken(shown), spoken(video.duration))
        state.elapsed = Formatters.timecode(shown)
        state.remaining = context.getString(R.string.player_remaining, Formatters.timecode(video.duration - shown))
        val rate = video.rate.takeIf { it > 0 } ?: 1.0
        val endMs = nowMs + ((video.duration - video.currentTime) / rate * 1000).toLong()
        state.endTime = context.getString(R.string.player_end_at, Formatters.clock(endMs))
        state.isPaused = video.isPaused
        state.isMuted = video.isMuted
        state.rate = rate
        state.seekPreview = seekPreviewSeconds
        if (seekPreviewSeconds != null) state.seekPreviewText = Formatters.timecode(seekPreviewSeconds)
    }

    override fun setNextEpisodeAvailable(isAvailable: Boolean) {
        state.hasNext = isAvailable
    }

    override fun setLoading(isLoading: Boolean, isBuffering: Boolean) {
        state.isLoading = isLoading
        state.isBuffering = isBuffering
    }

    override fun setBanner(text: String?) {
        state.banner = text
    }

    override fun showFeedback(@DrawableRes icon: Int?, text: String) {
        state.feedbackIcon = icon
        state.feedbackText = text
        state.feedbackSerial++
    }

    override fun revealControls(focus: RevealFocus) {
        val wasVisible = state.controlsVisible
        state.controlsVisible = true
        if (state.panel != PlayerPanel.NONE) return
        when (focus) {
            RevealFocus.PROGRESS -> state.focusRequest = PlayerFocusTarget.PROGRESS
            RevealFocus.PLAY_PAUSE -> state.focusRequest = PlayerFocusTarget.PLAY_PAUSE
            RevealFocus.CURRENT ->
                if (!wasVisible || !state.bottomBarFocused) state.focusRequest = PlayerFocusTarget.PLAY_PAUSE
        }
    }

    override fun hideControls() {
        state.controlsVisible = false
        state.focusRequest = PlayerFocusTarget.ROOT
    }

    override fun showPanel(new: PlayerPanel) {
        if (state.panel == new) return
        state.panel = new
        state.focusRequest = when (new) {
            PlayerPanel.NONE -> if (state.controlsVisible) PlayerFocusTarget.PLAY_PAUSE else PlayerFocusTarget.ROOT
            PlayerPanel.SPEED -> PlayerFocusTarget.SPEED_OPTION
            PlayerPanel.EXIT -> PlayerFocusTarget.EXIT
            PlayerPanel.RESUME -> PlayerFocusTarget.RESUME
            PlayerPanel.NEXT -> PlayerFocusTarget.NEXT
            PlayerPanel.ERROR -> PlayerFocusTarget.ERROR
        }
    }

    override fun setResumePrompt(seconds: Double) {
        state.resumeTitle = context.getString(R.string.player_resume_title, Formatters.timecode(seconds))
    }

    override fun setNextCard(episode: EpisodeRef, secondsLeft: Int) {
        val name = episode.name
        state.nextTitle = if (name.isNullOrBlank()) {
            context.getString(R.string.player_next_title_unnamed, episode.season, episode.episode)
        } else {
            context.getString(R.string.player_next_title, episode.season, episode.episode, name)
        }
        state.nextSecondsLeft = secondsLeft
    }

    override fun setError(titleText: String, message: String) {
        state.errorTitle = titleText
        state.errorMessage = message
    }

    private fun spoken(seconds: Double): String {
        val total = seconds.toLong().coerceAtLeast(0)
        val hours = (total / 3600).toInt()
        val minutes = ((total % 3600) / 60).toInt()
        val rest = (total % 60).toInt()
        return if (hours > 0) {
            resources.getQuantityString(R.plurals.player_spoken_hours, hours, hours, minutes)
        } else {
            resources.getQuantityString(R.plurals.player_spoken_minutes, minutes, minutes, rest)
        }
    }
}
