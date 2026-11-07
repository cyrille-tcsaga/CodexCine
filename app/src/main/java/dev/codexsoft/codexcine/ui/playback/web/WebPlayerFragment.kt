package dev.codexsoft.codexcine.ui.playback.web

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.accessibility.AccessibilityManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.codexsoft.codexcine.BuildConfig
import dev.codexsoft.codexcine.R
import dev.codexsoft.codexcine.core.AppResult
import dev.codexsoft.codexcine.core.Extras
import dev.codexsoft.codexcine.core.Formatters
import dev.codexsoft.codexcine.data.playback.StreamSource
import dev.codexsoft.codexcine.domain.model.MediaType
import dev.codexsoft.codexcine.ui.common.appContainer
import dev.codexsoft.codexcine.ui.common.collectWhileStarted
import dev.codexsoft.codexcine.ui.playback.RemoteKeyHandler
import dev.codexsoft.codexcine.ui.playback.toPlaybackRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Plays a [StreamSource.WebEmbed]: the page in a hardened WebView, below native controls driven by
 * the remote. In enhanced mode the WebView can never take the focus, so the page never sees a key.
 */
class WebPlayerFragment : Fragment(), RemoteKeyHandler {

    private val viewModel: WebPlayerViewModel by viewModels {
        viewModelFactory {
            initializer {
                val container = appContainer
                val activityIntent = requireActivity().intent
                val request = activityIntent.toPlaybackRequest()
                WebPlayerViewModel(
                    request = request,
                    embed = embedFromArguments(),
                    autoResume = activityIntent.getBooleanExtra(Extras.AUTO_RESUME, false),
                    resolver = container.streamSourceResolver,
                    progress = container.watchProgressRepository,
                    nextEpisodes = NextEpisodeResolver(TmdbEpisodeCatalog(container.catalogRepository)),
                    loadDetails = { id, type -> loadDetails(id, type) },
                    persistenceScope = container.persistenceScope,
                )
            }
        }
    }

    private suspend fun loadDetails(id: Int, type: MediaType) =
        (appContainer.catalogRepository.details(id, type) as? AppResult.Success)?.data

    private lateinit var webContainer: FrameLayout
    private lateinit var fullscreenContainer: FrameLayout
    private lateinit var controls: PlayerOverlayView

    private var webView: WebView? = null
    private var bridge: PlayerBridge? = null
    private var webViewFactory: HardenedWebViewFactory? = null
    private var fullscreenCallback: WebChromeClient.CustomViewCallback? = null

    private var isEnhanced = true
    private val keyMapper = RemoteKeyMapper()
    private val consumedKeyDowns = HashSet<Int>()

    /** A panel the viewer opened, as opposed to one that follows from the player's state. */
    private var requestedPanel = PlayerPanel.NONE
    private var exitPanelShownAtMs = 0L
    private var lastAnnouncedFailure: PlayerFailure? = null
    private var wasPaused: Boolean? = null
    private var basicNotice: String? = null

    private val hideControlsRunnable = Runnable { hideControlsIfIdle() }

    /** Every load gets a new WebView and bridge, and so a new nonce. See [PlayerBridge] for why. */
    private val page = object : PlayerPage {
        override fun load(url: String) {
            destroyWebView()
            val view = createWebView()
            if (view == null) {
                viewModel.onPageFailure(PlayerFailure.WEBVIEW_UNAVAILABLE)
            } else {
                view.loadUrl(url)
            }
        }

        override fun send(command: PageCommand) {
            bridge?.send(command)
        }

        override fun stop() {
            destroyWebView()
        }
    }

