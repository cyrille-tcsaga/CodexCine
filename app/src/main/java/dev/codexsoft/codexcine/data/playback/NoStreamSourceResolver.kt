package dev.codexsoft.codexcine.data.playback

class NoStreamSourceResolver : StreamSourceResolver {
    override suspend fun resolve(request: PlaybackRequest): StreamSource? {
        return null
    }
}
