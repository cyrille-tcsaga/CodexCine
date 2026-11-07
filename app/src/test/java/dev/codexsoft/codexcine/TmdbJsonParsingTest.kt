package dev.codexsoft.codexcine

import dev.codexsoft.codexcine.core.ImageUrls
import dev.codexsoft.codexcine.data.mapper.toMediaDetails
import dev.codexsoft.codexcine.data.mapper.toSearchResults
import dev.codexsoft.codexcine.data.remote.TmdbJson
import dev.codexsoft.codexcine.data.remote.dto.TmdbMovieDetailsDto
import dev.codexsoft.codexcine.data.remote.dto.TmdbMultiSearchItemDto
import dev.codexsoft.codexcine.data.remote.dto.TmdbPaginatedResponse
import dev.codexsoft.codexcine.data.remote.dto.TmdbTvDetailsDto
import dev.codexsoft.codexcine.domain.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class TmdbJsonParsingTest {

    private fun fixture(name: String): String =
        requireNotNull(javaClass.classLoader?.getResource(name)) { "Missing fixture $name" }.readText()

    @Before
    fun resetImageBase() {
        ImageUrls.setBaseUrl(ImageUrls.DEFAULT_BASE_URL)
    }

    @Test
    fun `movie details with appended responses`() {
        val details = TmdbJson.decodeFromString<TmdbMovieDetailsDto>(fixture("movie_details.json"))
            .toMediaDetails()

        assertEquals(550, details.id)
        assertEquals("Fight Club", details.title)
        assertEquals("1999", details.releaseYear)
        assertEquals(139, details.runtimeMinutes)
        assertEquals(null, details.seasonCount)
        assertEquals("16", details.certification)
        assertEquals(listOf("Drame", "Thriller"), details.genres)
        assertEquals(27280, details.voteCount)
        assertEquals(MediaType.MOVIE, details.mediaType)
        assertEquals("frTrailerKey", details.trailerKey)
        assertEquals(listOf("Edward Norton", "Brad Pitt"), details.cast.map { it.name })
        assertEquals("The Narrator", details.cast.first().character)
        assertNull(details.cast[1].profileUrl)
        // Recommendations are empty in the fixture, so the similar titles are used instead.
        assertEquals(listOf(680), details.recommendations.map { it.id })
        assertEquals(
            "https://image.tmdb.org/t/p/w1280/hZkgoQYus5vegHoetLkCJzb17zJ.jpg",
            details.backdropUrl,
        )
    }

    @Test
    fun `tv details with appended responses`() {
        val details = TmdbJson.decodeFromString<TmdbTvDetailsDto>(fixture("tv_details.json"))
            .toMediaDetails()

        assertEquals("Breaking Bad", details.title)
        assertEquals(5, details.seasonCount)
        assertEquals("16", details.certification)
        assertNull("A blank overview must not reach the UI", details.overview)
        assertNull(details.trailerKey)
        assertEquals(listOf(1, 2), details.seasons.map { it.seasonNumber })
        assertEquals(listOf(1399), details.recommendations.map { it.id })
    }

    @Test
    fun `multi search keeps movie, tv and person only`() {
        val response = TmdbJson.decodeFromString<TmdbPaginatedResponse<TmdbMultiSearchItemDto>>(
            fixture("search_multi.json"),
        )
        val results = response.results.orEmpty().toSearchResults()

        assertEquals(1, response.totalPages)
        assertEquals(listOf("Fight Club"), results.movies.map { it.title })
        assertEquals(listOf("Breaking Bad"), results.shows.map { it.title })
        assertEquals(listOf("Brad Pitt"), results.people.map { it.title })
        assertNull("TMDB sends an empty date, which is no year", results.shows.first().releaseYear)
    }

    @Test
    fun `explicit nulls and missing fields are tolerated`() {
        val minimal = """{"id": 1, "title": null, "runtime": null, "genres": null, "credits": null}"""
        val details = TmdbJson.decodeFromString<TmdbMovieDetailsDto>(minimal).toMediaDetails()

        assertEquals("", details.title)
        assertNull(details.runtimeMinutes)
        assertEquals(emptyList<String>(), details.genres)
    }
}