    private val webEvents = object : WebPlayerEvents {
        override fun onMainFramePageFinished(view: WebView) {
            if (view !== webView) return
            viewModel.onPageFinished()
            // In basic mode keys go to the focused document, and the video is in a nested frame.
            if (!isEnhanced) view.evaluateJavascript(FOCUS_FIRST_FRAME_SCRIPT, null)
        }

        override fun onPageFailed(view: WebView, failure: PlayerFailure) {
            if (view === webView) viewModel.onPageFailure(failure)
        }

        override fun onRendererGone(view: WebView) {
            if (view === webView) viewModel.onPageFailure(PlayerFailure.RENDERER_CRASH)
        }

        override fun onShowFullscreen(view: View, callback: WebChromeClient.CustomViewCallback) {
            onHideFullscreen()
            fullscreenCallback = callback
            fullscreenContainer.addView(view, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        }

        override fun onHideFullscreen() {
            fullscreenContainer.removeAllViews()
            fullscreenCallback?.onCustomViewHidden()
            fullscreenCallback = null
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val context = inflater.context
        webContainer = FrameLayout(context)
        fullscreenContainer = FrameLayout(context)
        controls = PlayerOverlayView(context)
        return FrameLayout(context).apply {
            setBackgroundColor(android.graphics.Color.BLACK)
            addView(webContainer, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            addView(fullscreenContainer, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            addView(controls, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val forceBasic = BuildConfig.DEBUG && requireArguments().getBoolean(ARG_FORCE_BASIC)
        isEnhanced = !forceBasic && isBridgeSupported()
        webViewFactory = HardenedWebViewFactory(requireContext(), debugAssetLoader(requireContext()))

        controls.onAction = ::onOverlayAction
        controls.onRateSelected = { rate ->
            viewModel.setRate(rate)
            closeRequestedPanel()
            controls.showFeedback(null, getString(R.string.player_feedback_speed, Formatters.rate(rate)))
        }
        controls.onProgressAccessibilitySeek = { direction -> seekWithFeedback(direction * ACCESSIBILITY_SEEK_S) }
        controls.setProviderName(embedFromArguments().providerName)
        controls.setBasicMode(!isEnhanced)
        controls.revealControls(RevealFocus.PLAY_PAUSE)

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backCallback)

        if (!isEnhanced) showBasicNoticeOnce()
        viewModel.attachPage(page, isEnhanced)

        collectWhileStarted(viewModel.uiState, ::render)
        tickClock()
    }

    private fun isBridgeSupported(): Boolean = try {
        PlayerBridge.isSupported()
    } catch (e: RuntimeException) {
        Log.w(TAG, "WebView features are not readable", e)
        false
    }

    /** Creating a WebView throws when the system's WebView package is missing or being updated. */
    private fun createWebView(): WebView? {
        val factory = webViewFactory ?: return null
        return try {
            val view = factory.create(webEvents)
            view.isFocusable = !isEnhanced
            view.isFocusableInTouchMode = !isEnhanced
            if (isEnhanced) {
                bridge = PlayerBridge(requireContext(), view) { message ->
                    if (view === webView) onBridgeMessage(message)
                }
            }
            webContainer.addView(view, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            webView = view
            if (!isEnhanced) view.requestFocus()
            view
        } catch (e: RuntimeException) {
            Log.e(TAG, "Could not create a WebView", e)
            null
        }
    }

    private fun destroyWebView() {
        webEvents.onHideFullscreen()
        bridge = null
        val view = webView ?: return
        webView = null
        view.stopLoading()
        view.loadUrl(HardenedWebViewFactory.BLANK_PAGE)
        webContainer.removeView(view)
        view.removeAllViews()
        // Possibly called from one of this WebView's own callbacks, where it must not be destroyed yet.
        Handler(Looper.getMainLooper()).post { view.destroy() }
    }

    private fun onBridgeMessage(message: BridgeMessage) {
        when (message) {
            BridgeMessage.Ready -> Unit
            is BridgeMessage.State -> viewModel.onBridgeState(message.state)
            is BridgeMessage.Event -> viewModel.onBridgeEvent(message.name)
        }
    }

    override fun onResume() {
        super.onResume()
        webView?.onResume()
    }

    override fun onPause() {
        // The order matters: a paused WebView no longer delivers the command.
        viewModel.onHostPaused()
        webView?.onPause()
        super.onPause()
    }

    override fun onDestroyView() {
        view?.removeCallbacks(hideControlsRunnable)
        viewModel.detachPage()
        destroyWebView()
        activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onDestroyView()
    }

    // region Rendering

    private fun render(state: WebPlayerUiState) {
        controls.setTitles(state.request.title, subtitleOf(state), state.embed.languages)
        controls.setNextEpisodeAvailable(state.nextEpisode != null)
        controls.setClock(System.currentTimeMillis())

        val video = state.video
        if (video != null) controls.showPlayback(video, state.seekPreviewSeconds, System.currentTimeMillis())
        controls.setLoading(
            isLoading = state.phase == PlayerPhase.Loading,
            isBuffering = video?.isBuffering == true,
        )
        controls.setBanner(
            when {
                state.isUnmuteHintVisible -> getString(R.string.player_muted_hint)
                state.isSlowConnection -> getString(R.string.player_slow_connection)
                else -> basicNotice
            },
        )
        renderPanel(state)
        renderKeepScreenOn(state.keepScreenOn)
        trackPauseChanges(video)
    }

    private fun subtitleOf(state: WebPlayerUiState): String {
        if (!isEnhanced) return getString(R.string.player_basic_back_hint)
        val request = state.request
        val season = request.seasonNumber
        val episode = request.episodeNumber
        val name = state.episodeName
        val episodeLine = when {
            season == null || episode == null -> null
            name.isNullOrBlank() -> getString(R.string.playback_episode_subtitle, season, episode)
            else -> getString(R.string.player_episode_named, season, episode, name)
        }
        return listOfNotNull(state.year, episodeLine).joinToString(getString(R.string.separator))
    }

    private fun renderPanel(state: WebPlayerUiState) {
        val phase = state.phase
        val resume = state.resumePromptSeconds
        val countdown = state.nextCountdownSeconds
        val next = state.nextEpisode
        val panel = when {
            phase is PlayerPhase.Failed -> {
                controls.setError(state.request.title, getString(messageFor(phase.failure)))
                announceFailure(phase.failure)
                PlayerPanel.ERROR
            }
            resume != null -> {
                controls.setResumePrompt(resume)
                PlayerPanel.RESUME
            }
            requestedPanel != PlayerPanel.NONE -> requestedPanel
            countdown != null && next != null -> {
                controls.setNextCard(next, countdown)
                PlayerPanel.NEXT
            }
            else -> PlayerPanel.NONE
        }
        if (phase !is PlayerPhase.Failed) lastAnnouncedFailure = null
        if (panel != PlayerPanel.NONE) {
            view?.removeCallbacks(hideControlsRunnable)
        } else if (controls.panel != PlayerPanel.NONE) {
            scheduleControlsHide()
        }
        controls.showPanel(panel)
    }

    private fun announceFailure(failure: PlayerFailure) {
        if (lastAnnouncedFailure == failure) return
        lastAnnouncedFailure = failure
        controls.announceForAccessibility(
            getString(
                if (failure == PlayerFailure.NO_SOURCE || failure == PlayerFailure.SOURCE_TIMEOUT) {
                    R.string.player_announce_no_source
                } else {
                    messageFor(failure)
                },
            ),
        )
    }

    private fun messageFor(failure: PlayerFailure): Int = when (failure) {
        PlayerFailure.SOURCE_TIMEOUT -> R.string.player_error_source_timeout
        PlayerFailure.PAGE_ERROR -> R.string.player_error_page
        PlayerFailure.PLAYBACK_ERROR -> R.string.error_playback
        PlayerFailure.SSL_ERROR -> R.string.player_error_ssl
        PlayerFailure.RENDERER_CRASH -> R.string.player_error_renderer
        PlayerFailure.WEBVIEW_UNAVAILABLE -> R.string.player_error_webview
        PlayerFailure.NO_SOURCE -> R.string.error_no_source
        PlayerFailure.SERVICE_UNREACHABLE -> R.string.error_service_unreachable
    }

    private fun renderKeepScreenOn(keepOn: Boolean) {
        val window = activity?.window ?: return
        if (keepOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    /** The controls stay up while paused, and go away on their own once playback is running. */
    private fun trackPauseChanges(video: VideoState?) {
        val isPaused = video?.isPaused
        if (isPaused == wasPaused) return
        val wasFirstState = wasPaused == null
        wasPaused = isPaused
        when (isPaused) {
            true -> {
                view?.removeCallbacks(hideControlsRunnable)
                controls.revealControls(RevealFocus.CURRENT)
                if (!wasFirstState) controls.announceForAccessibility(getString(R.string.player_announce_pause))
            }
            false -> {
                scheduleControlsHide()
                if (!wasFirstState) controls.announceForAccessibility(getString(R.string.player_announce_play))
            }
            null -> Unit
        }
    }

    private fun tickClock() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    controls.setClock(System.currentTimeMillis())
                    delay(CLOCK_TICK_MS)
                }
            }
        }
    }

    private fun showBasicNoticeOnce() {
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(PREF_BASIC_NOTICE_SHOWN, false)) return
        prefs.edit { putBoolean(PREF_BASIC_NOTICE_SHOWN, true) }
        basicNotice = getString(R.string.player_basic_notice)
        controls.postDelayed(
            {
                basicNotice = null
                if (view != null) render(viewModel.uiState.value)
            },
            BASIC_NOTICE_MS,
        )
    }

    // endregion

    // region Controls visibility

    private fun scheduleControlsHide() {
        val root = view ?: return
        root.removeCallbacks(hideControlsRunnable)
        root.postDelayed(hideControlsRunnable, CONTROLS_HIDE_MS)
    }

    private fun hideControlsIfIdle() {
        val state = viewModel.uiState.value
        val isPlaying = state.video?.isPaused == false || !isEnhanced
        if (!isPlaying || controls.panel != PlayerPanel.NONE || isTalkBackActive()) return
        controls.hideControls()
    }

    private fun isTalkBackActive(): Boolean {
        val manager = requireContext().getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        return manager?.isTouchExplorationEnabled == true
    }

    private fun reveal(focus: RevealFocus = RevealFocus.CURRENT) {
        controls.revealControls(focus)
        scheduleControlsHide()
    }

    // endregion

    // region Remote

    override fun onRemoteKey(event: KeyEvent): Boolean {
        val keyCode = event.keyCode
        // Back goes through the OnBackPressedDispatcher: from Android 13 it may never arrive here as a key.
        if (keyCode == KeyEvent.KEYCODE_BACK) return false
        if (event.action == KeyEvent.ACTION_UP) return consumedKeyDowns.remove(keyCode)
        if (event.action != KeyEvent.ACTION_DOWN) return false

        val consumed = if (isEnhanced) onEnhancedKey(event) else onBasicKey(event)
        if (consumed) consumedKeyDowns += keyCode
        return consumed
    }

    private fun onEnhancedKey(event: KeyEvent): Boolean {
        if (controls.panel != PlayerPanel.NONE) return false
        val wasVisible = controls.isControlsVisible
        val command = keyMapper.map(event.keyCode, event.repeatCount, event.eventTime, keyContext())
        if (command == PlayerCommand.PassThrough) {
            if (wasVisible) scheduleControlsHide()
            return false
        }
        execute(command)
        return true
    }

    private fun keyContext(): KeyContext {
        val state = viewModel.uiState.value
        return KeyContext(
            isOverlayVisible = controls.isControlsVisible,
            isProgressFocused = controls.isProgressFocused,
            isSeries = state.request.mediaType == MediaType.TV,
            isUnmuteHintVisible = state.isUnmuteHintVisible,
        )
    }

    private fun execute(command: PlayerCommand) {
        when (command) {
            PlayerCommand.TogglePlayPause -> {
                val willPlay = viewModel.uiState.value.video?.isPaused == true
                viewModel.togglePlayPause()
                showPlayPauseFeedback(willPlay)
                reveal()
            }
            PlayerCommand.Play -> {
                viewModel.play()
                showPlayPauseFeedback(true)
            }
            PlayerCommand.Pause -> {
                viewModel.pause()
                showPlayPauseFeedback(false)
            }
            PlayerCommand.Unmute -> viewModel.unmute()
            is PlayerCommand.SeekBy -> if (command.seconds != 0) seekWithFeedback(command.seconds)
            is PlayerCommand.JumpToPercent -> {
                viewModel.jumpToPercent(command.percent)
                reveal(RevealFocus.PROGRESS)
            }
            is PlayerCommand.Reveal -> reveal(command.focus)
            PlayerCommand.NextEpisode -> goToNextEpisode()
            PlayerCommand.PreviousOrRestart -> viewModel.previousOrRestart()
            PlayerCommand.OpenSpeedSelector -> openPanel(PlayerPanel.SPEED)
            PlayerCommand.HideOverlay -> controls.hideControls()
            PlayerCommand.ConfirmExit -> openExitPanel()
            PlayerCommand.PassThrough -> Unit
        }
    }

    private fun seekWithFeedback(seconds: Int) {
        viewModel.seekBy(seconds)
        if (!controls.isControlsVisible) reveal(RevealFocus.PROGRESS) else scheduleControlsHide()
        controls.showFeedback(
            null,
            getString(
                if (seconds > 0) R.string.player_feedback_forward else R.string.player_feedback_rewind,
                abs(seconds),
            ),
        )
    }

    private fun showPlayPauseFeedback(isPlaying: Boolean) {
        controls.showFeedback(
            if (isPlaying) R.drawable.ic_player_play else R.drawable.ic_player_pause,
            getString(if (isPlaying) R.string.player_announce_play else R.string.player_announce_pause),
        )
    }

    private fun goToNextEpisode() {
        if (viewModel.uiState.value.nextEpisode == null) return
        controls.announceForAccessibility(getString(R.string.player_cd_next))
        viewModel.nextEpisode()
    }

    /** Most HTML5 players follow the keyboard: Space toggles, and the arrows seek. */
    private fun onBasicKey(event: KeyEvent): Boolean {
        if (controls.panel != PlayerPanel.NONE) return false
        val synthetic = when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> KeyEvent.KEYCODE_SPACE
            KeyEvent.KEYCODE_DPAD_LEFT -> KeyEvent.KEYCODE_DPAD_LEFT
            KeyEvent.KEYCODE_DPAD_RIGHT -> KeyEvent.KEYCODE_DPAD_RIGHT
            else -> return false
        }
        val view = webView ?: return false
        view.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, synthetic))
        view.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, synthetic))
        reveal()
        return true
    }

    private val backCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            when (controls.panel) {
                PlayerPanel.ERROR, PlayerPanel.RESUME -> requireActivity().finish()
                PlayerPanel.SPEED -> closeRequestedPanel()
                PlayerPanel.NEXT -> viewModel.cancelNext()
                PlayerPanel.EXIT ->
                    if (System.currentTimeMillis() - exitPanelShownAtMs <= SECOND_BACK_WINDOW_MS) {
                        saveAndQuit()
                    } else {
                        closeRequestedPanel()
                    }
                PlayerPanel.NONE -> execute(
                    keyMapper.map(KeyEvent.KEYCODE_BACK, 0, System.currentTimeMillis(), keyContext()),
                )
            }
        }
    }

    private fun openPanel(panel: PlayerPanel) {
        requestedPanel = panel
        render(viewModel.uiState.value)
    }

    private fun openExitPanel() {
        exitPanelShownAtMs = System.currentTimeMillis()
        openPanel(PlayerPanel.EXIT)
    }

    private fun closeRequestedPanel() {
        requestedPanel = PlayerPanel.NONE
        render(viewModel.uiState.value)
    }

    private fun saveAndQuit() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.saveProgress()?.join()
            activity?.finish()
        }
    }

    private fun onOverlayAction(action: OverlayAction) {
        scheduleControlsHide()
        when (action) {
            OverlayAction.REWIND -> seekWithFeedback(-BUTTON_SEEK_S)
            OverlayAction.FORWARD -> seekWithFeedback(BUTTON_SEEK_S)
            OverlayAction.PLAY_PAUSE -> viewModel.togglePlayPause()
            OverlayAction.SPEED -> openPanel(PlayerPanel.SPEED)
            OverlayAction.MUTE -> viewModel.toggleMute()
            OverlayAction.NEXT -> goToNextEpisode()
            OverlayAction.RELOAD -> {
                viewModel.saveProgress()
                viewModel.retry()
            }
            OverlayAction.QUIT, OverlayAction.EXIT_QUIT -> saveAndQuit()
            OverlayAction.EXIT_CONTINUE -> closeRequestedPanel()
            OverlayAction.RESUME_YES -> viewModel.acceptResume()
            OverlayAction.RESUME_NO -> viewModel.declineResume()
            OverlayAction.NEXT_NOW -> viewModel.playNextNow()
            OverlayAction.NEXT_CANCEL -> viewModel.cancelNext()
            OverlayAction.ERROR_RETRY -> viewModel.retry()
            OverlayAction.ERROR_BACK -> requireActivity().finish()
        }
    }

    // endregion

    private fun embedFromArguments(): StreamSource.WebEmbed {
        val args = requireArguments()
        return StreamSource.WebEmbed(
            url = args.getString(ARG_URL).orEmpty(),
            languages = args.getStringArrayList(ARG_LANGUAGES).orEmpty(),
            providerName = args.getString(ARG_PROVIDER).orEmpty(),
        )
    }

    companion object {
        private const val TAG = "WebPlayerFragment"
        private const val MATCH_PARENT = ViewGroup.LayoutParams.MATCH_PARENT
        private const val ARG_URL = "url"
        private const val ARG_LANGUAGES = "languages"
        private const val ARG_PROVIDER = "provider"
        private const val ARG_FORCE_BASIC = "force_basic"
        private const val PREFS_NAME = "player_prefs"
        private const val PREF_BASIC_NOTICE_SHOWN = "basic_notice_shown"
        private const val BUTTON_SEEK_S = 10
        private const val ACCESSIBILITY_SEEK_S = 10
        private const val CONTROLS_HIDE_MS = 5_000L
        private const val SECOND_BACK_WINDOW_MS = 2_000L
        private const val CLOCK_TICK_MS = 15_000L
        private const val BASIC_NOTICE_MS = 8_000L
        private const val FOCUS_FIRST_FRAME_SCRIPT =
            "(function(){var f=document.querySelector('iframe');if(f)f.focus();})();"

        fun newInstance(source: StreamSource.WebEmbed, forceBasicMode: Boolean = false) = WebPlayerFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_URL, source.url)
                putStringArrayList(ARG_LANGUAGES, ArrayList(source.languages))
                putString(ARG_PROVIDER, source.providerName)
                putBoolean(ARG_FORCE_BASIC, forceBasicMode)
            }
        }
    }
}
