package dev.codexsoft.codexcine.data.remote

import dev.codexsoft.codexcine.data.remote.dto.*
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

private const val LATEST_MIN_VOTES = 10

interface TmdbApi {

    @GET("configuration")
    suspend fun getConfiguration(): TmdbConfigurationDto

    @GET("trending/all/week")
    suspend fun getTrending(@Query("page") page: Int = 1): TmdbPaginatedResponse<TmdbMultiSearchItemDto>

    @GET("movie/popular")
    suspend fun getPopularMovies(@Query("page") page: Int = 1): TmdbPaginatedResponse<TmdbMovieDto>

    @GET("movie/top_rated")
    suspend fun getTopRatedMovies(@Query("page") page: Int = 1): TmdbPaginatedResponse<TmdbMovieDto>

    @GET("movie/now_playing")
    suspend fun getNowPlayingMovies(@Query("page") page: Int = 1): TmdbPaginatedResponse<TmdbMovieDto>

    @GET("movie/upcoming")
    suspend fun getUpcomingMovies(@Query("page") page: Int = 1): TmdbPaginatedResponse<TmdbMovieDto>

    @GET("tv/popular")
    suspend fun getPopularTv(@Query("page") page: Int = 1): TmdbPaginatedResponse<TmdbTvDto>

    @GET("tv/top_rated")
    suspend fun getTopRatedTv(@Query("page") page: Int = 1): TmdbPaginatedResponse<TmdbTvDto>

    @GET("tv/on_the_air")
    suspend fun getOnTheAirTv(@Query("page") page: Int = 1): TmdbPaginatedResponse<TmdbTvDto>

    @GET("discover/movie")
    suspend fun discoverMoviesByGenre(
        @Query("with_genres") genreId: Int,
        @Query("sort_by") sortBy: String = "popularity.desc",
        @Query("page") page: Int = 1
    ): TmdbPaginatedResponse<TmdbMovieDto>

    /** Latest releases up to [releasedBefore] (YYYY-MM-DD). The vote floor keeps out obscure entries. */
    @GET("discover/movie")
    suspend fun discoverLatestMovies(
        @Query("primary_release_date.lte") releasedBefore: String,
        @Query("sort_by") sortBy: String = "primary_release_date.desc",
        @Query("vote_count.gte") minVotes: Int = LATEST_MIN_VOTES,
        @Query("page") page: Int = 1
    ): TmdbPaginatedResponse<TmdbMovieDto>

    /** Latest shows first aired up to [airedBefore] (YYYY-MM-DD). The vote floor keeps out obscure entries. */
    @GET("discover/tv")
    suspend fun discoverLatestTv(
        @Query("first_air_date.lte") airedBefore: String,
        @Query("sort_by") sortBy: String = "first_air_date.desc",
        @Query("vote_count.gte") minVotes: Int = LATEST_MIN_VOTES,
        @Query("page") page: Int = 1
    ): TmdbPaginatedResponse<TmdbTvDto>

    @GET("movie/{id}")
    suspend fun getMovieDetails(
        @Path("id") id: Int,
        @Query("append_to_response") appendToResponse: String = "credits,videos,recommendations,similar,release_dates",
        @Query("include_video_language") includeVideoLanguage: String = "fr,en,null"
    ): TmdbMovieDetailsDto

    @GET("tv/{id}")
    suspend fun getTvDetails(
        @Path("id") id: Int,
        @Query("append_to_response") appendToResponse: String = "credits,videos,recommendations,similar,content_ratings",
        @Query("include_video_language") includeVideoLanguage: String = "fr,en,null"
    ): TmdbTvDetailsDto

    @GET("tv/{id}/season/{season_number}")
    suspend fun getTvSeason(
        @Path("id") tvId: Int,
        @Path("season_number") seasonNumber: Int
    ): TmdbSeasonDetailsDto

    @GET("search/multi")
    suspend fun searchMulti(
        @Query("query") query: String,
        @Query("page") page: Int = 1
    ): TmdbPaginatedResponse<TmdbMultiSearchItemDto>

    @GET("person/{id}")
    suspend fun getPersonDetails(
        @Path("id") personId: Int,
        @Query("append_to_response") appendToResponse: String = "combined_credits"
    ): TmdbPersonDetailsDto
}
