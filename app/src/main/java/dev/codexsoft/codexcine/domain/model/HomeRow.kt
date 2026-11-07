package dev.codexsoft.codexcine.domain.model

import dev.codexsoft.codexcine.data.progress.WatchProgress

/** Home screen categories. Declaration order is the display order. */
enum class CategoryType(val genreId: Int? = null) {
    CONTINUE_WATCHING,
    MY_LIST,
    TRENDING,
    POPULAR_MOVIES,
    NOW_PLAYING_MOVIES,
    TOP_RATED_MOVIES,
    UPCOMING_MOVIES,
    POPULAR_TV,
    TOP_RATED_TV,
    ON_THE_AIR_TV,
    GENRE_ACTION(genreId = 28),
    GENRE_ANIMATION(genreId = 16),
    GENRE_COMEDY(genreId = 35),
    ABOUT;

    /** True for the categories that are loaded from TMDB and can be paged. */
    val isRemote: Boolean get() = this != CONTINUE_WATCHING && this != MY_LIST && this != ABOUT
}

data class HomeRow(
    val category: CategoryType,
    val items: List<MediaItem>,
    val continueWatching: List<WatchProgress> = emptyList(),
)

data class MediaPage(
    val items: List<MediaItem>,
    val page: Int,
    val totalPages: Int,
)
