package dev.codexsoft.codexcine.domain.model

import java.time.LocalDate

/** What the viewer narrowed a "Voir tout" grid to. All null: the category as it comes. */
data class GridFilters(
    val genreId: Int? = null,
    /** First year of the decade, such as 1990. */
    val decade: Int? = null,
    /** Lowest TMDB rating, out of 10. */
    val minRating: Int? = null,
    val sort: GridSort? = null,
) {
    val isActive: Boolean get() = genreId != null || decade != null || minRating != null || sort != null
    val activeCount: Int get() = listOfNotNull(genreId, decade, minRating, sort).size
}

enum class GridSort { POPULARITY, RATING, NEWEST }

data class Genre(val id: Int, val name: String)

/**
 * Turns a home category and filters into TMDB `discover` parameters. Pure, so that it can be tested.
 *
 * Each category keeps its meaning (popular, best rated, in cinemas…) and the filters narrow it down.
 */
object DiscoverQuery {

    val DECADES = listOf(2020, 2010, 2000, 1990, 1980, 1970)
    val MIN_RATINGS = listOf(8, 7, 6)

    /** Enough votes for a rating to mean something. */
    private const val RATED_MIN_VOTES = 300
    private const val FILTERED_MIN_VOTES = 50
    private const val LATEST_MIN_VOTES = 10
    private const val NOW_PLAYING_DAYS = 45L
    private const val ON_THE_AIR_DAYS = 7L

    /** Movies or shows; null for a category that mixes both (trending) or is not a TMDB list. */
    fun mediaTypeOf(category: CategoryType): MediaType? = when (category) {
        CategoryType.POPULAR_MOVIES, CategoryType.NOW_PLAYING_MOVIES, CategoryType.TOP_RATED_MOVIES,
        CategoryType.UPCOMING_MOVIES, CategoryType.LATEST_MOVIES, CategoryType.GENRE_ACTION,
        CategoryType.GENRE_ANIMATION, CategoryType.GENRE_COMEDY -> MediaType.MOVIE
        CategoryType.POPULAR_TV, CategoryType.TOP_RATED_TV, CategoryType.ON_THE_AIR_TV,
        CategoryType.LATEST_TV -> MediaType.TV
        CategoryType.TRENDING, CategoryType.CONTINUE_WATCHING, CategoryType.NEW_EPISODES,
        CategoryType.MY_LIST, CategoryType.ABOUT -> null
    }

    /** Categories that are a window in time have no room for a decade. */
    fun allowsDecade(category: CategoryType): Boolean = category !in setOf(
        CategoryType.NOW_PLAYING_MOVIES,
        CategoryType.UPCOMING_MOVIES,
        CategoryType.ON_THE_AIR_TV,
    )

    fun build(category: CategoryType, filters: GridFilters, today: LocalDate): Map<String, String> {
        val type = requireNotNull(mediaTypeOf(category)) { "$category cannot be filtered" }
        val isMovie = type == MediaType.MOVIE
        val dateKey = if (isMovie) "primary_release_date" else "first_air_date"
        val params = linkedMapOf("sort_by" to "popularity.desc")
        var minVotes = 0

        // The category's own meaning.
        when (category) {
            CategoryType.TOP_RATED_MOVIES, CategoryType.TOP_RATED_TV -> {
                params["sort_by"] = "vote_average.desc"
                minVotes = RATED_MIN_VOTES
            }
            CategoryType.NOW_PLAYING_MOVIES -> {
                params["$dateKey.gte"] = today.minusDays(NOW_PLAYING_DAYS).toString()
                params["$dateKey.lte"] = today.toString()
            }
            CategoryType.UPCOMING_MOVIES -> params["$dateKey.gte"] = today.plusDays(1).toString()
            CategoryType.ON_THE_AIR_TV -> {
                params["air_date.gte"] = today.toString()
                params["air_date.lte"] = today.plusDays(ON_THE_AIR_DAYS).toString()
            }
            CategoryType.LATEST_MOVIES, CategoryType.LATEST_TV -> {
                params["sort_by"] = "$dateKey.desc"
                params["$dateKey.lte"] = today.toString()
                minVotes = LATEST_MIN_VOTES
            }
            else -> Unit
        }
        val genres = listOfNotNull(category.genreId, filters.genreId).distinct()
        if (genres.isNotEmpty()) params["with_genres"] = genres.joinToString(",")

        // The viewer's filters.
        filters.decade?.takeIf { allowsDecade(category) }?.let { decade ->
            params["$dateKey.gte"] = "$decade-01-01"
            params["$dateKey.lte"] = minOf(LocalDate.of(decade + 9, 12, 31), today).toString()
        }
        filters.minRating?.let {
            params["vote_average.gte"] = it.toString()
            minVotes = maxOf(minVotes, FILTERED_MIN_VOTES)
        }
        when (filters.sort) {
            GridSort.POPULARITY -> params["sort_by"] = "popularity.desc"
            GridSort.RATING -> {
                params["sort_by"] = "vote_average.desc"
                minVotes = maxOf(minVotes, RATED_MIN_VOTES)
            }
            GridSort.NEWEST -> {
                params["sort_by"] = "$dateKey.desc"
                // Newest first must not open on announced titles.
                if (category != CategoryType.UPCOMING_MOVIES && "$dateKey.lte" !in params) {
                    params["$dateKey.lte"] = today.toString()
                }
            }
            null -> Unit
        }
        if (minVotes > 0) params["vote_count.gte"] = minVotes.toString()
        return params
    }
}
