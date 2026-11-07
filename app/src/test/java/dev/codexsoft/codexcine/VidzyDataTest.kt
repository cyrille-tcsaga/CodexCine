package dev.codexsoft.codexcine

import dev.codexsoft.codexcine.core.AppResult
import dev.codexsoft.codexcine.core.ErrorKind
import dev.codexsoft.codexcine.data.playback.PlaybackRequest
import dev.codexsoft.codexcine.data.playback.StreamServiceException
import dev.codexsoft.codexcine.data.playback.StreamSource
import dev.codexsoft.codexcine.data.vidzy.VidzyApi
import dev.codexsoft.codexcine.data.vidzy.VidzyAvailabilityDto
import dev.codexsoft.codexcine.data.vidzy.VidzyJson
import dev.codexsoft.codexcine.data.vidzy.VidzyRepository
import dev.codexsoft.codexcine.data.vidzy.VidzyStreamSourceResolver
import dev.codexsoft.codexcine.data.vidzy.VidzyUrlBuilder
import dev.codexsoft.codexcine.domain.model.MediaType
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class VidzyUrlBuilderTest {

    @Test
    fun `movie url`() {
        val url = VidzyUrlBuilder.build(MediaType.MOVIE, 550).toHttpUrl()
        assertEquals(listOf("movie", "550"), url.pathSegments)
        assertEquals("vidzy.org", url.host)
        assertEquals("https", url.scheme)
    }

    @Test
    fun `episode url uses the serie segment`() {
        val url = VidzyUrlBuilder.build(MediaType.TV, 1399, season = 2, episode = 5).toHttpUrl()
        assertEquals(listOf("serie", "1399", "2", "5"), url.pathSegments)
    }

    @Test
    fun `missing season and episode default to the first episode`() {
        val url = VidzyUrlBuilder.build(MediaType.TV, 1399).toHttpUrl()
        assertEquals(listOf("serie", "1399", "1", "1"), url.pathSegments)
    }

    @Test
    fun `player options are present and encoded`() {
        val raw = VidzyUrlBuilder.build(MediaType.MOVIE, 550)
        val url = raw.toHttpUrl()
        assertEquals("1", url.queryParameter("autoplay"))
        assertEquals("F5A623", url.queryParameter("color"))
        assertEquals("download,cast,fullscreen", url.queryParameter("hide"))
        assertTrue(raw, "hide=download%2Ccast%2Cfullscreen" in raw)
    }

    @Test
    fun `info and autonext are never sent`() {
        val url = VidzyUrlBuilder.build(MediaType.TV, 1399, 1, 1).toHttpUrl()
        assertNull(url.queryParameter("info"))
        assertNull(url.queryParameter("autonext"))
    }
}

class VidzyAvailabilityDtoTest {

    private fun parse(json: String) = VidzyJson.decodeFromString<VidzyAvailabilityDto>(json)

    @Test
    fun `full response as served by the api`() {
        val dto = parse(
            """{"available":true,"tmdb_id":550,"detectedType":"movie","title":"Fight Club","year":1999,
               "poster":"https:\/\/image.tmdb.org\/t.jpg","languages":["vf","vostfr"],
               "embed":"https:\/\/vidzy.org\/movie\/550"}""",
        )
        assertEquals(true, dto.available)
        assertEquals("movie", dto.detectedType)
        assertEquals("Fight Club", dto.title)
        assertEquals(listOf("vf", "vostfr"), dto.languages)
        assertEquals("https://vidzy.org/movie/550", dto.embed)
    }

    @Test
    fun `not available response has only two fields`() {
        val dto = parse("""{"available":false,"tmdb_id":999999999}""")
        assertEquals(false, dto.available)
        assertNull(dto.languages)
        assertNull(dto.embed)
    }

    @Test
    fun `empty object and absent languages`() {
        assertNull(parse("{}").available)
        assertNull(parse("""{"available":true}""").languages)
    }

    @Test
    fun `unknown extra fields and an unexpected year type are ignored`() {
        val dto = parse("""{"available":true,"year":"","seasons":[{"season":1,"episodes":[1,2]}],"x":{"y":1}}""")
        assertEquals(true, dto.available)
    }

