package dev.codexsoft.codexcine

import android.view.KeyEvent
import dev.codexsoft.codexcine.data.progress.WatchProgress
import dev.codexsoft.codexcine.data.progress.WatchProgressRules
import dev.codexsoft.codexcine.domain.model.Episode
import dev.codexsoft.codexcine.domain.model.Season
import dev.codexsoft.codexcine.ui.playback.web.ActiveFrameTracker
import dev.codexsoft.codexcine.ui.playback.web.AdHostBlocklist
import dev.codexsoft.codexcine.ui.playback.web.BridgeMessage
import dev.codexsoft.codexcine.ui.playback.web.BridgeProtocol
import dev.codexsoft.codexcine.ui.playback.web.EpisodeCatalog
import dev.codexsoft.codexcine.ui.playback.web.EpisodeRef
import dev.codexsoft.codexcine.ui.playback.web.KeyContext
import dev.codexsoft.codexcine.ui.playback.web.MainFrameHostPolicy
import dev.codexsoft.codexcine.ui.playback.web.NextEpisodeResolver
import dev.codexsoft.codexcine.ui.playback.web.PageCommand
import dev.codexsoft.codexcine.ui.playback.web.PageCommandName
import dev.codexsoft.codexcine.ui.playback.web.PlayerCommand
import dev.codexsoft.codexcine.ui.playback.web.RemoteKeyMapper
import dev.codexsoft.codexcine.ui.playback.web.RevealFocus
import dev.codexsoft.codexcine.ui.playback.web.SeekAccelerator
import dev.codexsoft.codexcine.ui.playback.web.VideoState
import dev.codexsoft.codexcine.ui.playback.web.isAllowedSubframeUrl
import dev.codexsoft.codexcine.ui.playback.web.isFatalSslError
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RemoteKeyMapperTest {

    private val hidden = KeyContext(isOverlayVisible = false, isSeries = true)
    private val visible = KeyContext(isOverlayVisible = true, isSeries = true)

    private fun map(key: Int, context: KeyContext, repeat: Int = 0, time: Long = 0L) =
        RemoteKeyMapper().map(key, repeat, time, context)

    @Test
    fun `ok toggles when hidden, activates the focused button when visible`() {
        listOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER).forEach {
            assertEquals(PlayerCommand.TogglePlayPause, map(it, hidden))
            assertEquals(PlayerCommand.PassThrough, map(it, visible))
        }
    }

    @Test
    fun `ok restores the sound while the muted hint is up`() {
        assertEquals(PlayerCommand.Unmute, map(KeyEvent.KEYCODE_DPAD_CENTER, hidden.copy(isUnmuteHintVisible = true)))
    }

    @Test
    fun `media keys act in both states`() {
        listOf(hidden, visible).forEach {
            assertEquals(PlayerCommand.TogglePlayPause, map(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, it))
            assertEquals(PlayerCommand.Play, map(KeyEvent.KEYCODE_MEDIA_PLAY, it))
            assertEquals(PlayerCommand.Pause, map(KeyEvent.KEYCODE_MEDIA_PAUSE, it))
            assertEquals(PlayerCommand.SeekBy(-30), map(KeyEvent.KEYCODE_MEDIA_REWIND, it))
            assertEquals(PlayerCommand.SeekBy(30), map(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, it))
            assertEquals(PlayerCommand.NextEpisode, map(KeyEvent.KEYCODE_MEDIA_NEXT, it))
            assertEquals(PlayerCommand.PreviousOrRestart, map(KeyEvent.KEYCODE_MEDIA_PREVIOUS, it))
        }
    }

    @Test
    fun `next is not ours on a movie`() {
        assertEquals(PlayerCommand.PassThrough, map(KeyEvent.KEYCODE_MEDIA_NEXT, hidden.copy(isSeries = false)))
    }

    @Test
    fun `left and right seek when hidden or on the progress bar, and move focus otherwise`() {
        assertEquals(PlayerCommand.SeekBy(-10), map(KeyEvent.KEYCODE_DPAD_LEFT, hidden))
        assertEquals(PlayerCommand.SeekBy(10), map(KeyEvent.KEYCODE_DPAD_RIGHT, hidden))
        assertEquals(PlayerCommand.PassThrough, map(KeyEvent.KEYCODE_DPAD_LEFT, visible))
        assertEquals(PlayerCommand.PassThrough, map(KeyEvent.KEYCODE_DPAD_RIGHT, visible))
        val onBar = visible.copy(isProgressFocused = true)
        assertEquals(PlayerCommand.SeekBy(-10), map(KeyEvent.KEYCODE_DPAD_LEFT, onBar))
        assertEquals(PlayerCommand.SeekBy(10), map(KeyEvent.KEYCODE_DPAD_RIGHT, onBar))
    }

    @Test
    fun `up and down reveal with the right focus, and navigate when visible`() {
        assertEquals(PlayerCommand.Reveal(RevealFocus.PROGRESS), map(KeyEvent.KEYCODE_DPAD_UP, hidden))
        assertEquals(PlayerCommand.Reveal(RevealFocus.PLAY_PAUSE), map(KeyEvent.KEYCODE_DPAD_DOWN, hidden))
        assertEquals(PlayerCommand.PassThrough, map(KeyEvent.KEYCODE_DPAD_UP, visible))
        assertEquals(PlayerCommand.PassThrough, map(KeyEvent.KEYCODE_DPAD_DOWN, visible))
    }

    @Test
    fun `digits jump to a percentage of the duration`() {
        (0..9).forEach { digit ->
            val expected = PlayerCommand.JumpToPercent(digit * 10)
            assertEquals(expected, map(KeyEvent.KEYCODE_0 + digit, hidden))
            assertEquals(expected, map(KeyEvent.KEYCODE_0 + digit, visible))
        }
    }

    @Test
    fun `system volume keys are never intercepted`() {
        listOf(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE, KeyEvent.KEYCODE_M)
            .forEach {
                assertEquals(PlayerCommand.PassThrough, map(it, hidden))
                assertEquals(PlayerCommand.PassThrough, map(it, visible))
            }
    }

    @Test
    fun `menu opens the speed selector in both states`() {
        assertEquals(PlayerCommand.OpenSpeedSelector, map(KeyEvent.KEYCODE_MENU, hidden))
        assertEquals(PlayerCommand.OpenSpeedSelector, map(KeyEvent.KEYCODE_MENU, visible))
    }

    @Test
    fun `back hides the overlay, or asks before leaving`() {
        assertEquals(PlayerCommand.HideOverlay, map(KeyEvent.KEYCODE_BACK, visible))
        assertEquals(PlayerCommand.ConfirmExit, map(KeyEvent.KEYCODE_BACK, hidden))
    }

    @Test
    fun `seek accelerates 10 then 30 then 60 seconds`() {
        val accelerator = SeekAccelerator()
        val steps = (0 until 10).map { accelerator.next(1, 0, it * 200L) }
        assertEquals(listOf(10, 10, 10, 30, 30, 30, 30, 30, 60, 60), steps)
    }

    @Test
    fun `seek acceleration resets after a second without a seek key`() {
        val accelerator = SeekAccelerator()
        repeat(5) { accelerator.next(1, 0, it * 100L) }
        assertEquals(10, accelerator.next(1, 0, 400L + 1_001L))
    }

    @Test
    fun `seek acceleration resets when the direction changes`() {
        val accelerator = SeekAccelerator()
        repeat(5) { accelerator.next(1, 0, it * 100L) }
        assertEquals(-10, accelerator.next(-1, 0, 500L))
    }

    @Test
    fun `a long press advances on every third repeat only`() {
        val accelerator = SeekAccelerator()
        assertEquals(listOf(10, 0, 0, 10, 0, 0, 10), (0..6).map { accelerator.next(1, it, it * 50L) })
    }
}

