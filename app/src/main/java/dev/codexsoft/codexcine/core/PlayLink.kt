package dev.codexsoft.codexcine.core

import dev.codexsoft.codexcine.domain.model.MediaType
import java.net.URI
import java.net.URISyntaxException
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * What the system home screen opens: `codexcine://play/{movie|tv}/{id}?s=2&e=5&t=Title`.
 *
 * Any app can send this link, so [parse] accepts numbers in range, the two playable types and a
 * short title, and nothing else.
 */
data class PlayLink(
    val mediaType: MediaType,
    val mediaId: Int,
    val season: Int? = null,
    val episode: Int? = null,
    val title: String = "",
) {
    fun toUri(): String = buildString {
        append(SCHEME).append("://").append(HOST).append('/')
        append(mediaType.value).append('/').append(mediaId)
        val query = listOfNotNull(
            season?.let { "s=$it" },
            episode?.let { "e=$it" },
            title.takeIf { it.isNotEmpty() }?.let { "t=" + URLEncoder.encode(it.take(MAX_TITLE), UTF_8) },
        )
        if (query.isNotEmpty()) append('?').append(query.joinToString("&"))
    }

    companion object {
        const val SCHEME = "codexcine"
        const val HOST = "play"
        private const val MAX_TITLE = 200

        // By name: the Charset overloads of URLEncoder and URLDecoder need API 33.
        private const val UTF_8 = "UTF-8"
        private const val MAX_NUMBER = 100_000_000

        fun parse(link: String?): PlayLink? {
            val uri = try {
                URI(link ?: return null)
            } catch (_: URISyntaxException) {
                return null
            }
            if (uri.scheme != SCHEME || uri.host != HOST) return null
            val parts = uri.path.orEmpty().trim('/').split('/')
            if (parts.size != 2) return null
            val type = MediaType.fromValue(parts[0])?.takeIf { it == MediaType.MOVIE || it == MediaType.TV }
                ?: return null
            val id = parts[1].toNumber() ?: return null
            if (id <= 0) return null

            val query = uri.rawQuery.orEmpty().split('&')
                .mapNotNull { pair -> pair.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }
                .toMap()
            val season = query["s"]?.let { it.toNumber() ?: return null }
            val episode = query["e"]?.let { it.toNumber() ?: return null }
            // A show needs both numbers, a movie neither.
            if (type == MediaType.TV && (season == null || episode == null || episode <= 0)) return null
            if (type == MediaType.MOVIE && (season != null || episode != null)) return null
            val title = try {
                query["t"]?.let { URLDecoder.decode(it, UTF_8) }.orEmpty()
            } catch (_: IllegalArgumentException) {
                return null
            }
            return PlayLink(type, id, season, episode, title.filterNot(Char::isISOControl).take(MAX_TITLE))
        }

        private fun String.toNumber(): Int? = toIntOrNull()?.takeIf { it in 0..MAX_NUMBER }
    }
}
