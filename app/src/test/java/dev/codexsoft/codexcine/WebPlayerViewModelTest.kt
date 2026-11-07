package dev.codexsoft.codexcine

import dev.codexsoft.codexcine.data.playback.PlaybackRequest
import dev.codexsoft.codexcine.data.playback.StreamServiceException
import dev.codexsoft.codexcine.data.playback.StreamSource
import dev.codexsoft.codexcine.data.playback.StreamSourceResolver
import dev.codexsoft.codexcine.data.progress.WatchProgress
import dev.codexsoft.codexcine.data.progress.WatchProgressRules
import dev.codexsoft.codexcine.data.progress.WatchProgressStore
import dev.codexsoft.codexcine.domain.model.Episode
import dev.codexsoft.codexcine.domain.model.MediaType
import dev.codexsoft.codexcine.domain.model.Season
import dev.codexsoft.codexcine.ui.playback.web.BridgeProtocol
import dev.codexsoft.codexcine.ui.playback.web.EpisodeCatalog
import dev.codexsoft.codexcine.ui.playback.web.NextEpisodeResolver
import dev.codexsoft.codexcine.ui.playback.web.PageCommand
import dev.codexsoft.codexcine.ui.playback.web.PageCommandName
import dev.codexsoft.codexcine.ui.playback.web.PlayerFailure
import dev.codexsoft.codexcine.ui.playback.web.PlayerPage
import dev.codexsoft.codexcine.ui.playback.web.PlayerPhase
import dev.codexsoft.codexcine.ui.playback.web.VideoState
import dev.codexsoft.codexcine.ui.playback.web.WebPlayerViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class WebPlayerViewModelTest {

    private lateinit var dispatcher: TestDispatcher

    private val movie = PlaybackRequest(550, MediaType.MOVIE, "Fight Club")
    private val episode = PlaybackRequest(1399, MediaType.TV, "GoT", seasonNumber = 1, episodeNumber = 1)
    private val embed = StreamSource.WebEmbed("https://vidzy.org/movie/550", listOf("vf"), "Vidzy")

    private class FakePage : PlayerPage {
        val loaded = mutableListOf<String>()
        val sent = mutableListOf<PageCommand>()
        var stops = 0

        override fun load(url: String) {
            loaded += url
        }

        override fun send(command: PageCommand) {
            sent += command
        }

        override fun stop() {
            stops++
        }
    }

    private class FakeStore(val entries: MutableMap<String, WatchProgress> = mutableMapOf()) : WatchProgressStore {
        val ended = mutableListOf<String>()

        override suspend fun get(key: String) = entries[key]

        override suspend fun save(entry: WatchProgress, isEnded: Boolean) {
            if (isEnded) ended += entry.key
            entries[entry.key] = entry
        }

        override suspend fun remove(key: String) {
            entries.remove(key)
        }
    }

    private val page = FakePage()
    private val store = FakeStore()
    private var now = 1_000_000L

    private fun video(
        time: Double = 5.0,
        duration: Double = 1000.0,
        paused: Boolean = false,
        buffering: Boolean = false,
        ended: Boolean = false,
    ) = VideoState(time, duration, paused, buffering, 1.0, 1.0, false, ended, 0.0)

    private fun viewModel(
        scope: CoroutineScope,
        request: PlaybackRequest = movie,
        autoResume: Boolean = false,
        resolver: StreamSourceResolver = object : StreamSourceResolver {
            override suspend fun resolve(request: PlaybackRequest) =
                embed.copy(url = "https://vidzy.org/serie/${request.seasonNumber}/${request.episodeNumber}")
        },
        next: NextEpisodeResolver? = null,
    ) = WebPlayerViewModel(
        request = request,
        embed = embed,
        autoResume = autoResume,
        resolver = resolver,
        progress = store,
        nextEpisodes = next,
        loadDetails = { _, _ -> null },
        persistenceScope = scope,
        clock = { now },
    ).also { it.attachPage(page, isEnhanced = true) }

    private fun twoEpisodes() = NextEpisodeResolver(
        object : EpisodeCatalog {
            override suspend fun seasons(showId: Int) = listOf(Season(1, 1, "S1", 2, null))

            override suspend fun episodes(showId: Int, season: Int) = listOf(
                Episode(1, 1, 1, "Un", null, null, "2020-01-01", 40),
                Episode(2, 2, 1, "Deux", null, null, "2020-01-08", 40),
            )
        },
        today = { LocalDate.of(2026, 11, 7) },
    )

    @Before
    fun setUp() {
        dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `loads the embed when a page is attached`() = runTest(dispatcher) {
        viewModel(backgroundScope)
        assertEquals(listOf(embed.url), page.loaded)
    }

    @Test
    fun `watchdog fires 25 s after the page finished when no video reported`() = runTest(dispatcher) {
        val vm = viewModel(backgroundScope)
        vm.onPageFinished()
        advanceTimeBy(24_999)
        assertEquals(PlayerPhase.Loading, vm.uiState.value.phase)
        advanceTimeBy(2)
        assertEquals(PlayerPhase.Failed(PlayerFailure.SOURCE_TIMEOUT), vm.uiState.value.phase)
        assertEquals(1, page.stops)
    }

    @Test
    fun `watchdog also covers a page that never finishes`() = runTest(dispatcher) {
        val vm = viewModel(backgroundScope)
        advanceTimeBy(60_001)
        assertEquals(PlayerPhase.Failed(PlayerFailure.SOURCE_TIMEOUT), vm.uiState.value.phase)
    }

    @Test
    fun `a video state stops the watchdog`() = runTest(dispatcher) {
        val vm = viewModel(backgroundScope)
        vm.onPageFinished()
        vm.onBridgeState(video())
        advanceTimeBy(120_000)
        assertEquals(PlayerPhase.Active, vm.uiState.value.phase)
    }

    @Test
    fun `retry reloads the same url after a failure`() = runTest(dispatcher) {
        val vm = viewModel(backgroundScope)
        vm.onPageFailure(PlayerFailure.PAGE_ERROR)
        vm.retry()
        assertEquals(listOf(embed.url, embed.url), page.loaded)
        assertEquals(PlayerPhase.Loading, vm.uiState.value.phase)
    }

    @Test
    fun `slow connection banner after 30 s of buffering, cleared when it recovers`() = runTest(dispatcher) {
        val vm = viewModel(backgroundScope)
        vm.onBridgeState(video(buffering = true))
        advanceTimeBy(29_999)
        assertFalse(vm.uiState.value.isSlowConnection)
        advanceTimeBy(2)
        assertTrue(vm.uiState.value.isSlowConnection)
        vm.onBridgeState(video(buffering = false))
        assertFalse(vm.uiState.value.isSlowConnection)
    }

    @Test
    fun `seeks are debounced into one command with the accumulated target`() = runTest(dispatcher) {
        val vm = viewModel(backgroundScope)
        vm.onBridgeState(video(time = 100.0))
        runCurrent()
        page.sent.clear()

        vm.seekBy(10)
        vm.seekBy(10)
        vm.seekBy(30)
        assertEquals(150.0, vm.uiState.value.seekPreviewSeconds)
        assertTrue(page.sent.none { it.name == PageCommandName.SEEK_TO })

        advanceTimeBy(601)
        assertEquals(listOf(PageCommand(PageCommandName.SEEK_TO, 150.0)), page.sent.filter { it.name == PageCommandName.SEEK_TO })
        assertNull(vm.uiState.value.seekPreviewSeconds)
    }

    @Test
    fun `seek target is clamped to the video`() = runTest(dispatcher) {
        val vm = viewModel(backgroundScope)
        vm.onBridgeState(video(time = 5.0, duration = 100.0))
        vm.seekBy(-60)
        assertEquals(0.0, vm.uiState.value.seekPreviewSeconds)
        vm.seekBy(5000)
        assertEquals(99.0, vm.uiState.value.seekPreviewSeconds)
    }

    @Test
    fun `digit jumps to a percentage`() = runTest(dispatcher) {
        val vm = viewModel(backgroundScope)
        vm.onBridgeState(video(duration = 1000.0))
        vm.jumpToPercent(40)
        assertEquals(400.0, vm.uiState.value.seekPreviewSeconds)
    }

    @Test
    fun `no saved position starts playing a paused video`() = runTest(dispatcher) {
        val vm = viewModel(backgroundScope)
        vm.onBridgeState(video(paused = true))
        runCurrent()
        assertNull(vm.uiState.value.resumePromptSeconds)
        assertTrue(PageCommand(PageCommandName.PLAY) in page.sent)
    }

    private fun saved(position: Double, request: PlaybackRequest = movie) = WatchProgress(
        mediaType = request.mediaType.value,
        tmdbId = request.mediaId,
        season = request.seasonNumber ?: 0,
        episode = request.episodeNumber ?: 0,
        positionSeconds = position,
        durationSeconds = 1000.0,
        updatedAtMs = 0L,
        title = request.title,
    )

    @Test
    fun `saved position shows the prompt and pauses`() = runTest(dispatcher) {
        store.entries["movie:550:0:0"] = saved(500.0)
        val vm = viewModel(backgroundScope)
        vm.onBridgeState(video())
        runCurrent()
        assertEquals(500.0, vm.uiState.value.resumePromptSeconds)
        assertTrue(PageCommand(PageCommandName.PAUSE) in page.sent)

        vm.acceptResume()
        assertNull(vm.uiState.value.resumePromptSeconds)
        assertTrue(PageCommand(PageCommandName.SEEK_TO, 500.0) in page.sent)
        assertEquals(PageCommand(PageCommandName.PLAY), page.sent.last())
    }

    @Test
    fun `declining the prompt starts from the beginning`() = runTest(dispatcher) {
        store.entries["movie:550:0:0"] = saved(500.0)
        val vm = viewModel(backgroundScope)
        vm.onBridgeState(video())
        runCurrent()
        vm.declineResume()
        assertTrue(PageCommand(PageCommandName.SEEK_TO, 0.0) in page.sent)
    }

    @Test
    fun `positions under a minute or finished do not prompt`() = runTest(dispatcher) {
        store.entries["movie:550:0:0"] = saved(59.0)
        val early = viewModel(backgroundScope)
        early.onBridgeState(video())
        runCurrent()
        assertNull(early.uiState.value.resumePromptSeconds)

        store.entries["movie:550:0:0"] = saved(950.0)
        val finished = viewModel(backgroundScope)
        finished.onBridgeState(video())
        runCurrent()
        assertNull(finished.uiState.value.resumePromptSeconds)
    }

    @Test
    fun `continue watching resumes without asking`() = runTest(dispatcher) {
        store.entries["movie:550:0:0"] = saved(500.0)
        val vm = viewModel(backgroundScope, autoResume = true)
        vm.onBridgeState(video())
        runCurrent()
        assertNull(vm.uiState.value.resumePromptSeconds)
        assertTrue(PageCommand(PageCommandName.SEEK_TO, 500.0) in page.sent)
    }

    @Test
    fun `a pending prompt never overwrites the saved position`() = runTest(dispatcher) {
        store.entries["movie:550:0:0"] = saved(500.0)
        val vm = viewModel(backgroundScope)
        vm.onBridgeState(video(time = 3.0))
        runCurrent()
        assertNull(vm.saveProgress())
        now += 60_000
        vm.onBridgeState(video(time = 3.0))
        runCurrent()
        assertEquals(500.0, store.entries.getValue("movie:550:0:0").positionSeconds, 0.0)
    }

    @Test
    fun `progress is saved every 10 s while playing and on pause`() = runTest(dispatcher) {
        val vm = viewModel(backgroundScope)
        vm.onBridgeState(video(time = 1.0))
        runCurrent()
        assertTrue(store.entries.isEmpty())

        now += 10_000
        vm.onBridgeState(video(time = 11.0))
        runCurrent()
        assertEquals(11.0, store.entries.getValue("movie:550:0:0").positionSeconds, 0.0)

        now += 1_000
        vm.onBridgeState(video(time = 12.0, paused = true))
        runCurrent()
        assertEquals(12.0, store.entries.getValue("movie:550:0:0").positionSeconds, 0.0)
    }

    @Test
    fun `ended is saved as finished, once`() = runTest(dispatcher) {
        val vm = viewModel(backgroundScope)
        vm.onBridgeState(video(time = 1.0))
        runCurrent()
        vm.onBridgeEvent(BridgeProtocol.EVENT_ENDED)
        vm.onBridgeState(video(time = 1000.0, ended = true))
        runCurrent()
        assertEquals(listOf(WatchProgressRules.key("movie", 550, 0, 0)), store.ended)
    }

    @Test
    fun `host pause sends the pause command and saves`() = runTest(dispatcher) {
        val vm = viewModel(backgroundScope)
        vm.onBridgeState(video(time = 42.0))
        runCurrent()
        vm.onHostPaused()
        runCurrent()
        assertEquals(PageCommand(PageCommandName.PAUSE), page.sent.last())
        assertEquals(42.0, store.entries.getValue("movie:550:0:0").positionSeconds, 0.0)
    }

    @Test
    fun `screen stays on for 10 minutes of pause`() = runTest(dispatcher) {
        val vm = viewModel(backgroundScope)
        vm.onBridgeState(video(paused = true))
        advanceTimeBy(599_999)
        assertTrue(vm.uiState.value.keepScreenOn)
        advanceTimeBy(2)
        assertFalse(vm.uiState.value.keepScreenOn)
        vm.onBridgeState(video(paused = false))
        assertTrue(vm.uiState.value.keepScreenOn)
    }

    @Test
    fun `a video error is fatal only if the video stays stuck`() = runTest(dispatcher) {
        val vm = viewModel(backgroundScope)
        vm.onBridgeState(video(time = 10.0))
        vm.onBridgeEvent(BridgeProtocol.EVENT_ERROR)
        vm.onBridgeState(video(time = 11.0))
        advanceTimeBy(10_000)
        assertEquals(PlayerPhase.Active, vm.uiState.value.phase)

        vm.onBridgeEvent(BridgeProtocol.EVENT_ERROR)
        advanceTimeBy(8_001)
        assertEquals(PlayerPhase.Failed(PlayerFailure.PLAYBACK_ERROR), vm.uiState.value.phase)
    }

    @Test
    fun `next card counts down near the end, then loads the next episode in place`() = runTest(dispatcher) {
        val vm = viewModel(backgroundScope, request = episode, next = twoEpisodes())
        runCurrent()
        assertEquals(1, vm.uiState.value.nextEpisode?.season)
        assertEquals(2, vm.uiState.value.nextEpisode?.episode)
        assertEquals("Un", vm.uiState.value.episodeName)

        vm.onBridgeState(video(time = 900.0))
        runCurrent()
        assertNull(vm.uiState.value.nextCountdownSeconds)

        vm.onBridgeState(video(time = 975.0))
        runCurrent()
        assertEquals(5, vm.uiState.value.nextCountdownSeconds)

        advanceTimeBy(4_001)
        runCurrent()
        assertEquals(1, vm.uiState.value.nextCountdownSeconds)
        assertEquals(1, vm.uiState.value.request.episodeNumber)

        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(2, vm.uiState.value.request.episodeNumber)
        assertEquals("https://vidzy.org/serie/1/2", page.loaded.last())
        assertNull(vm.uiState.value.nextCountdownSeconds)
        assertEquals(PlayerPhase.Loading, vm.uiState.value.phase)
    }

    @Test
    fun `cancelling the card keeps it away for this episode`() = runTest(dispatcher) {
        val vm = viewModel(backgroundScope, request = episode, next = twoEpisodes())
        runCurrent()
        vm.onBridgeState(video(time = 975.0))
        runCurrent()
        vm.cancelNext()
        assertNull(vm.uiState.value.nextCountdownSeconds)
        vm.onBridgeState(video(time = 980.0))
        runCurrent()
        assertNull(vm.uiState.value.nextCountdownSeconds)
        advanceTimeBy(20_000)
        assertEquals(1, vm.uiState.value.request.episodeNumber)
    }

    @Test
    fun `seeking back into the episode removes the card`() = runTest(dispatcher) {
        val vm = viewModel(backgroundScope, request = episode, next = twoEpisodes())
        runCurrent()
        vm.onBridgeState(video(time = 975.0))
        runCurrent()
        assertNotNull(vm.uiState.value.nextCountdownSeconds)
        vm.onBridgeState(video(time = 100.0))
        assertNull(vm.uiState.value.nextCountdownSeconds)
    }

    @Test
    fun `previous restarts after 10 s, and goes back before that`() = runTest(dispatcher) {
        store.entries.clear()
        val vm = viewModel(
            backgroundScope,
            request = episode.copy(episodeNumber = 2),
            next = twoEpisodes(),
        )
        runCurrent()
        vm.onBridgeState(video(time = 11.0))
        runCurrent()
        vm.previousOrRestart()
        assertEquals(0.0, vm.uiState.value.seekPreviewSeconds)
        advanceTimeBy(601)

        vm.onBridgeState(video(time = 3.0))
        runCurrent()
        vm.previousOrRestart()
        runCurrent()
        assertEquals(1, vm.uiState.value.request.episodeNumber)
    }

    @Test
    fun `an unreachable provider when switching episode can be retried`() = runTest(dispatcher) {
        var isOnline = false
        val vm = viewModel(
            backgroundScope,
            request = episode,
            next = twoEpisodes(),
            resolver = object : StreamSourceResolver {
                override suspend fun resolve(request: PlaybackRequest): StreamSource {
                    if (!isOnline) throw StreamServiceException("offline")
                    return embed.copy(url = "https://vidzy.org/ok")
                }
            },
        )
        runCurrent()
        vm.onBridgeState(video(time = 100.0))
        runCurrent()
        vm.nextEpisode()
        runCurrent()
        assertEquals(PlayerPhase.Failed(PlayerFailure.SERVICE_UNREACHABLE), vm.uiState.value.phase)

        isOnline = true
        vm.retry()
        runCurrent()
        assertEquals("https://vidzy.org/ok", page.loaded.last())
    }

    @Test
    fun `basic mode has no watchdog and is active once the page finished`() = runTest(dispatcher) {
        val vm = WebPlayerViewModel(
            request = movie,
            embed = embed,
            autoResume = false,
            resolver = object : StreamSourceResolver {
                override suspend fun resolve(request: PlaybackRequest) = embed
            },
            progress = store,
            nextEpisodes = null,
            loadDetails = { _, _ -> null },
            persistenceScope = backgroundScope,
            clock = { now },
        )
        vm.attachPage(page, isEnhanced = false)
        vm.onPageFinished()
        assertEquals(PlayerPhase.Active, vm.uiState.value.phase)
        advanceTimeBy(300_000)
        assertEquals(PlayerPhase.Active, vm.uiState.value.phase)
    }
}
