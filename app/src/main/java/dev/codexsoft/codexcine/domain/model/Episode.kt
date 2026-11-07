package dev.codexsoft.codexcine.domain.model

data class Episode(
    val id: Int,
    val episodeNumber: Int,
    val seasonNumber: Int,
    val name: String,
    val overview: String?,
    val stillUrl: String?,
    val airDate: String?,
    val runtimeMinutes: Int?,
)
