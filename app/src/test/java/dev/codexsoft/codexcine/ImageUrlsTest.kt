package dev.codexsoft.codexcine

import dev.codexsoft.codexcine.core.ImageUrls
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ImageUrlsTest {

    @After
    fun restoreDefaultBase() {
        ImageUrls.setBaseUrl(ImageUrls.DEFAULT_BASE_URL)
    }

    @Test
    fun `null or blank path gives null`() {
        assertNull(ImageUrls.poster(null))
        assertNull(ImageUrls.backdrop(""))
        assertNull(ImageUrls.background("  "))
        assertNull(ImageUrls.profile(null))
    }

    @Test
    fun `each helper uses its own size`() {
        assertEquals("https://image.tmdb.org/t/p/w342/a.jpg", ImageUrls.poster("/a.jpg"))
        assertEquals("https://image.tmdb.org/t/p/w780/a.jpg", ImageUrls.backdrop("/a.jpg"))
        assertEquals("https://image.tmdb.org/t/p/w1280/a.jpg", ImageUrls.background("/a.jpg"))
        assertEquals("https://image.tmdb.org/t/p/w185/a.jpg", ImageUrls.profile("/a.jpg"))
    }

    @Test
    fun `configured base url is applied with or without a trailing slash`() {
        ImageUrls.setBaseUrl("https://cdn.example.org/t/p")
        assertEquals("https://cdn.example.org/t/p/w342/a.jpg", ImageUrls.poster("/a.jpg"))

        ImageUrls.setBaseUrl("https://other.example.org/")
        assertEquals("https://other.example.org/w342/a.jpg", ImageUrls.poster("/a.jpg"))
    }

    @Test
    fun `blank configuration keeps the current base`() {
        ImageUrls.setBaseUrl(null)
        ImageUrls.setBaseUrl("")
        assertEquals("https://image.tmdb.org/t/p/w342/a.jpg", ImageUrls.poster("/a.jpg"))
    }
}
