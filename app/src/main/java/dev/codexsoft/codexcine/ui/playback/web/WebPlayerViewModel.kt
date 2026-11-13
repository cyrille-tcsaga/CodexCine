package dev.codexsoft.codexcine.ui.playback.web

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.codexsoft.codexcine.data.playback.PlaybackRequest
import dev.codexsoft.codexcine.data.playback.StreamServiceException
import dev.codexsoft.codexcine.data.playback.StreamSource
import dev.codexsoft.codexcine.data.playback.StreamSourceResolver
import dev.codexsoft.codexcine.data.progress.WatchProgress
import dev.codexsoft.codexcine.data.progress.WatchProgressRules
import dev.codexsoft.codexcine.data.progress.WatchProgressStore
import dev.codexsoft.codexcine.domain.model.MediaDetails
import dev.codexsoft.codexcine.domain.model.MediaType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class PlayerFailure {
    SOURCE_TIMEOUT,
    PAGE_ERROR,
    PLAYBACK_ERROR,
    SSL_ERROR,
    RENDERER_CRASH,
    WEBVIEW_UNAVAILABLE,
    NO_SOURCE,
    SERVICE_UNREACHABLE,
}

sealed interface PlayerPhase {
    data object Loading : PlayerPhase

    /** The video is reporting its state, or in basic mode the page has loaded. */
    data object Active : PlayerPhase
    data class Failed(val failure: PlayerFailure) : PlayerPhase
}

data class WebPlayerUiState(
    val request: PlaybackRequest,
    val embed: StreamSource.WebEmbed,
    val phase: PlayerPhase = PlayerPhase.Loading,
    val year: String? = null,
    val episodeName: String? = null,
    val video: VideoState? = null,
    val isSlowConnection: Boolean = false,
    val seekPreviewSeconds: Double? = null,
    val resumePromptSeconds: Double? = null,
    val nextEpisode: EpisodeRef? = null,
    val nextCountdownSeconds: Int? = null,
    val isUnmuteHintVisible: Boolean = false,
    val keepScreenOn: Boolean = true,
)

/** What the ViewModel needs from the screen. Everything here is synchronous, so that a pause is sent before onPause returns. */
interface PlayerPage {
    fun load(url: String)
    fun send(command: PageCommand)
    fun stop()
}

/**
 * The player's state machine. The page only ever reports state; every decision is taken here, on
 * the main thread, which is where [dev.codexsoft.codexcine.ui.playback.web.PlayerBridge] delivers messages.
 */
