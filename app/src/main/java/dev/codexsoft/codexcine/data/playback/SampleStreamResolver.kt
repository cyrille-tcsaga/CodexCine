package dev.codexsoft.codexcine.data.playback

/** Plays a public test clip for every title, to verify the player end to end. Debug builds only. */
class SampleStreamResolver : StreamSourceResolver {
    override suspend fun resolve(request: PlaybackRequest): StreamSource = StreamSource(
        uri = SAMPLE_URI,
        mimeType = "video/mp4",
    )

    private companion object {
        // The sample from the Leanback template now answers HTTP 403; this one is Media3's own test media.
        const val SAMPLE_URI = "https://storage.googleapis.com/exoplayer-test-media-0/BigBuckBunny_320x180.mp4"
    }
}
