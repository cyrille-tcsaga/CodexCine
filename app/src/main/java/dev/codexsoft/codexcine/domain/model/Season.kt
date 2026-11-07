package dev.codexsoft.codexcine.domain.model

data class Season(
    val id: Int,
    val seasonNumber: Int,
    val name: String?,
    val episodeCount: Int,
    val posterUrl: String?,
)
