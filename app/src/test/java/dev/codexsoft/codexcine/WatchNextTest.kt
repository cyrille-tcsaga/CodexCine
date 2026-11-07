package dev.codexsoft.codexcine

import dev.codexsoft.codexcine.core.PlayLink
import dev.codexsoft.codexcine.data.progress.WatchProgress
import dev.codexsoft.codexcine.data.watchnext.WatchNextEntry
import dev.codexsoft.codexcine.data.watchnext.WatchNextRules
import dev.codexsoft.codexcine.domain.model.Episode
import dev.codexsoft.codexcine.domain.model.MediaItem
import dev.codexsoft.codexcine.domain.model.MediaType
import dev.codexsoft.codexcine.domain.model.NewEpisode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayLinkTest {

    @Test
    fun `a link survives the round trip, title included`() {
        val episode = PlayLink(MediaType.TV, 2734, 28, 5, "New York Unité Spéciale & co")
        assertEquals("codexcine://play/tv/2734?s=28&e=5&t=New+York+Unit%C3%A9+Sp%C3%A9ciale+%26+co", episode.toUri())
        assertEquals(episode, PlayLink.parse(episode.toUri()))

        val movie = PlayLink(MediaType.MOVIE, 550)
        assertEquals("codexcine://play/movie/550", movie.toUri())
        assertEquals(movie, PlayLink.parse(movie.toUri()))
    }

    @Test
    fun `anything else is refused`() {
        listOf(
            null,
            "",
            "https://play/tv/1?s=1&e=1",
            "codexcine://other/tv/1?s=1&e=1",
            "codexcine://play/person/1",
            "codexcine://play/tv/1",
            "codexcine://play/tv/1?s=1",
            "codexcine://play/tv/1?s=1&e=0",
            "codexcine://play/movie/1?s=1&e=1",
            "codexcine://play/movie/0",
            "codexcine://play/movie/-5",
            "codexcine://play/movie/abc",
            "codexcine://play/movie/1/extra",
            "codexcine://play/movie/99999999999",
            "codexcine://play/tv/1?s=x&e=1",
            "codexcine://play/movie/1?t=%E0%A4%A",
            "not a uri at all",
        ).forEach { assertNull(it, PlayLink.parse(it)) }
    }

    @Test
    fun `control characters are dropped and long titles cut`() {
        val link = PlayLink.parse("codexcine://play/movie/1?t=" + "a%0Ab".padEnd(5, 'x') + "y".repeat(300))
        assertTrue(link?.title.orEmpty().none { it.isISOControl() })
        assertEquals(200, link?.title?.length)
    }
}

class WatchNextRulesTest {

    private fun progress(
        type: String = "tv",
        id: Int = 1,
        season: Int = 1,
        episode: Int = 2,
        updatedAt: Long = 100,
        position: Double = 600.0,
    ) = WatchProgress(type, id, season, episode, position, 1_800.0, updatedAt, "Série $id", "poster", "backdrop")

    @Test
    fun `titles in progress become continue cards, newest first, with a playable link`() {
        val entries = WatchNextRules.continueEntries(
            listOf(progress(id = 1, updatedAt = 100), progress(type = "movie", id = 2, season = 0, episode = 0, updatedAt = 200)),
            dismissedAt = emptyMap(),
        )
        assertEquals(listOf(2, 1), entries.map { PlayLink.parse(it.link)?.mediaId })
        val show = entries[1]
        assertEquals(WatchNextEntry.Kind.CONTINUE, show.kind)
        assertEquals("continue:tv:1:1:2", show.key)
        assertEquals(600_000L, show.positionMs)
        assertEquals(1_800_000L, show.durationMs)
        assertEquals("backdrop", show.imageUrl)
        assertEquals(PlayLink(MediaType.TV, 1, 1, 2, "Série 1"), PlayLink.parse(show.link))
        assertNull(entries[0].season)
    }

    @Test
    fun `a removed card stays away until the title is watched again`() {
        val key = "continue:tv:1:1:2"
        assertTrue(WatchNextRules.continueEntries(listOf(progress(updatedAt = 100)), mapOf(key to 150)).isEmpty())
        assertEquals(1, WatchNextRules.continueEntries(listOf(progress(updatedAt = 200)), mapOf(key to 150)).size)
    }

    @Test
    fun `unknown types, test ids and empty progress are left out, at most ten cards`() {
        val skipped = listOf(progress(type = "person"), progress(id = 0), progress(position = 0.0))
        assertTrue(WatchNextRules.continueEntries(skipped, emptyMap()).isEmpty())
        val many = (1..15).map { progress(id = it, updatedAt = it.toLong()) }
        assertEquals(WatchNextRules.MAX_CONTINUE, WatchNextRules.continueEntries(many, emptyMap()).size)
    }

    @Test
    fun `new episodes only for shows already started, unless removed`() {
        fun new(id: Int, started: Boolean) = NewEpisode(
            MediaItem(id, "Série $id", null, "backdrop", null, 0.0, MediaType.TV),
            Episode(10, 5, 3, "Titre", null, "still", "2026-11-01", 42),
            isShowStarted = started,
        )
        val entries = WatchNextRules.newEntries(
            listOf(new(1, started = true), new(2, started = false), new(3, started = true)),
            dismissed = setOf("new:3:3:5"),
            nowMs = 7,
        )
        val entry = entries.single()
        assertEquals("new:1:3:5", entry.key)
        assertEquals(WatchNextEntry.Kind.NEW, entry.kind)
        assertEquals(42 * 60_000L, entry.durationMs)
        assertEquals("still", entry.imageUrl)
        assertEquals(PlayLink(MediaType.TV, 1, 3, 5, "Série 1"), PlayLink.parse(entry.link))
    }
}
