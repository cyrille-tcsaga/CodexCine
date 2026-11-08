package dev.codexsoft.codexcine.domain.model

data class MediaDetails(
    val id: Int,
    val title: String,
    val overview: String?,
    val posterUrl: String?,
    val backdropUrl: String?,
    val releaseYear: String?,
    val runtimeMinutes: Int?,
    val seasonCount: Int?,
    val certification: String?,
    val genres: List<String>,
    val voteAverage: Double,
    val voteCount: Int,
    val mediaType: MediaType,
    val trailerKey: String?,
    val cast: List<CastMember>,
    val seasons: List<Season>,
    val recommendations: List<MediaItem>,
    /** The saga a film belongs to, as a TMDB collection id. Always null for a show. */
    val collectionId: Int? = null,
)
