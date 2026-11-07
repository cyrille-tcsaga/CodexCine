package dev.codexsoft.codexcine.data.mapper

import dev.codexsoft.codexcine.core.ImageUrls
import dev.codexsoft.codexcine.data.remote.dto.TmdbCastDto
import dev.codexsoft.codexcine.data.remote.dto.TmdbContentRatingsDto
import dev.codexsoft.codexcine.data.remote.dto.TmdbEpisodeDto
import dev.codexsoft.codexcine.data.remote.dto.TmdbMovieDetailsDto
import dev.codexsoft.codexcine.data.remote.dto.TmdbMovieDto
import dev.codexsoft.codexcine.data.remote.dto.TmdbMultiSearchItemDto
import dev.codexsoft.codexcine.data.remote.dto.TmdbPersonDetailsDto
import dev.codexsoft.codexcine.data.remote.dto.TmdbReleaseDatesDto
import dev.codexsoft.codexcine.data.remote.dto.TmdbSeasonDto
import dev.codexsoft.codexcine.data.remote.dto.TmdbTvDetailsDto
import dev.codexsoft.codexcine.data.remote.dto.TmdbTvDto
import dev.codexsoft.codexcine.data.remote.dto.TmdbVideoDto
import dev.codexsoft.codexcine.data.remote.dto.TmdbVideosDto
import dev.codexsoft.codexcine.domain.model.CastMember
import dev.codexsoft.codexcine.domain.model.Episode
import dev.codexsoft.codexcine.domain.model.MediaDetails
import dev.codexsoft.codexcine.domain.model.MediaItem
import dev.codexsoft.codexcine.domain.model.MediaType
import dev.codexsoft.codexcine.domain.model.PersonDetails
import dev.codexsoft.codexcine.domain.model.SearchResults
import dev.codexsoft.codexcine.domain.model.Season
import dev.codexsoft.codexcine.domain.model.Video

private const val MAX_CAST = 15

fun TmdbMovieDto.toMediaItem(): MediaItem = MediaItem(
    id = id,
    title = title.orEmpty(),
    posterUrl = ImageUrls.poster(posterPath),
    backdropUrl = ImageUrls.background(backdropPath),
    releaseYear = yearOf(releaseDate),
    voteAverage = voteAverage ?: 0.0,
    mediaType = MediaType.MOVIE,
    overview = overview?.takeIf { it.isNotBlank() },
)

fun TmdbTvDto.toMediaItem(): MediaItem = MediaItem(
    id = id,
    title = name.orEmpty(),
    posterUrl = ImageUrls.poster(posterPath),
    backdropUrl = ImageUrls.background(backdropPath),
    releaseYear = yearOf(firstAirDate),
    voteAverage = voteAverage ?: 0.0,
    mediaType = MediaType.TV,
    overview = overview?.takeIf { it.isNotBlank() },
)

/** Returns null for a `media_type` other than movie, tv or person. */
fun TmdbMultiSearchItemDto.toMediaItem(): MediaItem? = when (MediaType.fromValue(mediaType)) {
    MediaType.MOVIE -> MediaItem(
        id = id,
        title = title.orEmpty(),
        posterUrl = ImageUrls.poster(posterPath),
        backdropUrl = ImageUrls.background(backdropPath),
        releaseYear = yearOf(releaseDate),
        voteAverage = voteAverage ?: 0.0,
        mediaType = MediaType.MOVIE,
        overview = overview?.takeIf { it.isNotBlank() },
    )
    MediaType.TV -> MediaItem(
        id = id,
        title = name.orEmpty(),
        posterUrl = ImageUrls.poster(posterPath),
        backdropUrl = ImageUrls.background(backdropPath),
        releaseYear = yearOf(firstAirDate),
        voteAverage = voteAverage ?: 0.0,
        mediaType = MediaType.TV,
        overview = overview?.takeIf { it.isNotBlank() },
    )
    MediaType.PERSON -> MediaItem(
        id = id,
        title = name.orEmpty(),
        posterUrl = ImageUrls.profile(profilePath),
        backdropUrl = null,
        releaseYear = null,
        voteAverage = 0.0,
        mediaType = MediaType.PERSON,
    )
    null -> null
}

/** Trending can contain people, but home rows only offer movies and shows. */
fun List<TmdbMultiSearchItemDto>.toTrendingItems(): List<MediaItem> =
    mapNotNull { it.toMediaItem() }.filter { it.mediaType != MediaType.PERSON }

fun List<TmdbMultiSearchItemDto>.toSearchResults(): SearchResults {
    val items = mapNotNull { it.toMediaItem() }
    return SearchResults(
        movies = items.filter { it.mediaType == MediaType.MOVIE },
        shows = items.filter { it.mediaType == MediaType.TV },
        people = items.filter { it.mediaType == MediaType.PERSON },
    )
}

fun TmdbMovieDetailsDto.toMediaDetails(): MediaDetails {
    val recommended = recommendations?.results.orEmpty().ifEmpty { similar?.results.orEmpty() }
    return MediaDetails(
        id = id,
        title = title.orEmpty(),
        overview = overview?.takeIf { it.isNotBlank() },
        posterUrl = ImageUrls.poster(posterPath),
        backdropUrl = ImageUrls.background(backdropPath),
        releaseYear = yearOf(releaseDate),
        runtimeMinutes = runtime?.takeIf { it > 0 },
        seasonCount = null,
        certification = frenchMovieCertification(releaseDates),
        genres = genres.orEmpty().mapNotNull { it.name },
        voteAverage = voteAverage ?: 0.0,
        voteCount = voteCount ?: 0,
        mediaType = MediaType.MOVIE,
        trailerKey = selectTrailerKey(videos.toVideos()),
        cast = credits?.cast.orEmpty().take(MAX_CAST).map { it.toCastMember() },
        seasons = emptyList(),
        recommendations = recommended.map { it.toMediaItem() },
    )
}