class BridgeProtocolTest {

    private val nonce = "secret"

    private fun state(
        n: String = nonce,
        ct: String = "12.5",
        d: String = "6120",
    ) = """{"n":"$n","t":"state","ct":$ct,"d":$d,"p":false,"b":false,"r":1,"v":1,"m":false,"e":false,"bf":20}"""

    @Test
    fun `valid messages are parsed`() {
        assertEquals(BridgeMessage.Ready, BridgeProtocol.parse("""{"n":"secret","t":"ready"}""", nonce))
        assertEquals(
            BridgeMessage.Event("playing"),
            BridgeProtocol.parse("""{"n":"secret","t":"event","name":"playing"}""", nonce),
        )
        val message = BridgeProtocol.parse(state(), nonce) as BridgeMessage.State
        assertEquals(12.5, message.state.currentTime, 0.0)
        assertEquals(6120.0, message.state.duration, 0.0)
        assertEquals(20.0, message.state.bufferedEnd, 0.0)
    }

    @Test
    fun `a wrong or missing nonce is dropped`() {
        assertNull(BridgeProtocol.parse(state(n = "guess"), nonce))
        assertNull(BridgeProtocol.parse("""{"t":"ready"}""", nonce))
    }

    @Test
    fun `non finite and negative numbers are dropped`() {
        assertNull(BridgeProtocol.parse(state(ct = "NaN"), nonce))
        assertNull(BridgeProtocol.parse(state(ct = "1e999"), nonce))
        assertNull(BridgeProtocol.parse(state(d = "null"), nonce))
        assertNull(BridgeProtocol.parse(state(ct = "-1"), nonce))
    }

