package dev.codexsoft.codexcine.domain.model

enum class MediaType(val value: String) {
    MOVIE("movie"),
    TV("tv"),
    PERSON("person");

    companion object {
        fun fromValue(value: String?): MediaType? = entries.firstOrNull { it.value == value }
    }
}
