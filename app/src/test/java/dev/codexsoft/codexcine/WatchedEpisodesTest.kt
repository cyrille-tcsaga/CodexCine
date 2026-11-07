package dev.codexsoft.codexcine

import dev.codexsoft.codexcine.data.progress.EpisodeNumber
import dev.codexsoft.codexcine.data.progress.WatchedEpisodesRules
import dev.codexsoft.codexcine.data.progress.WatchedShow
import dev.codexsoft.codexcine.domain.model.Episode
import dev.codexsoft.codexcine.ui.common.meaningfulName
import dev.codexsoft.codexcine.ui.season.EpisodeAction
import dev.codexsoft.codexcine.ui.season.EpisodeItem
import dev.codexsoft.codexcine.ui.season.EpisodeMenu
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class EpisodeMenuTest {

    private val today = LocalDate.of(2026, 11, 7)

    private fun item(number: Int, watched: Boolean = false, airDate: String? = "2026-01-0$number") = EpisodeItem(
        Episode(id = number, episodeNumber = number, seasonNumber = 2, name = "", overview = null,
            stillUrl = null, airDate = airDate, runtimeMinutes = null),
        isWatched = watched,
    )

    @Test
    fun `an unwatched first episode offers to mark it, and the season`() {
        val season = listOf(item(1), item(2), item(3))
        assertEquals(
            listOf(EpisodeAction.MARK_WATCHED, EpisodeAction.MARK_SEASON_WATCHED),
            EpisodeMenu.actions(season[0], season, today),
        )
    }

    @Test
    fun `an episode after unwatched ones also offers to mark up to it`() {
        val season = listOf(item(1), item(2, watched = true), item(3))
        val actions = EpisodeMenu.actions(season[2], season, today)
        assertEquals(
            listOf(EpisodeAction.MARK_WATCHED, EpisodeAction.MARK_WATCHED_UP_TO_HERE, EpisodeAction.MARK_SEASON_WATCHED),
            actions,
        )
        assertEquals(
            listOf(EpisodeNumber(2, 1), EpisodeNumber(2, 2), EpisodeNumber(2, 3)),
            EpisodeMenu.episodesFor(EpisodeAction.MARK_WATCHED_UP_TO_HERE, season[2], season, today),
        )
    }

    @Test
    fun `a fully watched season offers to unmark the episode and the season`() {
        val season = listOf(item(1, watched = true), item(2, watched = true))
        assertEquals(
            listOf(EpisodeAction.MARK_UNWATCHED, EpisodeAction.MARK_SEASON_UNWATCHED),
            EpisodeMenu.actions(season[1], season, today),
        )
        assertEquals(
            listOf(EpisodeNumber(2, 1), EpisodeNumber(2, 2)),
            EpisodeMenu.episodesFor(EpisodeAction.MARK_SEASON_UNWATCHED, season[1], season, today),
        )
    }

    @Test
    fun `episodes not aired yet are left out of the season and up-to-here marks`() {
        val season = listOf(item(1), item(2, airDate = null), item(3, airDate = "2026-12-01"))
        assertEquals(
            listOf(EpisodeNumber(2, 1), EpisodeNumber(2, 2)),
            EpisodeMenu.episodesFor(EpisodeAction.MARK_SEASON_WATCHED, season[0], season, today),
        )
        // Once every aired episode is watched, the season counts as watched.
        val watched = listOf(item(1, watched = true), item(2, watched = true, airDate = null), season[2])
        assertTrue(EpisodeAction.MARK_SEASON_UNWATCHED in EpisodeMenu.actions(watched[0], watched, today))
    }

    @Test
    fun `a single-episode season has no season action`() {
        val season = listOf(item(1))
        assertEquals(listOf(EpisodeAction.MARK_WATCHED), EpisodeMenu.actions(season[0], season, today))
    }
}

class WatchedEpisodesSetTest {

    @Test
    fun `unmarking removes episodes, and a show with none left is forgotten`() {
        var shows = WatchedEpisodesRules.setWatched(
            emptyList(), 7, listOf(EpisodeNumber(1, 1), EpisodeNumber(1, 2)), isWatched = true, nowMs = 1,
        )
        shows = WatchedEpisodesRules.setWatched(shows, 7, listOf(EpisodeNumber(1, 2)), isWatched = false, nowMs = 2)
        assertEquals(listOf(EpisodeNumber(1, 1)), shows.single().episodes)

        shows = WatchedEpisodesRules.setWatched(shows, 7, listOf(EpisodeNumber(1, 1)), isWatched = false, nowMs = 3)
        assertEquals(emptyList<WatchedShow>(), shows)
    }
}

class EpisodeNameTest {

    private fun named(name: String) = Episode(1, 5, 1, name, null, null, null, null)

    @Test
    fun `placeholder and blank titles are hidden, real ones kept`() {
        assertNull(named("Épisode 5").meaningfulName())
        assertNull(named("episode 12").meaningfulName())
        assertNull(named("  ").meaningfulName())
        assertEquals("Épisode spécial", named("Épisode spécial").meaningfulName())
        assertEquals("Félina", named(" Félina ").meaningfulName())
    }
}
