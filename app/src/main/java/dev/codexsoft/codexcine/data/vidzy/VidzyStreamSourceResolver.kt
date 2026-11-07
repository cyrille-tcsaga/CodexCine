package dev.codexsoft.codexcine.data.vidzy

import android.util.Log
import dev.codexsoft.codexcine.core.AppResult
import dev.codexsoft.codexcine.data.playback.PlaybackRequest
import dev.codexsoft.codexcine.data.playback.StreamServiceException
import dev.codexsoft.codexcine.data.playback.StreamSource
import dev.codexsoft.codexcine.data.playback.StreamSourceResolver
import dev.codexsoft.codexcine.domain.model.MediaType

class VidzyStreamSourceResolver(
    private val repository: VidzyRepository,
    private val baseUrl: String = VidzyUrlBuilder.BASE_URL,
) : StreamSourceResolver {

    override suspend fun resolve(request: PlaybackRequest): StreamSource? {
        val availability = when (val result = repository.availability(request.mediaId)) {
            is AppResult.Error -> throw StreamServiceException(result.message ?: "Vidzy is unreachable")
            is AppResult.Success -> result.data
        }
        if (!availability.isAvailable) return null

        warnOnTypeMismatch(request, availability.detectedType)
        return StreamSource.WebEmbed(
            url = VidzyUrlBuilder.build(
                mediaType = request.mediaType,
                tmdbId = request.mediaId,
                season = request.seasonNumber,
                episode = request.episodeNumber,
                baseUrl = baseUrl,
            ),
            languages = availability.languages,
            providerName = PROVIDER_NAME,
        )
    }

    /** A TMDB movie and a TMDB show can share an id, so the request stays the authority. */
    private fun warnOnTypeMismatch(request: PlaybackRequest, detectedType: String?) {
        val detected = when (detectedType?.lowercase()) {
            "movie" -> MediaType.MOVIE
            "tv", "serie", "series" -> MediaType.TV
            else -> return
        }
        if (detected != request.mediaType) {
            Log.w(TAG, "Vidzy detected $detected for ${request.mediaId}, requested ${request.mediaType}")
        }
    }

    private companion object {
        const val TAG = "VidzyResolver"
        const val PROVIDER_NAME = "Vidzy"
    }
}
