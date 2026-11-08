package dev.codexsoft.codexcine.ui.search

import android.app.SearchManager
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.BaseColumns
import android.util.Log
import dev.codexsoft.codexcine.CodexCineApp
import dev.codexsoft.codexcine.core.AppResult
import dev.codexsoft.codexcine.core.DetailsLink
import dev.codexsoft.codexcine.domain.model.MediaItem
import dev.codexsoft.codexcine.domain.model.SearchResults
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/** One title as the system search shows it. */
data class SearchSuggestion(
    val id: Int,
    val title: String,
    val subtitle: String?,
    val imageUrl: String?,
    val year: String?,
    val link: String,
)

/** Which results become suggestions. Pure, so that it can be tested. */
object SearchSuggestions {

    const val MAX = 10
    private const val MIN_QUERY_LENGTH = 2
    private const val MAX_QUERY_LENGTH = 100

    /** The query as typed or spoken, cleaned; null when too short to search. */
    fun cleanQuery(raw: String?): String? = raw.orEmpty()
        .filterNot(Char::isISOControl)
        .trim()
        .take(MAX_QUERY_LENGTH)
        .takeIf { it.length >= MIN_QUERY_LENGTH }

    /** Movies and shows only, alternating so both appear: a person has no page to play from. */
    fun from(results: SearchResults): List<SearchSuggestion> {
        val movies = results.movies.iterator()
        val shows = results.shows.iterator()
        val merged = buildList {
            while (movies.hasNext() || shows.hasNext()) {
                if (movies.hasNext()) add(movies.next())
                if (shows.hasNext()) add(shows.next())
            }
        }
        return merged.take(MAX).map { it.toSuggestion() }
    }

    private fun MediaItem.toSuggestion() = SearchSuggestion(
        id = id,
        title = title,
        subtitle = releaseYear,
        imageUrl = posterUrl,
        year = releaseYear,
        link = DetailsLink(mediaType, id, title).toUri(),
    )
}

/**
 * Answers the system search (the launcher's, or the Assistant's on Android TV) with matching titles. Only
 * the system can read it (GLOBAL_SEARCH, see the manifest). Each answer is a TMDB search, as in the app.
 */
class SearchSuggestionProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        val cursor = MatrixCursor(COLUMNS)
        val query = SearchSuggestions.cleanQuery(selectionArgs?.firstOrNull() ?: uri.lastPathSegment)
            ?: return cursor
        val app = context?.applicationContext as? CodexCineApp ?: return cursor
        // A binder thread, made to wait: the system search gives up on its own if this is slow.
        val result = runBlocking {
            withTimeoutOrNull(TIMEOUT_MS) { app.container.catalogRepository.search(query) }
        }
        if (result !is AppResult.Success) {
            Log.w(TAG, "No suggestions: ${(result as? AppResult.Error)?.kind ?: "timeout"}")
            return cursor
        }
        SearchSuggestions.from(result.data).forEachIndexed { index, item ->
            cursor.addRow(
                arrayOf<Any?>(
                    index,
                    item.title,
                    item.subtitle,
                    item.imageUrl,
                    item.year?.toIntOrNull(),
                    VIDEO_TYPE,
                    item.link,
                ),
            )
        }
        return cursor
    }

    override fun getType(uri: Uri): String = SearchManager.SUGGEST_MIME_TYPE

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    private companion object {
        const val TAG = "SearchSuggestions"
        const val TIMEOUT_MS = 4_000L
        const val VIDEO_TYPE = "video/*"

        val COLUMNS = arrayOf(
            BaseColumns._ID,
            SearchManager.SUGGEST_COLUMN_TEXT_1,
            SearchManager.SUGGEST_COLUMN_TEXT_2,
            SearchManager.SUGGEST_COLUMN_RESULT_CARD_IMAGE,
            SearchManager.SUGGEST_COLUMN_PRODUCTION_YEAR,
            SearchManager.SUGGEST_COLUMN_CONTENT_TYPE,
            SearchManager.SUGGEST_COLUMN_INTENT_DATA,
        )
    }
}
