package dev.codexsoft.codexcine

import dev.codexsoft.codexcine.core.ImageUrls
import dev.codexsoft.codexcine.data.mapper.selectTrailerKey
import dev.codexsoft.codexcine.data.mapper.toEpisode
import dev.codexsoft.codexcine.data.mapper.toMediaDetails
import dev.codexsoft.codexcine.data.mapper.toMediaItem
import dev.codexsoft.codexcine.data.mapper.toPersonDetails
import dev.codexsoft.codexcine.data.mapper.toSearchResults
import dev.codexsoft.codexcine.data.mapper.toTrendingItems
import dev.codexsoft.codexcine.data.mapper.toVisibleSeasons
import dev.codexsoft.codexcine.data.mapper.frenchMovieCertification
import dev.codexsoft.codexcine.data.mapper.frenchTvCertification
import dev.codexsoft.codexcine.data.remote.dto.TmdbCastDto
import dev.codexsoft.codexcine.data.remote.dto.TmdbCombinedCreditsDto
import dev.codexsoft.codexcine.data.remote.dto.TmdbContentRatingItemDto
import dev.codexsoft.codexcine.data.remote.dto.TmdbContentRatingsDto
import dev.codexsoft.codexcine.data.remote.dto.TmdbCreditsDto
import dev.codexsoft.codexcine.data.remote.dto.TmdbEpisodeDto
import dev.codexsoft.codexcine.data.remote.dto.TmdbMovieDetailsDto
import dev.codexsoft.codexcine.data.remote.dto.TmdbMovieDto
import dev.codexsoft.codexcine.data.remote.dto.TmdbMultiSearchItemDto
import dev.codexsoft.codexcine.data.remote.dto.TmdbPaginatedResponse
import dev.codexsoft.codexcine.data.remote.dto.TmdbPersonDetailsDto
import dev.codexsoft.codexcine.data.remote.dto.TmdbReleaseDateItemDto
import dev.codexsoft.codexcine.data.remote.dto.TmdbReleaseDateResultDto
import dev.codexsoft.codexcine.data.remote.dto.TmdbReleaseDatesDto
import dev.codexsoft.codexcine.data.remote.dto.TmdbSeasonDto
import dev.codexsoft.codexcine.data.remote.dto.TmdbTvDetailsDto
import dev.codexsoft.codexcine.data.remote.dto.TmdbTvDto
import dev.codexsoft.codexcine.domain.model.MediaType
import dev.codexsoft.codexcine.domain.model.Video
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MappersTest {

    @Before
    fun resetImageBase() {
        ImageUrls.setBaseUrl(ImageUrls.DEFAULT_BASE_URL)
    }

    @Test
    fun `movie maps every field and uses the background size for the backdrop`() {
        val item = TmdbMovieDto(
            id = 1,
            title = "Titre",
            posterPath = "/p.jpg",
            backdropPath = "/b.jpg",
            releaseDate = "2024-03-15",
            voteAverage = 7.8,
        ).toMediaItem()

        assertEquals(1, item.id)
        assertEquals("Titre", item.title)
        assertEquals("https://image.tmdb.org/t/p/w342/p.jpg", item.posterUrl)
        assertEquals("https://image.tmdb.org/t/p/w1280/b.jpg", item.backdropUrl)
        assertEquals("2024", item.releaseYear)
        assertEquals(7.8, item.voteAverage, 0.0)
        assertEquals(MediaType.MOVIE, item.mediaType)
    }

    @Test
    fun `movie with only an id gets safe defaults`() {
        val item = TmdbMovieDto(id = 1).toMediaItem()

        assertEquals("", item.title)
        assertNull(item.posterUrl)
        assertNull(item.backdropUrl)
        assertNull(item.releaseYear)
        assertEquals(0.0, item.voteAverage, 0.0)
    }

    @Test
    fun `an empty date string gives no year`() {
        assertNull(TmdbMovieDto(id = 1, releaseDate = "").toMediaItem().releaseYear)
    }

    @Test
    fun `tv show uses name and first air date`() {
        val item = TmdbTvDto(id = 2, name = "Série", firstAirDate = "2011-04-17").toMediaItem()

        assertEquals("Série", item.title)
        assertEquals("2011", item.releaseYear)
        assertEquals(MediaType.TV, item.mediaType)
    }

    @Test
    fun `multi search drops unknown and missing media types`() {
        assertNull(TmdbMultiSearchItemDto(id = 1, mediaType = "collection").toMediaItem())
        assertNull(TmdbMultiSearchItemDto(id = 1, mediaType = null).toMediaItem())
    }

    @Test
    fun `multi search person uses the profile size and has no backdrop`() {
        val item = TmdbMultiSearchItemDto(
            id = 3,
            mediaType = "person",
            name = "Brad Pitt",
            profilePath = "/brad.jpg",
        ).toMediaItem()

        assertEquals(MediaType.PERSON, item?.mediaType)
        assertEquals("https://image.tmdb.org/t/p/w185/brad.jpg", item?.posterUrl)
        assertNull(item?.backdropUrl)
    }

    @Test
    fun `search results are grouped by media type`() {
        val results = listOf(
            TmdbMultiSearchItemDto(id = 1, mediaType = "movie"),
            TmdbMultiSearchItemDto(id = 2, mediaType = "tv"),
            TmdbMultiSearchItemDto(id = 3, mediaType = "person"),
            TmdbMultiSearchItemDto(id = 4, mediaType = "other"),
        ).toSearchResults()

        assertEquals(listOf(1), results.movies.map { it.id })
        assertEquals(listOf(2), results.shows.map { it.id })
        assertEquals(listOf(3), results.people.map { it.id })
    }

    @Test
    fun `trending keeps movies and shows only`() {
        val items = listOf(
            TmdbMultiSearchItemDto(id = 1, mediaType = "movie"),
            TmdbMultiSearchItemDto(id = 2, mediaType = "tv"),
            TmdbMultiSearchItemDto(id = 3, mediaType = "person"),
        ).toTrendingItems()

        assertEquals(listOf(1, 2), items.map { it.id })
    }

    @Test
    fun `blank overview becomes null so the UI can show its fallback`() {
        assertNull(TmdbMovieDetailsDto(id = 1, overview = "  ").toMediaDetails().overview)
        assertNull(TmdbTvDetailsDto(id = 1, overview = "").toMediaDetails().overview)
    }

    @Test
    fun `details fall back to similar when recommendations are empty`() {
        val details = TmdbMovieDetailsDto(
            id = 1,
            recommendations = TmdbPaginatedResponse(results = emptyList()),
            similar = TmdbPaginatedResponse(results = listOf(TmdbMovieDto(id = 9))),
        ).toMediaDetails()

        assertEquals(listOf(9), details.recommendations.map { it.id })
    }

    @Test
    fun `specials season is hidden when other seasons exist`() {
        val seasons = listOf(
            TmdbSeasonDto(id = 1, seasonNumber = 0),
            TmdbSeasonDto(id = 2, seasonNumber = 1),
        ).toVisibleSeasons()

        assertEquals(listOf(1), seasons.map { it.seasonNumber })
    }

    @Test
    fun `specials season is kept when it is the only one`() {
        val seasons = listOf(TmdbSeasonDto(id = 1, seasonNumber = 0)).toVisibleSeasons()

        assertEquals(listOf(0), seasons.map { it.seasonNumber })
    }

    @Test
    fun `episode uses the requested season when the field is absent`() {
        assertEquals(4, TmdbEpisodeDto(id = 1).toEpisode(fallbackSeasonNumber = 4).seasonNumber)
    }

    @Test
    fun `filmography is deduplicated by id and type and sorted by popularity`() {
        val person = TmdbPersonDetailsDto(
            id = 1,
            name = "Actrice",
            combinedCredits = TmdbCombinedCreditsDto(
                cast = listOf(
                    TmdbCastDto(id = 10, mediaType = "movie", title = "Peu connu", popularity = 1.0, voteAverage = 9.9),
                    TmdbCastDto(id = 20, mediaType = "tv", name = "Série connue", popularity = 50.0, voteAverage = 1.0),
                    TmdbCastDto(id = 20, mediaType = "tv", name = "Série connue", popularity = 50.0),
                    // Same id as the show above, but a different type: this is another title.
                    TmdbCastDto(id = 20, mediaType = "movie", title = "Film", popularity = 10.0),
                    TmdbCastDto(id = 30, mediaType = "person", name = "Ignoré", popularity = 99.0),
                ),
            ),
        ).toPersonDetails()

        assertEquals(
            listOf(20 to MediaType.TV, 20 to MediaType.MOVIE, 10 to MediaType.MOVIE),
            person.filmography.map { it.id to it.mediaType },
        )
    }

    // Trailer selection

    private fun trailer(key: String, language: String?, site: String = "YouTube", type: String = "Trailer") =
        Video(key = key, site = site, type = type, language = language)

    @Test
    fun `french trailer wins over english and others`() {
        val key = selectTrailerKey(listOf(trailer("de", "de"), trailer("en", "en"), trailer("fr", "fr")))
        assertEquals("fr", key)
    }

    @Test
    fun `english trailer wins when there is no french one`() {
        assertEquals("en", selectTrailerKey(listOf(trailer("de", "de"), trailer("en", "en"))))
    }

    @Test
    fun `any language is accepted as a last resort`() {
        assertEquals("de", selectTrailerKey(listOf(trailer("de", "de"))))
        assertEquals("none", selectTrailerKey(listOf(trailer("none", null))))
    }

    @Test
    fun `only youtube trailers qualify`() {
        val key = selectTrailerKey(
            listOf(
                trailer("vimeo", "fr", site = "Vimeo"),
                trailer("teaser", "fr", type = "Teaser"),
            ),
        )
        assertNull(key)
        assertNull(selectTrailerKey(emptyList()))
    }

    // Certifications

    @Test
    fun `movie certification comes from the first non blank french release`() {
        val dates = TmdbReleaseDatesDto(
            results = listOf(
                TmdbReleaseDateResultDto("US", listOf(TmdbReleaseDateItemDto("R"))),
                TmdbReleaseDateResultDto(
                    "FR",
                    listOf(TmdbReleaseDateItemDto(""), TmdbReleaseDateItemDto("12")),
                ),
            ),
        )
        assertEquals("12", frenchMovieCertification(dates))
    }

    @Test
    fun `movie without a french certification has none`() {
        val onlyUs = TmdbReleaseDatesDto(
            results = listOf(TmdbReleaseDateResultDto("US", listOf(TmdbReleaseDateItemDto("R")))),
        )
        val blankFr = TmdbReleaseDatesDto(
            results = listOf(TmdbReleaseDateResultDto("FR", listOf(TmdbReleaseDateItemDto("")))),
        )
        assertNull(frenchMovieCertification(onlyUs))
        assertNull(frenchMovieCertification(blankFr))
        assertNull(frenchMovieCertification(null))
    }

    @Test
    fun `not rated is not a certification`() {
        val dates = TmdbReleaseDatesDto(
            results = listOf(
                TmdbReleaseDateResultDto(
                    "FR",
                    listOf(TmdbReleaseDateItemDto("NR"), TmdbReleaseDateItemDto("12")),
                ),
            ),
        )
        val onlyNotRated = TmdbContentRatingsDto(results = listOf(TmdbContentRatingItemDto("FR", "NR")))

        assertEquals("12", frenchMovieCertification(dates))
        assertNull(frenchTvCertification(onlyNotRated))
    }

    @Test
    fun `tv certification comes from the french content rating`() {
        val ratings = TmdbContentRatingsDto(
            results = listOf(
                TmdbContentRatingItemDto("US", "TV-MA"),
                TmdbContentRatingItemDto("FR", "16"),
            ),
        )
        assertEquals("16", frenchTvCertification(ratings))
        assertNull(frenchTvCertification(TmdbContentRatingsDto(results = emptyList())))
        assertNull(frenchTvCertification(null))
    }

    @Test
    fun `cast is limited to fifteen members`() {
        val details = TmdbMovieDetailsDto(
            id = 1,
            credits = TmdbCreditsDto(
                cast = (1..20).map { TmdbCastDto(id = it, name = "Acteur $it") },
            ),
        ).toMediaDetails()

        assertEquals(15, details.cast.size)
        assertTrue(details.cast.first().name.endsWith("1"))
    }
}