class WebPlayerViewModel(
    request: PlaybackRequest,
    embed: StreamSource.WebEmbed,
    private var autoResume: Boolean,
    private val resolver: StreamSourceResolver,
    private val progress: WatchProgressStore,
    private val nextEpisodes: NextEpisodeResolver?,
    private val loadDetails: suspend (Int, MediaType) -> MediaDetails?,
    private val persistenceScope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    /** Ignores the saved position, which stays stored until the first new save replaces it. */
    private val startOver: Boolean = false,
) : ViewModel() {

    private enum class ResumeCheck { NOT_STARTED, RUNNING, DONE }

    private val _uiState = MutableStateFlow(WebPlayerUiState(request, embed))
    val uiState: StateFlow<WebPlayerUiState> = _uiState.asStateFlow()

    private val state get() = _uiState.value
    private val video get() = state.video

    private var page: PlayerPage? = null
    private var isEnhanced = true
    private var needsResolving = false

    private var watchdogJob: Job? = null
    private var slowConnectionJob: Job? = null
    private var keepScreenJob: Job? = null
    private var seekJob: Job? = null
    private var countdownJob: Job? = null
    private var neighboursJob: Job? = null
    private var pendingErrorJob: Job? = null
    private var switchJob: Job? = null

    private var resumeCheck = ResumeCheck.NOT_STARTED
    private var initialPlaySent = false
    private var pendingSeekTarget: Double? = null
    private var lastSaveMs = 0L
    private var hasEnded = false
    private var isNextDismissed = false
    private var positionAtError = 0.0

    private var posterUrl: String? = null
    private var backdropUrl: String? = null

    init {
        val mediaId = request.mediaId
        // The debug harness uses an id that does not exist on TMDB.
        if (mediaId > 0) {
            viewModelScope.launch {
                val details = loadDetails(mediaId, request.mediaType) ?: return@launch
                posterUrl = details.posterUrl
                backdropUrl = details.backdropUrl
                _uiState.update { it.copy(year = details.releaseYear) }
            }
        }
    }

    /** Called with each new WebView. A previous one is gone with its page, so loading starts over. */
    fun attachPage(page: PlayerPage, isEnhanced: Boolean) {
        this.page = page
        this.isEnhanced = isEnhanced
        if (state.phase !is PlayerPhase.Failed) startLoad()
    }

    fun detachPage() {
        page = null
        watchdogJob?.cancel()
    }

    // region Page lifecycle

    fun onPageFinished() {
        if (!isEnhanced) {
            _uiState.update { if (it.phase == PlayerPhase.Loading) it.copy(phase = PlayerPhase.Active) else it }
            return
        }
        if (state.phase == PlayerPhase.Loading) startWatchdog(WATCHDOG_AFTER_PAGE_MS)
    }

    fun onPageFailure(failure: PlayerFailure) {
        fail(failure)
    }

    fun retry() {
        if (needsResolving) resolveAndLoad() else startLoad()
    }

    private fun startLoad() {
        cancelPerLoadJobs()
        resumeCheck = ResumeCheck.NOT_STARTED
        initialPlaySent = false
        pendingSeekTarget = null
        hasEnded = false
        isNextDismissed = false
        lastSaveMs = clock()
        _uiState.update {
            it.copy(
                phase = PlayerPhase.Loading,
                video = null,
                isSlowConnection = false,
                seekPreviewSeconds = null,
                resumePromptSeconds = null,
                nextCountdownSeconds = null,
                isUnmuteHintVisible = false,
                keepScreenOn = true,
            )
        }
        loadNeighbours()
        // The page may hold back onPageFinished for a long time on a stuck ad script.
        if (isEnhanced) startWatchdog(WATCHDOG_BEFORE_PAGE_MS)
        page?.load(state.embed.url)
    }

    private fun startWatchdog(timeoutMs: Long) {
        watchdogJob?.cancel()
        watchdogJob = viewModelScope.launch {
            delay(timeoutMs)
            fail(PlayerFailure.SOURCE_TIMEOUT)
        }
    }

    private fun fail(failure: PlayerFailure) {
        Log.w(TAG, "Playback failed: $failure")
        saveProgress()
        cancelPerLoadJobs()
        _uiState.update {
            it.copy(
                phase = PlayerPhase.Failed(failure),
                video = null,
                seekPreviewSeconds = null,
                resumePromptSeconds = null,
                nextCountdownSeconds = null,
                isSlowConnection = false,
                keepScreenOn = false,
            )
        }
        page?.stop()
    }

    private fun cancelPerLoadJobs() {
        watchdogJob?.cancel()
        slowConnectionJob?.cancel()
        slowConnectionJob = null
        keepScreenJob?.cancel()
        keepScreenJob = null
        seekJob?.cancel()
        countdownJob?.cancel()
        countdownJob = null
        pendingErrorJob?.cancel()
        pendingErrorJob = null
    }

    // endregion

    // region Bridge messages

    fun onBridgeState(new: VideoState) {
        if (state.phase is PlayerPhase.Failed) return
        val previous = video
        watchdogJob?.cancel()
        _uiState.update { it.copy(phase = PlayerPhase.Active, video = new) }

        startOrResume(new)
        trackBuffering(new)
        trackKeepScreenOn(new)
        if (pendingErrorJob != null && new.currentTime != positionAtError) {
            pendingErrorJob?.cancel()
            pendingErrorJob = null
        }
        if (new.isEnded) onEnded() else saveIfDue(previous, new)
        updateNextCard(new)
    }

    fun onBridgeEvent(name: String) {
        when (name) {
            BridgeProtocol.EVENT_ENDED -> onEnded()
            BridgeProtocol.EVENT_MUTED_AUTOPLAY -> _uiState.update { it.copy(isUnmuteHintVisible = true) }
            BridgeProtocol.EVENT_ERROR -> onVideoError()
        }
    }

    /** An element error is usually fatal, but ad plugins cause harmless ones: only give up if the video stays stuck. */
    private fun onVideoError() {
        if (pendingErrorJob != null) return
        positionAtError = video?.currentTime ?: 0.0
        pendingErrorJob = viewModelScope.launch {
            delay(VIDEO_ERROR_GRACE_MS)
            fail(PlayerFailure.PLAYBACK_ERROR)
        }
    }

    private fun startOrResume(new: VideoState) {
        when (resumeCheck) {
            ResumeCheck.RUNNING -> Unit
            ResumeCheck.DONE -> if (state.resumePromptSeconds != null && !new.isPaused) {
                // The page's own autoplay can start after the prompt has appeared.
                page?.send(PageCommand(PageCommandName.PAUSE))
            }
            ResumeCheck.NOT_STARTED -> {
                resumeCheck = ResumeCheck.RUNNING
                viewModelScope.launch { checkResume() }
            }
        }
    }

    private suspend fun checkResume() {
        val request = state.request
        val saved = progress.get(progressKey(request))
            ?.takeUnless { startOver }
            ?.takeIf { it.positionSeconds >= MIN_RESUME_POSITION_S }
            ?.takeUnless { WatchProgressRules.isFinished(it.positionSeconds, it.durationSeconds) }
        resumeCheck = ResumeCheck.DONE
        val shouldAutoResume = autoResume
        autoResume = false
        when {
            saved == null -> ensurePlaying()
            shouldAutoResume -> {
                page?.send(PageCommand(PageCommandName.SEEK_TO, saved.positionSeconds))
                ensurePlaying()
            }
            else -> {
                page?.send(PageCommand(PageCommandName.PAUSE))
                _uiState.update { it.copy(resumePromptSeconds = saved.positionSeconds) }
            }
        }
    }

    /** The embed's own autoplay may have been refused or never attempted. */
    private fun ensurePlaying() {
        if (initialPlaySent) return
        initialPlaySent = true
        if (video?.isPaused != false) page?.send(PageCommand(PageCommandName.PLAY))
    }

    private fun trackBuffering(new: VideoState) {
        if (new.isBuffering) {
            if (slowConnectionJob == null) {
                slowConnectionJob = viewModelScope.launch {
                    delay(SLOW_CONNECTION_MS)
                    _uiState.update { it.copy(isSlowConnection = true) }
                }
            }
        } else {
            slowConnectionJob?.cancel()
            slowConnectionJob = null
            if (state.isSlowConnection) _uiState.update { it.copy(isSlowConnection = false) }
        }
    }

    private fun trackKeepScreenOn(new: VideoState) {
        if (new.isPaused || new.isEnded) {
            if (keepScreenJob == null) {
                keepScreenJob = viewModelScope.launch {
                    delay(KEEP_SCREEN_WHILE_PAUSED_MS)
                    _uiState.update { it.copy(keepScreenOn = false) }
                }
            }
        } else {
            keepScreenJob?.cancel()
            keepScreenJob = null
            if (!state.keepScreenOn) _uiState.update { it.copy(keepScreenOn = true) }
        }
    }

    private fun onEnded() {
        if (hasEnded) return
        hasEnded = true
        saveProgress(isEnded = true)
        video?.let(::updateNextCard)
    }

    // endregion

    // region Commands

    fun togglePlayPause() {
        if (video != null) page?.send(PageCommand(PageCommandName.TOGGLE))
    }

    fun play() {
        if (video != null) page?.send(PageCommand(PageCommandName.PLAY))
    }

    fun pause() {
        if (video != null) page?.send(PageCommand(PageCommandName.PAUSE))
    }

    fun seekBy(seconds: Int) {
        val current = video ?: return
        seekTo((pendingSeekTarget ?: current.currentTime) + seconds)
    }

    fun jumpToPercent(percent: Int) {
        val current = video ?: return
        seekTo(current.duration * percent / 100.0)
    }

    /** Shows the target right away, and sends one seek once the keys have stopped. */
    private fun seekTo(target: Double) {
        val current = video ?: return
        val clamped = target.coerceIn(0.0, (current.duration - 1).coerceAtLeast(0.0))
        pendingSeekTarget = clamped
        _uiState.update { it.copy(seekPreviewSeconds = clamped) }
        seekJob?.cancel()
        seekJob = viewModelScope.launch {
            delay(SEEK_DEBOUNCE_MS)
            pendingSeekTarget = null
            _uiState.update { it.copy(seekPreviewSeconds = null) }
            page?.send(PageCommand(PageCommandName.SEEK_TO, clamped))
        }
    }

    fun setRate(rate: Double) {
        if (rate in BridgeProtocol.ALLOWED_RATES) page?.send(PageCommand(PageCommandName.RATE, rate))
    }

    fun toggleMute() {
        val current = video ?: return
        if (current.isMuted) unmute() else page?.send(PageCommand(PageCommandName.MUTE))
    }

    fun unmute() {
        page?.send(PageCommand(PageCommandName.UNMUTE))
        _uiState.update { it.copy(isUnmuteHintVisible = false) }
    }

    fun acceptResume() {
        val position = state.resumePromptSeconds ?: return
        closeResumePrompt(position)
    }

    fun declineResume() {
        if (state.resumePromptSeconds != null) closeResumePrompt(0.0)
    }

    private fun closeResumePrompt(position: Double) {
        _uiState.update { it.copy(resumePromptSeconds = null) }
        page?.send(PageCommand(PageCommandName.SEEK_TO, position))
        page?.send(PageCommand(PageCommandName.PLAY))
        initialPlaySent = true
    }

    fun onHostPaused() {
        page?.send(PageCommand(PageCommandName.PAUSE))
        saveProgress()
    }

    // endregion

    // region Episodes

    private fun loadNeighbours() {
        neighboursJob?.cancel()
        val request = state.request
        val season = request.seasonNumber
        val episode = request.episodeNumber
        val resolver = nextEpisodes
        _uiState.update { it.copy(nextEpisode = null, episodeName = null) }
        if (resolver == null || request.mediaType != MediaType.TV || season == null || episode == null) return
        neighboursJob = viewModelScope.launch {
            val name = resolver.episodeName(request.mediaId, season, episode)
            val next = resolver.next(request.mediaId, season, episode)
            _uiState.update { it.copy(episodeName = name, nextEpisode = next) }
        }
    }

    private fun updateNextCard(new: VideoState) {
        if (state.nextEpisode == null || isNextDismissed) return
        val isNearEnd = new.isEnded || new.duration - new.currentTime <= NEXT_CARD_LEAD_S
        if (isNearEnd && countdownJob == null) {
            startCountdown()
        } else if (!isNearEnd && countdownJob != null) {
            // The user sought back into the episode.
            countdownJob?.cancel()
            countdownJob = null
            _uiState.update { it.copy(nextCountdownSeconds = null) }
        }
    }

    private fun startCountdown() {
        countdownJob = viewModelScope.launch {
            var remaining = NEXT_COUNTDOWN_S
            _uiState.update { it.copy(nextCountdownSeconds = remaining) }
            while (remaining > 0) {
                delay(1_000)
                val current = video
                if (current == null || !current.isPaused || current.isEnded) {
                    remaining--
                    _uiState.update { it.copy(nextCountdownSeconds = remaining) }
                }
            }
            playNextNow()
        }
    }

    /** From the card: the episode is considered watched, even if a short one is not at 93 % yet. */
    fun playNextNow() {
        val next = state.nextEpisode ?: return
        saveProgress(isEnded = true)
        switchTo(next)
    }

    fun cancelNext() {
        isNextDismissed = true
        countdownJob?.cancel()
        countdownJob = null
        _uiState.update { it.copy(nextCountdownSeconds = null) }
    }

    fun nextEpisode() {
        val next = state.nextEpisode ?: return
        saveProgress()
        switchTo(next)
    }

    fun previousOrRestart() {
        val current = video ?: return
        val request = state.request
        val resolver = nextEpisodes
        val season = request.seasonNumber
        val episode = request.episodeNumber
        val canGoBack = resolver != null && request.mediaType == MediaType.TV && season != null && episode != null
        if (current.currentTime > RESTART_THRESHOLD_S || !canGoBack) {
            seekTo(0.0)
            return
        }
        viewModelScope.launch {
            val previous = resolver?.previous(request.mediaId, season ?: return@launch, episode ?: return@launch)
            if (previous != null) {
                saveProgress()
                switchTo(previous)
            } else {
                seekTo(0.0)
            }
        }
    }

    private fun switchTo(target: EpisodeRef) {
        cancelPerLoadJobs()
        _uiState.update {
            it.copy(
                request = it.request.copy(seasonNumber = target.season, episodeNumber = target.episode),
                video = null,
                phase = PlayerPhase.Loading,
                nextCountdownSeconds = null,
            )
        }
        resolveAndLoad()
    }

    private fun resolveAndLoad() {
        needsResolving = true
        switchJob?.cancel()
        _uiState.update { it.copy(phase = PlayerPhase.Loading) }
        switchJob = viewModelScope.launch {
            try {
                val source = resolver.resolve(state.request)
                if (source is StreamSource.WebEmbed) {
                    needsResolving = false
                    _uiState.update { it.copy(embed = source) }
                    startLoad()
                } else {
                    fail(PlayerFailure.NO_SOURCE)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: StreamServiceException) {
                Log.w(TAG, "Provider unreachable", e)
                fail(PlayerFailure.SERVICE_UNREACHABLE)
            }
        }
    }

    // endregion

    // region Progress

    private fun saveIfDue(previous: VideoState?, new: VideoState) {
        val wasJustPaused = previous != null && !previous.isPaused && new.isPaused
        val isDue = !new.isPaused && clock() - lastSaveMs >= SAVE_INTERVAL_MS
        if (wasJustPaused || isDue) saveProgress()
    }

    /** Writes outside viewModelScope, so that saving survives the player closing. Returns the write, or null if there is nothing to save. */
    fun saveProgress(isEnded: Boolean = false): Job? {
        val current = video ?: return null
        // A pending prompt means the position is still the page's start, not where the viewer is.
        if (resumeCheck != ResumeCheck.DONE || state.resumePromptSeconds != null) return null
        if (current.currentTime <= 0.0 && !isEnded) return null
        lastSaveMs = clock()
        val entry = toEntry(current)
        return persistenceScope.launch {
            try {
                progress.save(entry, isEnded)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Could not save progress", e)
            }
        }
    }

    private fun toEntry(current: VideoState): WatchProgress {
        val request = state.request
        return WatchProgress(
            mediaType = request.mediaType.value,
            tmdbId = request.mediaId,
            season = request.seasonNumber ?: 0,
            episode = request.episodeNumber ?: 0,
            positionSeconds = current.currentTime,
            durationSeconds = current.duration,
            updatedAtMs = clock(),
            title = request.title,
            posterUrl = posterUrl,
            backdropUrl = backdropUrl,
        )
    }

    private fun progressKey(request: PlaybackRequest) = WatchProgressRules.key(
        request.mediaType.value,
        request.mediaId,
        request.seasonNumber ?: 0,
        request.episodeNumber ?: 0,
    )

    // endregion

    private companion object {
        const val TAG = "WebPlayerViewModel"
        const val WATCHDOG_BEFORE_PAGE_MS = 60_000L
        const val WATCHDOG_AFTER_PAGE_MS = 25_000L
        const val SLOW_CONNECTION_MS = 30_000L
        const val KEEP_SCREEN_WHILE_PAUSED_MS = 10L * 60L * 1000L
        const val SEEK_DEBOUNCE_MS = 600L
        const val SAVE_INTERVAL_MS = 10_000L
        const val MIN_RESUME_POSITION_S = 60.0
        const val NEXT_CARD_LEAD_S = 30.0
        // The ring in view_player_overlay.xml (player_next_ring, android:max) uses the same value.
        const val NEXT_COUNTDOWN_S = 5
        const val RESTART_THRESHOLD_S = 10.0
        const val VIDEO_ERROR_GRACE_MS = 8_000L
    }
}