    @Test
    fun `unknown types, unknown events and garbage are dropped`() {
        assertNull(BridgeProtocol.parse("""{"n":"secret","t":"run","cmd":"x"}""", nonce))
        assertNull(BridgeProtocol.parse("""{"n":"secret","t":"event","name":"openWindow"}""", nonce))
        assertNull(BridgeProtocol.parse("not json", nonce))
        assertNull(BridgeProtocol.parse("[1,2]", nonce))
        assertNull(BridgeProtocol.parse(null, nonce))
    }

    @Test
    fun `an oversized message is dropped`() {
        assertNull(BridgeProtocol.parse("""{"n":"secret","t":"ready","x":"${"a".repeat(2000)}"}""", nonce))
    }

    @Test
    fun `only whitelisted rates are encoded`() {
        listOf(0.75, 1.0, 1.25, 1.5).forEach {
            assertTrue(BridgeProtocol.encode(PageCommand(PageCommandName.RATE, it), nonce) != null)
        }
        listOf(0.5, 2.0, 16.0).forEach {
            assertNull(BridgeProtocol.encode(PageCommand(PageCommandName.RATE, it), nonce))
        }
        assertNull(BridgeProtocol.encode(PageCommand(PageCommandName.RATE), nonce))
    }

    @Test
    fun `commands carry the nonce and refuse non finite values`() {
        val encoded = BridgeProtocol.encode(PageCommand(PageCommandName.SEEK_TO, 30.0), nonce)
        assertEquals("""{"n":"secret","cmd":"seekTo","value":30.0}""", encoded)
        assertNull(BridgeProtocol.encode(PageCommand(PageCommandName.SEEK_TO, Double.NaN), nonce))
    }
}

class ActiveFrameTrackerTest {

    private var now = 0L
    private val tracker = ActiveFrameTracker<String>(clock = { now })

    private fun video(duration: Double, paused: Boolean = false, ended: Boolean = false) = VideoState(
        currentTime = 1.0,
        duration = duration,
        isPaused = paused,
        isBuffering = false,
        rate = 1.0,
        volume = 1.0,
        isMuted = false,
        isEnded = ended,
        bufferedEnd = 0.0,
    )

    @Test
    fun `short videos are filtered out as ads`() {
        assertFalse(tracker.onState("ad", video(59.9)))
        assertNull(tracker.activeFrame)
        now += 1000
        assertTrue(tracker.onState("movie", video(60.0)))
        assertEquals("movie", tracker.activeFrame)
    }

    @Test
    fun `the first long video wins while it is playing`() {
        tracker.onState("a", video(100.0))
        now += 1000
        assertFalse(tracker.onState("b", video(5000.0)))
        assertEquals("a", tracker.activeFrame)
    }

    @Test
    fun `a longer video takes over from a paused or ended frame`() {
        tracker.onState("a", video(100.0, paused = true))
        now += 1000
        assertFalse(tracker.onState("b", video(90.0)))
        now += 1000
        assertTrue(tracker.onState("b", video(5000.0)))
        assertEquals("b", tracker.activeFrame)
    }

    @Test
    fun `a frame that fell silent is replaced, because a reloaded frame gets a new key`() {
        tracker.onState("old", video(100.0))
        now += 5_001
        assertTrue(tracker.onState("new", video(100.0)))
    }

    @Test
    fun `states closer than 250 ms apart are ignored per frame`() {
        assertTrue(tracker.onState("a", video(100.0)))
        now += 249
        assertFalse(tracker.onState("a", video(100.0)))
        now += 1
        assertTrue(tracker.onState("a", video(100.0)))
    }

    @Test
    fun `reset forgets the active frame`() {
        tracker.onState("a", video(100.0))
        tracker.reset()
        assertNull(tracker.activeFrame)
    }
}

