package dev.codexsoft.codexcine

import dev.codexsoft.codexcine.core.AppLink
import dev.codexsoft.codexcine.core.DetailsLink
import dev.codexsoft.codexcine.core.PlayLink
import dev.codexsoft.codexcine.data.update.AppVersion
import dev.codexsoft.codexcine.data.update.GitHubAsset
import dev.codexsoft.codexcine.data.update.GitHubRelease
import dev.codexsoft.codexcine.data.update.UpdateRules
import dev.codexsoft.codexcine.domain.model.CategoryType
import dev.codexsoft.codexcine.domain.model.DiscoverQuery
import dev.codexsoft.codexcine.domain.model.GridFilters
import dev.codexsoft.codexcine.domain.model.GridSort
import dev.codexsoft.codexcine.domain.model.MediaItem
import dev.codexsoft.codexcine.domain.model.MediaType
import dev.codexsoft.codexcine.domain.model.Saga
import dev.codexsoft.codexcine.domain.model.SagaRules
import dev.codexsoft.codexcine.domain.model.SearchResults
import dev.codexsoft.codexcine.ui.search.SearchSuggestions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

private fun movie(id: Int, type: MediaType = MediaType.MOVIE) =
    MediaItem(id, "Titre $id", "poster$id", null, "20${10 + id}", 7.0, type)

class SagaRulesTest {

    @Test
    fun `the other films in release order, undated last, with the film's place`() {
        val saga = Saga(
            "Harry Potter - Saga",
            listOf(movie(3) to "2004-06-04", movie(9) to null, movie(1) to "2001-11-16", movie(2) to "2002-11-15"),
        )
        val row = requireNotNull(SagaRules.rowFor(saga, currentId = 2))
        assertEquals(listOf(1, 3, 9), row.others.map { it.id })
        assertEquals(2, row.position)
        assertEquals(4, row.total)
    }

    @Test
    fun `a saga with nothing else to show has no row, and an unlisted film no position`() {
        assertNull(SagaRules.rowFor(Saga("Seul", listOf(movie(1) to "2001-01-01")), currentId = 1))
        val row = requireNotNull(SagaRules.rowFor(Saga("S", listOf(movie(1) to "2001-01-01")), currentId = 5))
        assertNull(row.position)
        assertEquals(listOf(1), row.others.map { it.id })
    }

    @Test
    fun `duplicates are shown once`() {
        val saga = Saga("S", listOf(movie(1) to "2001-01-01", movie(1) to "2001-01-01", movie(2) to "2003-01-01"))
        assertEquals(2, SagaRules.rowFor(saga, currentId = 7)?.total)
    }
}

class DiscoverQueryTest {

    private val today = LocalDate.of(2026, 11, 8)

    @Test
    fun `filters narrow a category without losing its meaning`() {
        val params = DiscoverQuery.build(
            CategoryType.TOP_RATED_MOVIES,
            GridFilters(genreId = 28, decade = 1990, minRating = 7),
            today,
        )
        assertEquals("vote_average.desc", params["sort_by"])
        assertEquals("28", params["with_genres"])
        assertEquals("1990-01-01", params["primary_release_date.gte"])
        assertEquals("1999-12-31", params["primary_release_date.lte"])
        assertEquals("7", params["vote_average.gte"])
        assertEquals("300", params["vote_count.gte"])
    }

    @Test
    fun `shows use their own date field, and the current decade stops today`() {
        val params = DiscoverQuery.build(CategoryType.POPULAR_TV, GridFilters(decade = 2020), today)
        assertEquals("2020-01-01", params["first_air_date.gte"])
        assertEquals("2026-11-08", params["first_air_date.lte"])
        assertFalse(params.containsKey("vote_count.gte"))
    }

    @Test
    fun `a genre category keeps its genre and adds the chosen one`() {
        val params = DiscoverQuery.build(CategoryType.GENRE_ACTION, GridFilters(genreId = 35), today)
        assertEquals("28,35", params["with_genres"])
    }

    @Test
    fun `time-window categories keep their window and ignore a decade`() {
        val params = DiscoverQuery.build(CategoryType.NOW_PLAYING_MOVIES, GridFilters(decade = 1990), today)
        assertEquals("2026-09-24", params["primary_release_date.gte"])
        assertEquals("2026-11-08", params["primary_release_date.lte"])
        assertFalse(DiscoverQuery.allowsDecade(CategoryType.UPCOMING_MOVIES))
    }

    @Test
    fun `sorting by newest never opens on announced titles, except for upcoming`() {
        val popular = DiscoverQuery.build(CategoryType.POPULAR_MOVIES, GridFilters(sort = GridSort.NEWEST), today)
        assertEquals("primary_release_date.desc", popular["sort_by"])
        assertEquals("2026-11-08", popular["primary_release_date.lte"])
        val upcoming = DiscoverQuery.build(CategoryType.UPCOMING_MOVIES, GridFilters(sort = GridSort.NEWEST), today)
        assertFalse(upcoming.containsKey("primary_release_date.lte"))
    }

