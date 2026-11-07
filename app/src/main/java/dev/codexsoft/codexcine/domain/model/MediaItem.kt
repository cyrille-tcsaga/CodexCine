package dev.codexsoft.codexcine.domain.model

data class MediaItem(
    val id: Int,
    val title: String,
    val posterUrl: String?,
    val backdropUrl: String?,
    val releaseYear: String?,
    val voteAverage: Double,
    val mediaType: MediaType,
)