class WatchProgressRulesTest {

    private fun entry(id: Int, position: Double = 100.0, duration: Double = 1000.0, at: Long = 0L) = WatchProgress(
        mediaType = "movie",
        tmdbId = id,
        positionSeconds = position,
        durationSeconds = duration,
        updatedAtMs = at,
        title = "T$id",
    )

    @Test
    fun `key format`() {
        assertEquals("tv:1399:2:5", WatchProgressRules.key("tv", 1399, 2, 5))
        assertEquals("movie:550:0:0", entry(550).key)
    }

    @Test
    fun `finished at 93 percent or when ended`() {
        assertFalse(WatchProgressRules.isFinished(929.0, 1000.0))
        assertTrue(WatchProgressRules.isFinished(930.0, 1000.0))
        assertTrue(WatchProgressRules.isFinished(1.0, 1000.0, isEnded = true))
        assertFalse(WatchProgressRules.isFinished(0.0, 0.0))
    }

    @Test
    fun `merge replaces the same key and sorts newest first`() {
        val merged = WatchProgressRules.merge(listOf(entry(1, at = 1), entry(2, at = 2)), entry(1, position = 200.0, at = 3))
        assertEquals(listOf(1, 2), merged.map { it.tmdbId })
        assertEquals(200.0, merged.first().positionSeconds, 0.0)
    }

    @Test
    fun `a finished title is removed`() {
        assertTrue(WatchProgressRules.merge(listOf(entry(1)), entry(1, position = 990.0)).isEmpty())
        assertTrue(WatchProgressRules.merge(listOf(entry(1)), entry(1), isEnded = true).isEmpty())
    }

    @Test
    fun `at most 30 entries are kept, dropping the oldest`() {
        val full = (1..30).map { entry(it, at = it.toLong()) }
        val merged = WatchProgressRules.merge(full, entry(31, at = 31))
        assertEquals(30, merged.size)
        assertEquals(31, merged.first().tmdbId)
        assertFalse(merged.any { it.tmdbId == 1 })
    }
}

class NextEpisodeResolverTest {

    private val today = LocalDate.of(2026, 11, 7)

    private fun episode(season: Int, number: Int, airDate: String? = "2020-01-01") = Episode(
        id = season * 100 + number,
        episodeNumber = number,
        seasonNumber = season,
        name = "E$number",
        overview = null,
        stillUrl = null,
        airDate = airDate,
        runtimeMinutes = 40,
    )

    private fun season(number: Int) = Season(number, number, "S$number", 2, null)

    private fun resolver(seasons: List<Season>, episodes: Map<Int, List<Episode>>, counter: (() -> Unit)? = null) =
        NextEpisodeResolver(
            object : EpisodeCatalog {
                override suspend fun seasons(showId: Int) = seasons

                override suspend fun episodes(showId: Int, season: Int): List<Episode>? {
                    counter?.invoke()
                    return episodes[season]
                }
            },
            today = { today },
        )

    private val twoSeasons = resolver(
        listOf(season(1), season(2)),
        mapOf(1 to listOf(episode(1, 1), episode(1, 2)), 2 to listOf(episode(2, 1), episode(2, 2))),
    )

    @Test
    fun `mid season goes to the next episode`() = runTest {
        assertEquals(EpisodeRef(1, 2, "E2"), twoSeasons.next(1, 1, 1))
    }

    @Test
    fun `last episode of a season goes to the next season`() = runTest {
        assertEquals(EpisodeRef(2, 1, "E1"), twoSeasons.next(1, 1, 2))
    }

    @Test
    fun `last episode of the show has no next`() = runTest {
        assertNull(twoSeasons.next(1, 2, 2))
    }

    @Test
    fun `an unaired next season has no next`() = runTest {
        val future = resolver(
            listOf(season(1), season(2)),
            mapOf(1 to listOf(episode(1, 1)), 2 to listOf(episode(2, 1, airDate = "2027-01-01"))),
        )
        assertNull(future.next(1, 1, 1))
    }

    @Test
    fun `a next season without an air date has not aired`() = runTest {
        val undated = resolver(
            listOf(season(1), season(2)),
            mapOf(1 to listOf(episode(1, 1)), 2 to listOf(episode(2, 1, airDate = null))),
        )
        assertNull(undated.next(1, 1, 1))
    }

    @Test
    fun `an episode airing later in the current season has no next`() = runTest {
        val airing = resolver(
            listOf(season(1)),
            mapOf(1 to listOf(episode(1, 1), episode(1, 2, airDate = "2026-11-08"))),
        )
        assertNull(airing.next(1, 1, 1))
    }

