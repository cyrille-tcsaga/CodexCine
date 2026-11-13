package dev.codexsoft.codexcine.ui.grid

import android.app.AlertDialog
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import dev.codexsoft.codexcine.R
import dev.codexsoft.codexcine.core.AppResult
import dev.codexsoft.codexcine.domain.model.CategoryType
import dev.codexsoft.codexcine.domain.model.DiscoverQuery
import dev.codexsoft.codexcine.domain.model.Genre
import dev.codexsoft.codexcine.domain.model.GridFilters
import dev.codexsoft.codexcine.domain.model.GridSort
import dev.codexsoft.codexcine.ui.common.appContainer
import kotlinx.coroutines.launch

/**
 * The "Filtres" menus of a "Voir tout" grid, shared by the Leanback and the Compose screens: a list of
 * what can be filtered, then a single-choice list for the one picked. Both use the app's dialog style.
 */
class GridFilterController(
    private val fragment: Fragment,
    private val category: CategoryType,
    private val filters: () -> GridFilters,
    private val apply: (GridFilters) -> Unit,
) {
    private var genres: List<Genre>? = null
    private var menu: AlertDialog? = null

    /** Whether TMDB can filter this category: trending mixes movies and shows, so it cannot. */
    val isAvailable: Boolean get() = DiscoverQuery.mediaTypeOf(category) != null

    fun dismiss() {
        menu?.dismiss()
        menu = null
    }

    fun showMenu() {
        val current = filters()
        val entries = buildList<Pair<String, () -> Unit>> {
            add(entry(R.string.filter_genre, genreName(current.genreId)) { pickGenre() })
            if (DiscoverQuery.allowsDecade(category)) {
                add(entry(R.string.filter_decade, current.decade?.let(::decadeLabel)) { pickDecade() })
            }
            add(entry(R.string.filter_rating, current.minRating?.let(::ratingLabel)) { pickRating() })
            add(entry(R.string.filter_sort, current.sort?.let(::sortLabel)) { pickSort() })
            if (current.isActive) add(fragment.getString(R.string.filter_reset) to { apply(GridFilters()) })
        }
        showList(fragment.getString(R.string.grid_filters), entries)
    }

    /** What is filtered, for the line next to the button: "Action · Années 1990 · ★ 7 et plus". */
    fun describe(filters: GridFilters): String = listOfNotNull(
        genreName(filters.genreId),
        filters.decade?.let(::decadeLabel),
        filters.minRating?.let(::ratingLabel),
        filters.sort?.let(::sortLabel),
    ).joinToString(fragment.getString(R.string.separator))

    private fun entry(labelRes: Int, value: String?, action: () -> Unit): Pair<String, () -> Unit> =
        fragment.getString(
            R.string.filter_entry,
            fragment.getString(labelRes),
            value ?: fragment.getString(R.string.filter_any),
        ) to action

    private fun pickGenre() {
        val known = genres
        if (known != null) return showGenres(known)
        val type = DiscoverQuery.mediaTypeOf(category) ?: return
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            when (val result = fragment.appContainer.catalogRepository.genres(type)) {
                is AppResult.Success -> {
                    genres = result.data
                    showGenres(result.data)
                }
                is AppResult.Error -> Toast.makeText(
                    fragment.requireContext(), R.string.grid_filters_genres_error, Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    private fun showGenres(all: List<Genre>) {
        // A genre category (Action, say) already has its genre.
        val choices = all.filterNot { it.id == category.genreId }
        val options = listOf<Genre?>(null) + choices
        val checked = filters().genreId?.let { id -> options.indexOfFirst { it?.id == id } }
        pick(R.string.filter_genre, options, { it?.name ?: fragment.getString(R.string.filter_any) }, checked) {
            apply(filters().copy(genreId = it?.id))
        }
    }

    private fun pickDecade() {
        val options = listOf<Int?>(null) + DiscoverQuery.DECADES
        pick(
            R.string.filter_decade, options,
            { it?.let(::decadeLabel) ?: fragment.getString(R.string.filter_any) },
            options.indexOf(filters().decade),
        ) { apply(filters().copy(decade = it)) }
    }

    private fun pickRating() {
        val options = listOf<Int?>(null) + DiscoverQuery.MIN_RATINGS
        pick(
            R.string.filter_rating, options,
            { it?.let(::ratingLabel) ?: fragment.getString(R.string.filter_any) },
            options.indexOf(filters().minRating),
        ) { apply(filters().copy(minRating = it)) }
    }

    private fun pickSort() {
        val options = listOf<GridSort?>(null) + GridSort.entries
        pick(
            R.string.filter_sort, options,
            { it?.let(::sortLabel) ?: fragment.getString(R.string.filter_sort_default) },
            options.indexOf(filters().sort),
        ) { apply(filters().copy(sort = it)) }
    }

    /** A single-choice list with the current value checked; choosing applies at once and closes it. */
    private fun <T> pick(titleRes: Int, options: List<T>, label: (T) -> String, checked: Int?, onPicked: (T) -> Unit) {
        if (fragment.view == null) return
        menu?.dismiss()
        menu = AlertDialog.Builder(fragment.requireContext(), R.style.CodexCine_Dialog)
            .setTitle(titleRes)
            .setSingleChoiceItems(options.map(label).toTypedArray(), checked?.coerceAtLeast(0) ?: 0) { dialog, which ->
                dialog.dismiss()
                onPicked(options[which])
            }
            .show()
    }

    private fun showList(title: String, entries: List<Pair<String, () -> Unit>>) {
        if (fragment.view == null) return
        menu?.dismiss()
        menu = AlertDialog.Builder(fragment.requireContext(), R.style.CodexCine_Dialog)
            .setTitle(title)
            .setItems(entries.map { it.first }.toTypedArray()) { _, which -> entries[which].second() }
            .show()
    }

    private fun genreName(id: Int?): String? = id?.let { genreId -> genres?.firstOrNull { it.id == genreId }?.name }

    private fun decadeLabel(decade: Int) = fragment.getString(R.string.filter_decade_value, decade)

    private fun ratingLabel(rating: Int) = fragment.getString(R.string.filter_rating_value, rating)

    private fun sortLabel(sort: GridSort) = fragment.getString(
        when (sort) {
            GridSort.POPULARITY -> R.string.filter_sort_popularity
            GridSort.RATING -> R.string.filter_sort_rating
            GridSort.NEWEST -> R.string.filter_sort_newest
        },
    )
}
