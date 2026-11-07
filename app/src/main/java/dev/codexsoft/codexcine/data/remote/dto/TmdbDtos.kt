package dev.codexsoft.codexcine.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TmdbPaginatedResponse<T>(
    @SerialName("page") val page: Int? = null,
    @SerialName("results") val results: List<T>? = null,
    @SerialName("total_pages") val totalPages: Int? = null,
    @SerialName("total_results") val totalResults: Int? = null,
)

@Serializable
data class TmdbMovieDto(
    @SerialName("id") val id: Int,
    @SerialName("title") val title: String? = null,
    @SerialName("original_title") val originalTitle: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("vote_average") val voteAverage: Double? = null,
    @SerialName("vote_count") val voteCount: Int? = null,
    @SerialName("overview") val overview: String? = null,
)

@Serializable
data class TmdbTvDto(
    @SerialName("id") val id: Int,
    @SerialName("name") val name: String? = null,
    @SerialName("original_name") val originalName: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
    @SerialName("vote_average") val voteAverage: Double? = null,
    @SerialName("vote_count") val voteCount: Int? = null,
    @SerialName("overview") val overview: String? = null,
)

@Serializable
data class TmdbMultiSearchItemDto(
    @SerialName("id") val id: Int,
    @SerialName("media_type") val mediaType: String? = null,
    @SerialName("title") val title: String? = null,
    @SerialName("name") val name: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("profile_path") val profilePath: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
    @SerialName("vote_average") val voteAverage: Double? = null,
    @SerialName("vote_count") val voteCount: Int? = null,
    @SerialName("overview") val overview: String? = null,
    @SerialName("popularity") val popularity: Double? = null,
)

@Serializable
data class TmdbMovieDetailsDto(
    @SerialName("id") val id: Int,
    @SerialName("title") val title: String? = null,
    @SerialName("overview") val overview: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("runtime") val runtime: Int? = null,
    @SerialName("vote_average") val voteAverage: Double? = null,
    @SerialName("vote_count") val voteCount: Int? = null,
    @SerialName("genres") val genres: List<TmdbGenreDto>? = null,
    @SerialName("credits") val credits: TmdbCreditsDto? = null,
    @SerialName("videos") val videos: TmdbVideosDto? = null,
    @SerialName("recommendations") val recommendations: TmdbPaginatedResponse<TmdbMovieDto>? = null,
    @SerialName("similar") val similar: TmdbPaginatedResponse<TmdbMovieDto>? = null,
    @SerialName("release_dates") val releaseDates: TmdbReleaseDatesDto? = null,
)

@Serializable
data class TmdbTvDetailsDto(
    @SerialName("id") val id: Int,
    @SerialName("name") val name: String? = null,
    @SerialName("overview") val overview: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
    @SerialName("number_of_seasons") val numberOfSeasons: Int? = null,
    @SerialName("vote_average") val voteAverage: Double? = null,
    @SerialName("vote_count") val voteCount: Int? = null,
    @SerialName("genres") val genres: List<TmdbGenreDto>? = null,
    @SerialName("seasons") val seasons: List<TmdbSeasonDto>? = null,
    @SerialName("credits") val credits: TmdbCreditsDto? = null,
    @SerialName("videos") val videos: TmdbVideosDto? = null,
    @SerialName("recommendations") val recommendations: TmdbPaginatedResponse<TmdbTvDto>? = null,
    @SerialName("similar") val similar: TmdbPaginatedResponse<TmdbTvDto>? = null,
    @SerialName("content_ratings") val contentRatings: TmdbContentRatingsDto? = null,
)

@Serializable
data class TmdbGenreDto(
    @SerialName("id") val id: Int,
    @SerialName("name") val name: String? = null,
)

@Serializable
data class TmdbCreditsDto(
    @SerialName("cast") val cast: List<TmdbCastDto>? = null,
)

@Serializable
data class TmdbCastDto(
    @SerialName("id") val id: Int,
    @SerialName("name") val name: String? = null,
    @SerialName("character") val character: String? = null,
    @SerialName("profile_path") val profilePath: String? = null,
    @SerialName("media_type") val mediaType: String? = null,
    @SerialName("title") val title: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
    @SerialName("vote_average") val voteAverage: Double? = null,
    @SerialName("popularity") val popularity: Double? = null,
)

@Serializable
data class TmdbVideosDto(
    @SerialName("results") val results: List<TmdbVideoDto>? = null,
)

@Serializable
data class TmdbVideoDto(
    @SerialName("id") val id: String? = null,
    @SerialName("key") val key: String? = null,
    @SerialName("name") val name: String? = null,
    @SerialName("site") val site: String? = null,
    @SerialName("type") val type: String? = null,
    @SerialName("iso_639_1") val iso6391: String? = null,
)

@Serializable
data class TmdbReleaseDatesDto(
    @SerialName("results") val results: List<TmdbReleaseDateResultDto>? = null,
)

@Serializable
data class TmdbReleaseDateResultDto(
    @SerialName("iso_3166_1") val iso31661: String? = null,
    @SerialName("release_dates") val releaseDates: List<TmdbReleaseDateItemDto>? = null,
)

@Serializable
data class TmdbReleaseDateItemDto(
    @SerialName("certification") val certification: String? = null,
    @SerialName("type") val type: Int? = null,
)

@Serializable
data class TmdbContentRatingsDto(
    @SerialName("results") val results: List<TmdbContentRatingItemDto>? = null,
)

@Serializable
data class TmdbContentRatingItemDto(
    @SerialName("iso_3166_1") val iso31661: String? = null,
    @SerialName("rating") val rating: String? = null,
)

@Serializable
data class TmdbSeasonDto(
    @SerialName("id") val id: Int,
    @SerialName("season_number") val seasonNumber: Int? = null,
    @SerialName("name") val name: String? = null,
    @SerialName("episode_count") val episodeCount: Int? = null,
    @SerialName("poster_path") val posterPath: String? = null,
)

@Serializable
data class TmdbSeasonDetailsDto(
    @SerialName("id") val id: Int? = null,
    @SerialName("season_number") val seasonNumber: Int? = null,
    @SerialName("name") val name: String? = null,
    @SerialName("overview") val overview: String? = null,
    @SerialName("episodes") val episodes: List<TmdbEpisodeDto>? = null,
)

@Serializable
data class TmdbEpisodeDto(
    @SerialName("id") val id: Int,
    @SerialName("episode_number") val episodeNumber: Int? = null,
    @SerialName("season_number") val seasonNumber: Int? = null,
    @SerialName("name") val name: String? = null,
    @SerialName("overview") val overview: String? = null,
    @SerialName("still_path") val stillPath: String? = null,
    @SerialName("air_date") val airDate: String? = null,
    @SerialName("runtime") val runtime: Int? = null,
)

@Serializable
data class TmdbPersonDetailsDto(
    @SerialName("id") val id: Int,
    @SerialName("name") val name: String? = null,
    @SerialName("biography") val biography: String? = null,
    @SerialName("profile_path") val profilePath: String? = null,
    @SerialName("combined_credits") val combinedCredits: TmdbCombinedCreditsDto? = null,
)

@Serializable
data class TmdbCombinedCreditsDto(
    @SerialName("cast") val cast: List<TmdbCastDto>? = null,
)

@Serializable
data class TmdbConfigurationDto(
    @SerialName("images") val images: TmdbImagesConfigDto? = null,
)

@Serializable
data class TmdbImagesConfigDto(
    @SerialName("secure_base_url") val secureBaseUrl: String? = null,
)
