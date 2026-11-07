package dev.codexsoft.codexcine

import dev.codexsoft.codexcine.data.playback.NoStreamSourceResolver
import dev.codexsoft.codexcine.data.playback.PlaybackRequest
import dev.codexsoft.codexcine.domain.model.MediaType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNull
import org.junit.Test

class NoStreamSourceResolverTest {

    @Test
    fun `always resolves to null`() = runTest {
        val request = PlaybackRequest(mediaId = 1, mediaType = MediaType.MOVIE, title = "Titre")
        assertNull(NoStreamSourceResolver().resolve(request))
    }
}
