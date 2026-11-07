package dev.codexsoft.codexcine.core

/**
 * Builds TMDB image URLs. `original` is deliberately not offered: the target box only has 2 GB of RAM.
 */
object ImageUrls {
    const val DEFAULT_BASE_URL = "https://image.tmdb.org/t/p/"

    const val SIZE_POSTER = "w342"
    const val SIZE_BACKDROP = "w780"
    const val SIZE_BACKGROUND = "w1280"
    const val SIZE_PROFILE = "w185"

    @Volatile
    private var baseUrl: String = DEFAULT_BASE_URL

    /** Applies `images.secure_base_url` from `GET configuration`. A blank value keeps the current base. */
    fun setBaseUrl(url: String?) {
        if (!url.isNullOrBlank()) {
            baseUrl = if (url.endsWith("/")) url else "$url/"
        }
    }

    fun poster(path: String?): String? = build(SIZE_POSTER, path)
    fun backdrop(path: String?): String? = build(SIZE_BACKDROP, path)
    fun background(path: String?): String? = build(SIZE_BACKGROUND, path)
    fun profile(path: String?): String? = build(SIZE_PROFILE, path)

    private fun build(size: String, path: String?): String? {
        if (path.isNullOrBlank()) return null
        return baseUrl + size + (if (path.startsWith("/")) path else "/$path")
    }
}
