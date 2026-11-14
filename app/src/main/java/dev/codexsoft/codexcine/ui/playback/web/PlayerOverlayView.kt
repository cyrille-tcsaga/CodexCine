package dev.codexsoft.codexcine.ui.playback.web

import android.content.Context
import android.provider.Settings
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import dev.codexsoft.codexcine.R
import dev.codexsoft.codexcine.core.Formatters

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

/** Native controls above the WebView. It displays state and reports presses, and decides nothing. */
class PlayerOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs), PlayerControls {

    override val view: View get() = this

    override var onAction: ((OverlayAction) -> Unit)? = null
    override var onRateSelected: ((Double) -> Unit)? = null

    private val areAnimationsEnabled = Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f,
    ) != 0f

    private val topBar: View
    private val bottomBar: ViewGroup
    private val title: TextView
    private val subtitle: TextView
    private val chips: LinearLayout
    private val clock: TextView
    private val seekPreview: TextView
    private val progress: PlayerProgressView
    private val elapsed: TextView
    private val endTime: TextView
    private val remaining: TextView
    private val focusHint: TextView
    private val playPause: ImageButton
    private val mute: ImageButton
    private val speed: Button
    private val next: ImageButton
    private val loading: View
    private val loadingText: TextView
    private val loadingProvider: TextView
    private val feedback: View
    private val feedbackIcon: ImageView
    private val feedbackText: TextView
    private val banner: TextView
    private val panels: Map<PlayerPanel, View>
    private val speedOptions: LinearLayout
    private val resumeTitle: TextView
    private val nextTitle: TextView
    private val nextRing: ProgressBar
    private val nextSeconds: TextView
    private val errorTitle: TextView
    private val errorMessage: TextView
    private val errorRetry: Button

    private var currentRate = 1.0
    private val hideFeedback = Runnable { feedback.isVisible = false }

    override var isControlsVisible = true
        private set
    override var panel = PlayerPanel.NONE
        private set

    override val isProgressFocused: Boolean get() = progress.isFocused

    override var onProgressAccessibilitySeek: ((Int) -> Unit)?
        get() = progress.onAccessibilitySeek
        set(value) {
            progress.onAccessibilitySeek = value
        }

    init {
        LayoutInflater.from(context).inflate(R.layout.view_player_overlay, this, true)
        topBar = findViewById(R.id.player_top_bar)
        bottomBar = findViewById(R.id.player_bottom_bar)
        title = findViewById(R.id.player_title)
        subtitle = findViewById(R.id.player_subtitle)
        chips = findViewById(R.id.player_language_chips)
        clock = findViewById(R.id.player_clock)
        seekPreview = findViewById(R.id.player_seek_preview)
        progress = findViewById(R.id.player_progress)
        elapsed = findViewById(R.id.player_elapsed)
        endTime = findViewById(R.id.player_end_time)
        remaining = findViewById(R.id.player_remaining)
        focusHint = findViewById(R.id.player_focus_hint)
        playPause = findViewById(R.id.player_button_play_pause)
        mute = findViewById(R.id.player_button_mute)
        speed = findViewById(R.id.player_button_speed)
        next = findViewById(R.id.player_button_next)
        loading = findViewById(R.id.player_loading)
        loadingText = findViewById(R.id.player_loading_text)
        loadingProvider = findViewById(R.id.player_loading_provider)
        feedback = findViewById(R.id.player_feedback)
        feedbackIcon = findViewById(R.id.player_feedback_icon)
        feedbackText = findViewById(R.id.player_feedback_text)
        banner = findViewById(R.id.player_banner)
        speedOptions = findViewById(R.id.player_speed_options)
        resumeTitle = findViewById(R.id.player_resume_title)
        nextTitle = findViewById(R.id.player_next_title)
        nextRing = findViewById(R.id.player_next_ring)
        nextSeconds = findViewById(R.id.player_next_seconds)
        errorTitle = findViewById(R.id.player_error_title)
        errorMessage = findViewById(R.id.player_error_message)
        errorRetry = findViewById(R.id.player_error_retry)
        panels = mapOf(
            PlayerPanel.SPEED to findViewById(R.id.player_panel_speed),
            PlayerPanel.EXIT to findViewById(R.id.player_panel_exit),
            PlayerPanel.RESUME to findViewById(R.id.player_panel_resume),
            PlayerPanel.NEXT to findViewById(R.id.player_panel_next),
            PlayerPanel.ERROR to findViewById(R.id.player_panel_error),
        )

        // Keeps the focus inside the player when every control is hidden.
        isFocusable = true
        descendantFocusability = FOCUS_AFTER_DESCENDANTS

        bindButtons()
        progress.setOnClickListener { onAction?.invoke(OverlayAction.PLAY_PAUSE) }
        setRate(1.0)
        progress.setOnFocusChangeListener { view, _ ->
            view.invalidate()
            updateFocusHint()
        }
        buildSpeedOptions()
    }

    private fun bindButtons() {
        val actions = mapOf(
            R.id.player_button_rewind to OverlayAction.REWIND,
            R.id.player_button_play_pause to OverlayAction.PLAY_PAUSE,
            R.id.player_button_forward to OverlayAction.FORWARD,
            R.id.player_button_speed to OverlayAction.SPEED,
            R.id.player_button_mute to OverlayAction.MUTE,
            R.id.player_button_next to OverlayAction.NEXT,
            R.id.player_button_reload to OverlayAction.RELOAD,
            R.id.player_button_quit to OverlayAction.QUIT,
            R.id.player_exit_continue to OverlayAction.EXIT_CONTINUE,
            R.id.player_exit_quit to OverlayAction.EXIT_QUIT,
            R.id.player_resume_yes to OverlayAction.RESUME_YES,
            R.id.player_resume_no to OverlayAction.RESUME_NO,
            R.id.player_next_now to OverlayAction.NEXT_NOW,
            R.id.player_next_cancel to OverlayAction.NEXT_CANCEL,
            R.id.player_error_retry to OverlayAction.ERROR_RETRY,
            R.id.player_error_back to OverlayAction.ERROR_BACK,
        )
        actions.forEach { (id, action) ->
            val button = findViewById<View>(id)
            button.setOnClickListener { onAction?.invoke(action) }
            button.setOnFocusChangeListener(::onButtonFocusChanged)
            allowScalingBeyond(button)
        }
    }

    /** A focused button grows past its own bounds, and every parent up to this view would cut it off. */
    private fun allowScalingBeyond(view: View) {
        var parent = view.parent as? ViewGroup
        while (parent != null) {
            parent.clipChildren = false
            parent.clipToPadding = false
            if (parent === this) return
            parent = parent.parent as? ViewGroup
        }
    }

    private fun onButtonFocusChanged(view: View, hasFocus: Boolean) {
        val scale = if (hasFocus) FOCUSED_SCALE else 1f
        if (areAnimationsEnabled) {
            view.animate().scaleX(scale).scaleY(scale).setDuration(FOCUS_ANIMATION_MS).start()
        } else {
            view.scaleX = scale
            view.scaleY = scale
        }
        if (view.parent === playPause.parent) updateFocusHint()
    }

    private fun buildSpeedOptions() {
        speedOptions.removeAllViews()
        BridgeProtocol.ALLOWED_RATES.forEach { rate ->
            val label = Formatters.rate(rate)
            val option = LayoutInflater.from(context).inflate(R.layout.view_player_speed_option, speedOptions, false) as Button
            option.text = context.getString(
                if (rate == currentRate) R.string.player_rate_selected else R.string.player_rate,
                label,
            )
            option.tag = rate
            option.setOnClickListener { onRateSelected?.invoke(rate) }
            option.setOnFocusChangeListener(::onButtonFocusChanged)
            speedOptions.addView(option)
            allowScalingBeyond(option)
        }
    }

    // region Static content

    override fun setTitles(titleText: String, subtitleText: String, languages: List<String>) {
        title.text = titleText
        subtitle.text = subtitleText
        subtitle.isVisible = subtitleText.isNotEmpty()
        chips.updateLayoutParams<MarginLayoutParams> {
            marginStart = if (subtitleText.isEmpty()) 0 else (12 * resources.displayMetrics.density).toInt()
        }
        val labels = languages.map { it.uppercase() }
        if (chips.childCount != labels.size) {
            chips.removeAllViews()
            labels.forEach { chips.addView(createChip(it)) }
        }
    }

    private fun createChip(label: String) = TextView(context).apply {
        text = label
        setTextAppearance(R.style.TextAppearance_CodexCine_PlayerBody)
        setBackgroundResource(R.drawable.bg_player_chip)
        val horizontal = (8 * resources.displayMetrics.density).toInt()
        setPadding(horizontal, 0, horizontal, 0)
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { marginEnd = horizontal }
        gravity = Gravity.CENTER
    }

    override fun setProviderName(name: String) {
        loadingProvider.text = name
    }

    override fun setClock(nowMs: Long) {
        clock.text = Formatters.clock(nowMs)
    }

    override fun setBasicMode(isBasic: Boolean) {
        bottomBar.isVisible = !isBasic
        if (isBasic) subtitle.text = context.getString(R.string.player_basic_back_hint)
        if (isBasic) subtitle.isVisible = true
    }

    // endregion

    // region Playback state

    override fun showPlayback(video: VideoState, seekPreviewSeconds: Double?, nowMs: Long) {
        val shown = seekPreviewSeconds ?: video.currentTime
        progress.setProgress(shown, video.bufferedEnd, video.duration)
        progress.contentDescription = context.getString(
            R.string.player_cd_position,
            spoken(shown),
            spoken(video.duration),
        )
        elapsed.text = Formatters.timecode(shown)
        remaining.text = context.getString(R.string.player_remaining, Formatters.timecode(video.duration - shown))
        val rate = video.rate.takeIf { it > 0 } ?: 1.0
        val endMs = nowMs + ((video.duration - video.currentTime) / rate * 1000).toLong()
        endTime.text = context.getString(R.string.player_end_at, Formatters.clock(endMs))

        playPause.setImageResource(if (video.isPaused) R.drawable.ic_player_play else R.drawable.ic_player_pause)
        playPause.contentDescription =
            context.getString(if (video.isPaused) R.string.player_cd_play else R.string.player_cd_pause)
        mute.setImageResource(if (video.isMuted) R.drawable.ic_player_muted else R.drawable.ic_player_volume)
        mute.contentDescription =
            context.getString(if (video.isMuted) R.string.player_cd_unmute else R.string.player_cd_mute)
        setRate(video.rate)
        updateFocusHint()

        if (seekPreviewSeconds == null) {
            seekPreview.visibility = INVISIBLE
        } else {
            seekPreview.text = Formatters.timecode(seekPreviewSeconds)
            seekPreview.visibility = VISIBLE
            positionSeekPreview(seekPreviewSeconds)
        }
    }

    private fun updateFocusHint() {
        val focused = (playPause.parent as ViewGroup).focusedChild
        focusHint.text = focused?.contentDescription ?: (focused as? TextView)?.text
    }

    private fun positionSeekPreview(seconds: Double) {
        seekPreview.post {
            val centered = progress.xFor(seconds) - seekPreview.width / 2f
            seekPreview.translationX = centered.coerceIn(0f, (progress.width - seekPreview.width).toFloat().coerceAtLeast(0f))
        }
    }

    private fun setRate(rate: Double) {
        val label = Formatters.rate(rate)
        speed.text = context.getString(R.string.player_rate, label)
        speed.contentDescription = context.getString(R.string.player_cd_speed, label)
        if (rate != currentRate) {
            currentRate = rate
            buildSpeedOptions()
        }
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

    override fun setNextEpisodeAvailable(isAvailable: Boolean) {
        next.isVisible = isAvailable
    }

    override fun setLoading(isLoading: Boolean, isBuffering: Boolean) {
        loading.isVisible = isLoading || isBuffering
        loadingText.isVisible = isLoading
        loadingProvider.isVisible = isLoading
    }

    override fun setBanner(text: String?) {
        banner.text = text
        banner.isVisible = text != null
    }

    override fun showFeedback(@DrawableRes icon: Int?, text: String) {
        feedbackIcon.isVisible = icon != null
        icon?.let(feedbackIcon::setImageResource)
        feedbackText.text = text
        feedback.isVisible = true
        removeCallbacks(hideFeedback)
        postDelayed(hideFeedback, FEEDBACK_MS)
    }

    // endregion

    // region Visibility and focus

    override fun revealControls(focus: RevealFocus) {
        val wasVisible = isControlsVisible
        setControlsVisible(true)
        if (panel != PlayerPanel.NONE) return
        when (focus) {
            RevealFocus.PROGRESS -> progress.requestFocus()
            RevealFocus.PLAY_PAUSE -> playPause.requestFocus()
            RevealFocus.CURRENT -> if (!wasVisible || !bottomBarHasFocus()) playPause.requestFocus()
        }
    }

    private fun bottomBarHasFocus() = bottomBar.findFocus() != null

    override fun hideControls() {
        setControlsVisible(false)
        requestFocus()
    }

    private fun setControlsVisible(isVisible: Boolean) {
        if (isControlsVisible == isVisible) return
        isControlsVisible = isVisible
        listOf(topBar, bottomBar).forEach { bar ->
            if (!areAnimationsEnabled) {
                bar.alpha = 1f
                bar.visibility = if (isVisible) VISIBLE else INVISIBLE
            } else if (isVisible) {
                bar.visibility = VISIBLE
                bar.animate().alpha(1f).setDuration(FADE_MS).withEndAction(null).start()
            } else {
                bar.animate().alpha(0f).setDuration(FADE_MS).withEndAction { bar.visibility = INVISIBLE }.start()
            }
        }
    }

    override fun showPanel(new: PlayerPanel) {
        if (panel == new) return
        panels[panel]?.isVisible = false
        panel = new
        panels[new]?.isVisible = true
        // An error has no video behind it, so the controls would only be noise.
        bottomBar.alpha = if (new == PlayerPanel.ERROR) 0f else 1f
        // A panel must not lose the focus to the controls behind it.
        bottomBar.descendantFocusability =
            if (new == PlayerPanel.NONE) FOCUS_AFTER_DESCENDANTS else FOCUS_BLOCK_DESCENDANTS
        when (new) {
            PlayerPanel.NONE -> if (isControlsVisible) playPause.requestFocus() else requestFocus()
            PlayerPanel.SPEED -> focusCurrentRate()
            PlayerPanel.EXIT -> findViewById<View>(R.id.player_exit_continue).requestFocus()
            PlayerPanel.RESUME -> findViewById<View>(R.id.player_resume_yes).requestFocus()
            PlayerPanel.NEXT -> findViewById<View>(R.id.player_next_now).requestFocus()
            PlayerPanel.ERROR -> errorRetry.requestFocus()
        }
    }

    private fun focusCurrentRate() {
        speedOptions.findViewWithTag<View>(currentRate)?.requestFocus()
    }

    override fun setResumePrompt(seconds: Double) {
        resumeTitle.text = context.getString(R.string.player_resume_title, Formatters.timecode(seconds))
    }

    override fun setNextCard(episode: EpisodeRef, secondsLeft: Int) {
        val name = episode.name
        nextTitle.text = if (name.isNullOrBlank()) {
            context.getString(R.string.player_next_title_unnamed, episode.season, episode.episode)
        } else {
            context.getString(R.string.player_next_title, episode.season, episode.episode, name)
        }
        nextRing.progress = secondsLeft
        nextSeconds.text = context.getString(R.string.player_countdown, secondsLeft)
    }

    override fun setError(titleText: String, message: String) {
        errorTitle.text = titleText
        errorMessage.text = message
    }

    // endregion

    private companion object {
        const val FOCUSED_SCALE = 1.1f
        const val FOCUS_ANIMATION_MS = 150L
        const val FADE_MS = 200L
        const val FEEDBACK_MS = 800L
    }
}
