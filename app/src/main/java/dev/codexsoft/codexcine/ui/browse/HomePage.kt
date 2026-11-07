package dev.codexsoft.codexcine.ui.browse

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.leanback.widget.HeaderItem
import dev.codexsoft.codexcine.R
import dev.codexsoft.codexcine.domain.model.CategoryType

/** The sidebar's pages. Each shows its rows, in the order listed here. */
enum class HomePage(
    @param:StringRes val titleRes: Int,
    @param:DrawableRes val iconRes: Int,
    val categories: List<CategoryType>,
    val hasBillboard: Boolean = false,
    @param:StringRes val emptyMessageRes: Int? = null,
) {
    HOME(
        R.string.page_home,
        R.drawable.ic_nav_home,
        listOf(
            CategoryType.CONTINUE_WATCHING,
            CategoryType.NEW_EPISODES,
            CategoryType.TRENDING,
            CategoryType.MY_LIST,
            CategoryType.POPULAR_MOVIES,
            CategoryType.POPULAR_TV,
            CategoryType.NOW_PLAYING_MOVIES,
            CategoryType.ABOUT,
        ),
        hasBillboard = true,
    ),
    MOVIES(
        R.string.page_movies,
        R.drawable.ic_nav_movies,
        listOf(
            CategoryType.POPULAR_MOVIES,
            CategoryType.LATEST_MOVIES,
            CategoryType.NOW_PLAYING_MOVIES,
            CategoryType.TOP_RATED_MOVIES,
            CategoryType.UPCOMING_MOVIES,
            CategoryType.GENRE_ACTION,
            CategoryType.GENRE_ANIMATION,
            CategoryType.GENRE_COMEDY,
        ),
    ),
    SHOWS(
        R.string.page_shows,
        R.drawable.ic_nav_shows,
        listOf(
            CategoryType.POPULAR_TV,
            CategoryType.LATEST_TV,
            CategoryType.ON_THE_AIR_TV,
            CategoryType.TOP_RATED_TV,
        ),
    ),
    MY_LIST(
        R.string.row_my_list,
        R.drawable.ic_nav_my_list,
        listOf(CategoryType.CONTINUE_WATCHING, CategoryType.NEW_EPISODES, CategoryType.MY_LIST),
        emptyMessageRes = R.string.my_list_empty,
    ),
}

/** A sidebar entry: Leanback's header, plus the page it opens. */
class SidebarItem(val page: HomePage, name: String) : HeaderItem(page.ordinal.toLong(), name)
