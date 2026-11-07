package dev.codexsoft.codexcine

import dev.codexsoft.codexcine.data.progress.EpisodeNumber
import dev.codexsoft.codexcine.data.progress.WatchedEpisodesRules
import dev.codexsoft.codexcine.data.progress.WatchedShow
import dev.codexsoft.codexcine.domain.model.Episode
import dev.codexsoft.codexcine.domain.model.MediaItem
import dev.codexsoft.codexcine.domain.model.MediaType
import dev.codexsoft.codexcine.domain.model.NewEpisodeRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class NewEpisodeRulesTest {

    private val today = LocalDate.of(2026, 11, 7)

    private fun show(id: Int) = MediaItem(id, "Série $id", null, null, null, 0.0, MediaType.TV)

    private fun episode(season: Int, number: Int, airDate: String?) =
        Episode(id = season * 100 + number, episodeNumber = number, seasonNumber = season, name = "É$number",
            overview = null, stillUrl = null, airDate = airDate, runtimeMinutes = null)

    private fun select(
        vararg followed: Pair<MediaItem, Episode?>,
        watched: Map<Int, Pair<Int, Int>> = emptyMap(),
        inProgress: Set<String> = emptySet(),
        started: Set<Int> = emptySet(),
    ) = NewEpisodeRules.select(followed.toList(), watched, inProgress, started, today)

    @Test
    fun `an episode aired in the last 30 days and not watched is new`() {
        val result = select(show(1) to episode(2, 5, "2026-11-01"))
        assertEquals(1, result.size)
        assertEquals(5, result.single().episode.episodeNumber)
        assertFalse(result.single().isShowStarted)
    }

    @Test
    fun `an episode older than the window, announced, or without a date is not`() {
        val result = select(
            show(1) to episode(1, 1, "2026-10-07"),
            show(2) to episode(1, 1, "2026-11-08"),
            show(3) to episode(1, 1, null),
            show(4) to episode(1, 1, "pas une date"),
            show(5) to null,
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `the first day of the window still counts`() {
        assertEquals(1, select(show(1) to episode(1, 1, "2026-10-08")).size)
    }

    @Test
    fun `an episode at or before the furthest watched one is not new`() {
        val aired = "2026-11-01"
        assertTrue(select(show(1) to episode(2, 5, aired), watched = mapOf(1 to (2 to 5))).isEmpty())
        assertTrue(select(show(1) to episode(2, 5, aired), watched = mapOf(1 to (3 to 1))).isEmpty())
        assertEquals(1, select(show(1) to episode(3, 1, aired), watched = mapOf(1 to (2 to 9))).size)
        assertEquals(1, select(show(1) to episode(2, 6, aired), watched = mapOf(1 to (2 to 5))).size)
    }

    @Test
    fun `an episode already in continue watching is left to that row`() {
        val result = select(show(1) to episode(2, 5, "2026-11-01"), inProgress = setOf(NewEpisodeRules.key(1, 2, 5)))
        assertTrue(result.isEmpty())
    }

    @Test
    fun `most recently aired first, one card per show, started shows flagged`() {
        val result = select(
            show(1) to episode(1, 3, "2026-10-20"),
            show(2) to episode(4, 1, "2026-11-05"),
            show(2) to episode(4, 1, "2026-11-05"),
            started = setOf(2),
        )
        assertEquals(listOf(2, 1), result.map { it.show.id })
        assertTrue(result.first().isShowStarted)
    }

    @Test
    fun `specials and unnumbered episodes are ignored`() {
        assertTrue(select(show(1) to episode(0, 3, "2026-11-01"), show(2) to episode(1, 0, "2026-11-01")).isEmpty())
    }
}

class WatchedEpisodesRulesTest {

    @Test
    fun `marking keeps every episode once, sorted, and the furthest one`() {
        var shows = emptyList<WatchedShow>()
        shows = WatchedEpisodesRules.markWatched(shows, 7, EpisodeNumber(2, 1), nowMs = 1)
        shows = WatchedEpisodesRules.markWatched(shows, 7, EpisodeNumber(1, 9), nowMs = 2)
        shows = WatchedEpisodesRules.markWatched(shows, 7, EpisodeNumber(2, 1), nowMs = 3)
        val show = shows.single()
        assertEquals(listOf(EpisodeNumber(1, 9), EpisodeNumber(2, 1)), show.episodes)
        assertEquals(EpisodeNumber(2, 1), show.furthest)
        assertEquals(3L, show.updatedAtMs)
    }

    @Test
    fun `the most recently watched show comes first and the oldest are dropped`() {
        var shows = emptyList<WatchedShow>()
        for (id in 1..WatchedEpisodesRules.MAX_SHOWS + 1) {
            shows = WatchedEpisodesRules.markWatched(shows, id, EpisodeNumber(1, 1), nowMs = id.toLong())
        }
        assertEquals(WatchedEpisodesRules.MAX_SHOWS, shows.size)
        assertEquals(WatchedEpisodesRules.MAX_SHOWS + 1, shows.first().showId)
        assertFalse(shows.any { it.showId == 1 })
    }
}