    @Test
    fun `trending and local rows cannot be filtered`() {
        assertNull(DiscoverQuery.mediaTypeOf(CategoryType.TRENDING))
        assertNull(DiscoverQuery.mediaTypeOf(CategoryType.MY_LIST))
        assertEquals(MediaType.TV, DiscoverQuery.mediaTypeOf(CategoryType.LATEST_TV))
    }

    @Test
    fun `active filters are counted`() {
        assertFalse(GridFilters().isActive)
        assertEquals(2, GridFilters(genreId = 1, sort = GridSort.RATING).activeCount)
    }
}

class DetailsLinkTest {

    @Test
    fun `a details link survives the round trip and is told apart from a play link`() {
        val link = DetailsLink(MediaType.TV, 1396, "Breaking Bad")
        assertEquals("codexcine://details/tv/1396?t=Breaking+Bad", link.toUri())
        assertEquals(link, AppLink.parse(link.toUri()))
        assertTrue(AppLink.parse(PlayLink(MediaType.MOVIE, 550).toUri()) is PlayLink)
    }

    @Test
    fun `a details link takes no episode and no other host`() {
        assertNull(DetailsLink.parse("codexcine://details/tv/1?s=1&e=1"))
        assertNull(DetailsLink.parse("codexcine://details/person/1"))
        assertNull(AppLink.parse("codexcine://other/movie/1"))
        assertNull(AppLink.parse("intent://details/movie/1"))
    }
}

class SearchSuggestionsTest {

    @Test
    fun `movies and shows alternate, people are left out, ten at most`() {
        val results = SearchResults(
            movies = (1..8).map { movie(it) },
            shows = (11..18).map { movie(it, MediaType.TV) },
            people = listOf(movie(99, MediaType.PERSON)),
        )
        val suggestions = SearchSuggestions.from(results)
        assertEquals(SearchSuggestions.MAX, suggestions.size)
        assertEquals(listOf(1, 11, 2, 12), suggestions.take(4).map { it.id })
        assertEquals("codexcine://details/tv/11?t=Titre+11", suggestions[1].link)
        assertTrue(suggestions.none { it.id == 99 })
    }

    @Test
    fun `queries are cleaned and too short ones dropped`() {
        assertEquals("dune", SearchSuggestions.cleanQuery("  du\u0000ne \n"))
        assertNull(SearchSuggestions.cleanQuery("a"))
        assertNull(SearchSuggestions.cleanQuery(null))
        assertEquals(100, SearchSuggestions.cleanQuery("x".repeat(500))?.length)
    }
}

class UpdateRulesTest {

    private fun release(tag: String, vararg assets: GitHubAsset, draft: Boolean = false, pre: Boolean = false) =
        GitHubRelease(tag, draft, pre, "Notes", assets.toList())

    private val apk = GitHubAsset(
        "CodexCine-v1.2.0.apk",
        "https://github.com/cyrille-tcsaga/CodexCine-releases/releases/download/v1.2.0/CodexCine-v1.2.0.apk",
        4_000_000,
    )

    @Test
    fun `versions are read from tags and compared in order`() {
        assertEquals(AppVersion(1, 2, 0), AppVersion.parse("v1.2"))
        assertEquals(AppVersion(1, 10, 3), AppVersion.parse("1.10.3"))
        assertTrue(AppVersion.parse("1.10.0")!! > AppVersion.parse("1.9.9")!!)
        assertNull(AppVersion.parse("latest"))
        assertNull(AppVersion.parse("1.2.0-beta"))
    }

    @Test
    fun `a newer release with an apk from GitHub is offered`() {
        val update = requireNotNull(UpdateRules.newerThan(AppVersion(1, 1, 0), release("v1.2.0", apk)))
        assertEquals(AppVersion(1, 2, 0), update.version)
        assertEquals(apk.downloadUrl, update.apkUrl)
        assertEquals("Notes", update.notes)
    }

    @Test
    fun `same or older versions, drafts, pre-releases and odd assets are not`() {
        val installed = AppVersion(1, 2, 0)
        assertNull(UpdateRules.newerThan(installed, release("v1.2.0", apk)))
        assertNull(UpdateRules.newerThan(installed, release("v1.1.9", apk)))
        assertNull(UpdateRules.newerThan(AppVersion(1, 0, 0), release("v1.2.0", apk, draft = true)))
        assertNull(UpdateRules.newerThan(AppVersion(1, 0, 0), release("v1.2.0", apk, pre = true)))
        assertNull(UpdateRules.newerThan(AppVersion(1, 0, 0), release("v1.2.0")))
        assertNull(UpdateRules.newerThan(AppVersion(1, 0, 0), release("v1.2.0", apk.copy(downloadUrl = "http://github.com/x.apk"))))
        assertNull(UpdateRules.newerThan(AppVersion(1, 0, 0), release("v1.2.0", apk.copy(downloadUrl = "https://evil.example/x.apk"))))
        assertNull(UpdateRules.newerThan(AppVersion(1, 0, 0), release("v1.2.0", apk.copy(size = 500L * 1024 * 1024))))
    }
}
