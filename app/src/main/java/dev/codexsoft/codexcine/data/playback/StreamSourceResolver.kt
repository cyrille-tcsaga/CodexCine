package dev.codexsoft.codexcine.data.playback

import dev.codexsoft.codexcine.domain.model.MediaType
import java.io.IOException

data class PlaybackRequest(
    val mediaId: Int,
    val mediaType: MediaType,
    val title: String,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
)

sealed interface StreamSource {

    /** A web page that embeds its own player, played in a hardened WebView. */
    data class WebEmbed(
        val url: String,
        val languages: List<String> = emptyList(), // e.g. ["vf", "vostfr"], display only
        val providerName: String,
    ) : StreamSource
}

/** The provider could not be reached, which unlike a null source is worth retrying. */
class StreamServiceException(message: String, cause: Throwable? = null) : IOException(message, cause)

/** The single extension point for playback: implement it and register it in `AppContainer`. */
interface StreamSourceResolver {
    /**
     * Returns a playable source, or null when the provider has nothing for this title.
     *
     * @throws StreamServiceException when the provider cannot be reached.
     */
    suspend fun resolve(request: PlaybackRequest): StreamSource?
}