    @Test
    fun `explicit nulls keep the defaults`() {
        val dto = parse("""{"available":null,"languages":null}""")
        assertNull(dto.available)
        assertNull(dto.languages)
    }
}

class VidzyRepositoryTest {

    private class FakeApi(var answer: () -> VidzyAvailabilityDto) : VidzyApi {
        var calls = 0
        override suspend fun availability(tmdbId: Int): VidzyAvailabilityDto {
            calls++
            return answer()
        }
    }

    private val available = VidzyAvailabilityDto(available = true, languages = listOf("vf", " "))

    @Test
    fun `successes are cached for 15 minutes`() = runTest {
        var now = 0L
        val api = FakeApi { available }
        val repository = VidzyRepository(lazyOf(api), clock = { now })

        repository.availability(1)
        now = 14L * 60_000L
        repository.availability(1)
        assertEquals(1, api.calls)

        now = 15L * 60_000L
        repository.availability(1)
        assertEquals(2, api.calls)
    }

    @Test
    fun `the cache keeps the 50 most recently used entries`() = runTest {
        val api = FakeApi { available }
        val repository = VidzyRepository(lazyOf(api), clock = { 0L })

        (1..50).forEach { repository.availability(it) }
        repository.availability(1) // Now the most recent, so 2 is the eldest.
        repository.availability(51)
        val before = api.calls

        repository.availability(1)
        assertEquals(before, api.calls)
        repository.availability(2)
        assertEquals(before + 1, api.calls)
    }

    @Test
    fun `failures are reported and not cached`() = runTest {
        val api = FakeApi { throw IOException("offline") }
        val repository = VidzyRepository(lazyOf(api), clock = { 0L })

        val result = repository.availability(1)
        assertEquals(ErrorKind.NO_CONNECTION, (result as AppResult.Error).kind)

        api.answer = { available }
        assertTrue(repository.availability(1) is AppResult.Success)
    }

    @Test
    fun `blank languages are dropped`() = runTest {
        val result = VidzyRepository(lazyOf(FakeApi { available }), clock = { 0L }).availability(1)
        assertEquals(listOf("vf"), (result as AppResult.Success).data.languages)
    }
}

class VidzyStreamSourceResolverTest {

    private val movie = PlaybackRequest(550, MediaType.MOVIE, "Fight Club")
    private val episode = PlaybackRequest(1399, MediaType.TV, "GoT", seasonNumber = 2, episodeNumber = 3)

    private fun resolver(answer: () -> VidzyAvailabilityDto) = VidzyStreamSourceResolver(
        VidzyRepository(
            lazyOf(
                object : VidzyApi {
                    override suspend fun availability(tmdbId: Int) = answer()
                },
            ),
        ),
    )

    @Test
    fun `available becomes a web embed`() = runTest {
        val source = resolver { VidzyAvailabilityDto(available = true, languages = listOf("vf")) }.resolve(movie)
        val embed = source as StreamSource.WebEmbed
        assertEquals(listOf("movie", "550"), embed.url.toHttpUrl().pathSegments)
        assertEquals(listOf("vf"), embed.languages)
        assertEquals("Vidzy", embed.providerName)
    }

    @Test
    fun `not available and absent flag become null`() = runTest {
        assertNull(resolver { VidzyAvailabilityDto(available = false) }.resolve(movie))
        assertNull(resolver { VidzyAvailabilityDto() }.resolve(movie))
    }

    @Test
    fun `network error becomes the typed error`() {
        val resolver = resolver { throw IOException("offline") }
        assertThrows(StreamServiceException::class.java) {
            kotlinx.coroutines.runBlocking { resolver.resolve(movie) }
        }
    }

    @Test
    fun `a contradicting detected type still follows the request`() = runTest {
        val source = resolver { VidzyAvailabilityDto(available = true, detectedType = "movie") }.resolve(episode)
        val url = (source as StreamSource.WebEmbed).url.toHttpUrl()
        assertEquals(listOf("serie", "1399", "2", "3"), url.pathSegments)
        assertFalse("movie" in url.pathSegments)
    }
}
