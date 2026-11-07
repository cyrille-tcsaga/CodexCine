package dev.codexsoft.codexcine.data.playback

import dev.codexsoft.codexcine.domain.model.MediaType

data class PlaybackRequest(
    val mediaId: Int,
    val mediaType: MediaType,
    val title: String,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
)

data class StreamSource(
    val uri: String,
    val mimeType: String? = null, // e.g. MimeTypes.APPLICATION_M3U8
    val headers: Map<String, String> = emptyMap(),
)

/** The single extension point for playback: implement it and register it in `AppContainer`. */
interface StreamSourceResolver {
    /** Returns a playable source, or null when no source is available. */
    suspend fun resolve(request: PlaybackRequest): StreamSource?
}
