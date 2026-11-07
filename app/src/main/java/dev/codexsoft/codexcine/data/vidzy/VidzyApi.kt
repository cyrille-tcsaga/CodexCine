package dev.codexsoft.codexcine.data.vidzy

import retrofit2.http.GET
import retrofit2.http.Path

interface VidzyApi {
    @GET("api/{tmdbId}")
    suspend fun availability(@Path("tmdbId") tmdbId: Int): VidzyAvailabilityDto
}
