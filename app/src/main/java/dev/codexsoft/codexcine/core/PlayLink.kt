package dev.codexsoft.codexcine.core

import dev.codexsoft.codexcine.domain.model.MediaType
import java.net.URI
import java.net.URISyntaxException
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * The links other apps may open, handled by PlayLinkActivity:
 * - `codexcine://play/{movie|tv}/{id}?s=2&e=5&t=Title` starts playback (the home screen's "Play next" row);
 * - `codexcine://details/{movie|tv}/{id}?t=Title` opens a title's page (the system search).
 *
 * Any app can send them, so parsing accepts numbers in range, the two playable types and a short title,
 * and nothing else.
 */
sealed interface AppLink {
    companion object {
        fun parse(link: String?): AppLink? = PlayLink.parse(link) ?: DetailsLink.parse(link)
    }
}

data class PlayLink(
    val mediaType: MediaType,
    val mediaId: Int,
    val season: Int? = null,
    val episode: Int? = null,
    val title: String = "",
) : AppLink {
    fun toUri(): String = LinkFormat.build(HOST, mediaType, mediaId, listOfNotNull(
        season?.let { "s=$it" },
        episode?.let { "e=$it" },
    ), title)

    companion object {
        const val HOST = "play"

        fun parse(link: String?): PlayLink? {
            val parsed = LinkFormat.parse(link, HOST) ?: return null
            val season = parsed.query["s"]?.let { LinkFormat.number(it) ?: return null }
            val episode = parsed.query["e"]?.let { LinkFormat.number(it) ?: return null }
            // A show needs both numbers, a movie neither.
            if (parsed.type == MediaType.TV && (season == null || episode == null || episode <= 0)) return null
            if (parsed.type == MediaType.MOVIE && (season != null || episode != null)) return null
            return PlayLink(parsed.type, parsed.id, season, episode, parsed.title)
        }
    }
}

data class DetailsLink(
    val mediaType: MediaType,
    val mediaId: Int,
    val title: String = "",
) : AppLink {
    fun toUri(): String = LinkFormat.build(HOST, mediaType, mediaId, emptyList(), title)

    companion object {
        const val HOST = "details"

        fun parse(link: String?): DetailsLink? {
            val parsed = LinkFormat.parse(link, HOST) ?: return null
            if (parsed.query.keys.any { it != "t" }) return null
            return DetailsLink(parsed.type, parsed.id, parsed.title)
        }
    }
}

/** The shared shape of the links, and its strict reading. */
internal object LinkFormat {
    const val SCHEME = "codexcine"
    private const val MAX_TITLE = 200
    private const val MAX_NUMBER = 100_000_000

    // By name: the Charset overloads of URLEncoder and URLDecoder need API 33.
    private const val UTF_8 = "UTF-8"

    class Parsed(val type: MediaType, val id: Int, val query: Map<String, String>, val title: String)

    fun build(host: String, type: MediaType, id: Int, params: List<String>, title: String): String = buildString {
        append(SCHEME).append("://").append(host).append('/').append(type.value).append('/').append(id)
        val query = params + listOfNotNull(
            title.takeIf { it.isNotEmpty() }?.let { "t=" + URLEncoder.encode(it.take(MAX_TITLE), UTF_8) },
        )
        if (query.isNotEmpty()) append('?').append(query.joinToString("&"))
    }

    fun parse(link: String?, host: String): Parsed? {
        val uri = try {
            URI(link ?: return null)
        } catch (_: URISyntaxException) {
            return null
        }
        if (uri.scheme != SCHEME || uri.host != host) return null
        val parts = uri.path.orEmpty().trim('/').split('/')
        if (parts.size != 2) return null
        val type = MediaType.fromValue(parts[0])?.takeIf { it == MediaType.MOVIE || it == MediaType.TV } ?: return null
        val id = number(parts[1])?.takeIf { it > 0 } ?: return null
        val query = uri.rawQuery.orEmpty().split('&')
            .mapNotNull { pair -> pair.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }
            .toMap()
        val title = try {
            query["t"]?.let { URLDecoder.decode(it, UTF_8) }.orEmpty()
        } catch (_: IllegalArgumentException) {
            return null
        }
        return Parsed(type, id, query, title.filterNot(Char::isISOControl).take(MAX_TITLE))
    }

    fun number(text: String): Int? = text.toIntOrNull()?.takeIf { it in 0..MAX_NUMBER }
}
