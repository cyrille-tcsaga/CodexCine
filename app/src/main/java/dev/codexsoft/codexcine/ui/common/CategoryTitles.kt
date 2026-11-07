package dev.codexsoft.codexcine.ui.common

import androidx.annotation.StringRes
import dev.codexsoft.codexcine.R
import dev.codexsoft.codexcine.domain.model.CategoryType

@get:StringRes
val CategoryType.titleRes: Int
    get() = when (this) {
        CategoryType.MY_LIST -> R.string.row_my_list
        CategoryType.TRENDING -> R.string.row_trending
        CategoryType.POPULAR_MOVIES -> R.string.row_popular_movies
        CategoryType.NOW_PLAYING_MOVIES -> R.string.row_now_playing_movies
        CategoryType.TOP_RATED_MOVIES -> R.string.row_top_rated_movies
        CategoryType.UPCOMING_MOVIES -> R.string.row_upcoming_movies
        CategoryType.POPULAR_TV -> R.string.row_popular_tv
        CategoryType.TOP_RATED_TV -> R.string.row_top_rated_tv
        CategoryType.ON_THE_AIR_TV -> R.string.row_on_the_air_tv
        CategoryType.GENRE_ACTION -> R.string.row_genre_action
        CategoryType.GENRE_ANIMATION -> R.string.row_genre_animation
        CategoryType.GENRE_COMEDY -> R.string.row_genre_comedy
        CategoryType.ABOUT -> R.string.row_about
    }