    @Test
    fun `an episode airing today counts as aired`() = runTest {
        val aired = resolver(
            listOf(season(1), season(2)),
            mapOf(1 to listOf(episode(1, 1)), 2 to listOf(episode(2, 1, airDate = "2026-11-07"))),
        )
        assertEquals(EpisodeRef(2, 1, "E1"), aired.next(1, 1, 1))
    }

    @Test
    fun `previous steps back across seasons`() = runTest {
        assertEquals(EpisodeRef(1, 1, "E1"), twoSeasons.previous(1, 1, 2))
        assertEquals(EpisodeRef(1, 2, "E2"), twoSeasons.previous(1, 2, 1))
        assertNull(twoSeasons.previous(1, 1, 1))
    }

    @Test
    fun `seasons are fetched once`() = runTest {
        var calls = 0
        val cached = resolver(listOf(season(1)), mapOf(1 to listOf(episode(1, 1), episode(1, 2)))) { calls++ }
        cached.next(1, 1, 1)
        cached.next(1, 1, 1)
        cached.episodeName(1, 1, 1)
        assertEquals(1, calls)
    }
}

class AdHostBlocklistTest {

    private val blocklist = AdHostBlocklist.parse(
        sequenceOf("# a comment", "", "   ", "doubleclick.net", "Popads.NET  # trailing comment", "mc.yandex.ru"),
    )

    @Test
    fun `exact hosts and parent domains match`() {
        assertTrue(blocklist.matches("doubleclick.net"))
        assertTrue(blocklist.matches("ad.g.doubleclick.net"))
        assertTrue(blocklist.matches("popads.net"))
        assertTrue(blocklist.matches("WWW.POPADS.NET"))
    }

    @Test
    fun `a listed subdomain does not cover its parent`() {
        assertTrue(blocklist.matches("mc.yandex.ru"))
        assertFalse(blocklist.matches("yandex.ru"))
    }

    @Test
    fun `lookalikes and unrelated hosts do not match`() {
        assertFalse(blocklist.matches("notdoubleclick.net"))
        assertFalse(blocklist.matches("doubleclick.net.example.org"))
        assertFalse(blocklist.matches("vidzy.cc"))
        assertFalse(blocklist.matches("net"))
        assertFalse(blocklist.matches(null))
        assertFalse(blocklist.matches(""))
    }

    @Test
    fun `comments and blank lines are not entries`() {
        assertFalse(blocklist.matches("#"))
        assertFalse(blocklist.matches("a comment"))
    }
}

class HostPolicyTest {

    private val policy = MainFrameHostPolicy(setOf("vidzy.org"))

    @Test
    fun `the provider and its subdomains are allowed over https`() {
        assertTrue(policy.isAllowed("https://vidzy.org/movie/550?autoplay=1"))
        assertTrue(policy.isAllowed("https://www.vidzy.org/movie/550"))
    }

    @Test
    fun `everything else is blocked`() {
        listOf(
            "http://vidzy.org/movie/550",
            "https://evilvidzy.org/",
            "https://vidzy.org.evil.com/",
            "https://vidzy.cc/embed-x.html",
            "intent://scan/#Intent;scheme=zxing;end",
            "market://details?id=x",
            "tel:123",
            "mailto:a@b.c",
            "javascript:alert(1)",
            "about:blank",
            "",
        ).forEach { assertFalse(it, policy.isAllowed(it)) }
    }

    @Test
    fun `a certificate error only ends playback for the page itself`() {
        val page = "https://vidzy.org/movie/550?autoplay=1"
        assertTrue(isFatalSslError("https://vidzy.org/movie/550", page))
        assertFalse(isFatalSslError("https://ads.example/pixel.gif", page))
        assertFalse(isFatalSslError("https://u14.vidzy.cc/hls2/a.ts", page))
        assertTrue("unknown failed url", isFatalSslError(null, page))
        assertTrue("unknown page", isFatalSslError("https://ads.example/x", null))
    }

    @Test
    fun `subframes may use any web host but no other scheme`() {
        assertTrue(isAllowedSubframeUrl("https://vidzy.cc/embed-x.html"))
        assertFalse(isAllowedSubframeUrl("intent://scan/#Intent;end"))
        assertFalse(isAllowedSubframeUrl("market://details?id=x"))
    }
}