fun TmdbTvDetailsDto.toMediaDetails(): MediaDetails {
    val recommended = recommendations?.results.orEmpty().ifEmpty { similar?.results.orEmpty() }
    return MediaDetails(
        id = id,
        title = name.orEmpty(),
        overview = overview?.takeIf { it.isNotBlank() },
        posterUrl = ImageUrls.poster(posterPath),
        backdropUrl = ImageUrls.background(backdropPath),
        releaseYear = yearOf(firstAirDate),
        runtimeMinutes = null,
        seasonCount = numberOfSeasons,
        certification = frenchTvCertification(contentRatings),
        genres = genres.orEmpty().mapNotNull { it.name },
        voteAverage = voteAverage ?: 0.0,
        voteCount = voteCount ?: 0,
        mediaType = MediaType.TV,
        trailerKey = selectTrailerKey(videos.toVideos()),
        cast = credits?.cast.orEmpty().take(MAX_CAST).map { it.toCastMember() },
        seasons = seasons.orEmpty().toVisibleSeasons(),
        recommendations = recommended.map { it.toMediaItem() },
    )
}

/** Season 0 ("Specials") is hidden, unless it is the only season the show has. */
fun List<TmdbSeasonDto>.toVisibleSeasons(): List<Season> {
    val seasons = mapNotNull { it.toSeason() }
    return seasons.filter { it.seasonNumber > 0 }.ifEmpty { seasons }
}

private fun TmdbSeasonDto.toSeason(): Season? {
    val number = seasonNumber ?: return null
    return Season(
        id = id,
        seasonNumber = number,
        name = name?.takeIf { it.isNotBlank() },
        episodeCount = episodeCount ?: 0,
        posterUrl = ImageUrls.poster(posterPath),
    )
}

fun TmdbCastDto.toCastMember(): CastMember = CastMember(
    id = id,
    name = name.orEmpty(),
    character = character?.takeIf { it.isNotBlank() },
    profileUrl = ImageUrls.profile(profilePath),
)

fun TmdbEpisodeDto.toEpisode(fallbackSeasonNumber: Int): Episode = Episode(
    id = id,
    episodeNumber = episodeNumber ?: 0,
    seasonNumber = seasonNumber ?: fallbackSeasonNumber,
    name = name.orEmpty(),
    overview = overview?.takeIf { it.isNotBlank() },
    stillUrl = ImageUrls.backdrop(stillPath),
    airDate = airDate?.takeIf { it.isNotBlank() },
    runtimeMinutes = runtime?.takeIf { it > 0 },
)

/** Cast credits only, movies and shows, one entry per title, most popular first. */
fun TmdbPersonDetailsDto.toPersonDetails(): PersonDetails = PersonDetails(
    id = id,
    name = name.orEmpty(),
    filmography = combinedCredits?.cast.orEmpty()
        .sortedByDescending { it.popularity ?: 0.0 }
        .mapNotNull { it.toCreditItem() }
        .distinctBy { it.mediaType to it.id },
)

private fun TmdbCastDto.toCreditItem(): MediaItem? {
    val type = MediaType.fromValue(mediaType)
    if (type != MediaType.MOVIE && type != MediaType.TV) return null
    return MediaItem(
        id = id,
        title = title ?: name.orEmpty(),
        posterUrl = ImageUrls.poster(posterPath),
        backdropUrl = ImageUrls.background(backdropPath),
        releaseYear = yearOf(releaseDate ?: firstAirDate),
        voteAverage = voteAverage ?: 0.0,
        mediaType = type,
    )
}

private fun TmdbVideosDto?.toVideos(): List<Video> =
    this?.results.orEmpty().mapNotNull { it.toVideo() }

private fun TmdbVideoDto.toVideo(): Video? {
    val videoKey = key?.takeIf { it.isNotBlank() } ?: return null
    return Video(key = videoKey, site = site, type = type, language = iso6391)
}

/** Only YouTube trailers qualify: French first, then English, then any language. */
fun selectTrailerKey(videos: List<Video>): String? {
    val trailers = videos.filter {
        it.site.equals("YouTube", ignoreCase = true) && it.type.equals("Trailer", ignoreCase = true)
    }
    val chosen = trailers.firstOrNull { it.language.equals("fr", ignoreCase = true) }
        ?: trailers.firstOrNull { it.language.equals("en", ignoreCase = true) }
        ?: trailers.firstOrNull()
    return chosen?.key
}

fun frenchMovieCertification(releaseDates: TmdbReleaseDatesDto?): String? =
    releaseDates?.results.orEmpty()
        .firstOrNull { it.iso31661.equals("FR", ignoreCase = true) }
        ?.releaseDates.orEmpty()
        .firstNotNullOfOrNull { release -> release.certification?.asFrenchCertification() }

fun frenchTvCertification(contentRatings: TmdbContentRatingsDto?): String? =
    contentRatings?.results.orEmpty()
        .firstOrNull { it.iso31661.equals("FR", ignoreCase = true) }
        ?.rating?.asFrenchCertification()

/** "NR" (not rated) appears in TMDB's French data but means that there is no certification. */
private fun String.asFrenchCertification(): String? =
    takeIf { it.isNotBlank() && !it.equals("NR", ignoreCase = true) }

/** TMDB sends an empty string, not null, for an unknown date. */
private fun yearOf(date: String?): String? = date?.take(4)?.takeIf { it.length == 4 }
