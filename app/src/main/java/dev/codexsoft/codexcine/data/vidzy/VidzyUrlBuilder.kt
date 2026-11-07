package dev.codexsoft.codexcine.data.vidzy

import dev.codexsoft.codexcine.domain.model.MediaType
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * Builds player URLs. `info` and `autonext` are deliberately absent: the native overlay shows the
 * metadata and drives next-episode logic, so that progress saving stays consistent.
 */
object VidzyUrlBuilder {

    const val BASE_URL = "https://vidzy.org/"

    fun build(
        mediaType: MediaType,
        tmdbId: Int,
        season: Int? = null,
        episode: Int? = null,
        baseUrl: String = BASE_URL,
    ): String {
        val builder = baseUrl.toHttpUrl().newBuilder()
        if (mediaType == MediaType.TV) {
            builder.addPathSegment("serie")
                .addPathSegment(tmdbId.toString())
                .addPathSegment((season ?: 1).toString())
                .addPathSegment((episode ?: 1).toString())
        } else {
            builder.addPathSegment("movie").addPathSegment(tmdbId.toString())
        }
        return builder.addPlayerOptions().build().toString()
    }

    private fun HttpUrl.Builder.addPlayerOptions() = apply {
        addQueryParameter("autoplay", "1")
        addQueryParameter("color", "F5A623")
        addQueryParameter("hide", "download,cast,fullscreen")
    }
}
