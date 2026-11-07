package dev.codexsoft.codexcine.data.repository

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.codexsoft.codexcine.domain.model.MediaItem
import dev.codexsoft.codexcine.domain.model.MediaType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.IOException

private val Context.myListDataStore: DataStore<Preferences> by preferencesDataStore(name = "my_list_prefs")

@Serializable
private data class MyListEntry(
    @SerialName("id") val id: Int,
    @SerialName("title") val title: String,
    @SerialName("poster_url") val posterUrl: String? = null,
    @SerialName("backdrop_url") val backdropUrl: String? = null,
    @SerialName("release_year") val releaseYear: String? = null,
    @SerialName("vote_average") val voteAverage: Double = 0.0,
    @SerialName("media_type") val mediaType: String,
)

class MyListRepository(context: Context) {

    private val dataStore = context.applicationContext.myListDataStore
    private val entriesKey = stringPreferencesKey("my_list_entries_json")
    private val json = Json { ignoreUnknownKeys = true }

    val items: Flow<List<MediaItem>> = dataStore.data
        .catch { error ->
            if (error is IOException) {
                Log.w(TAG, "Could not read my list", error)
                emit(emptyPreferences())
            } else {
                throw error
            }
        }
        .map { prefs -> decode(prefs[entriesKey]).mapNotNull { it.toMediaItem() } }

    suspend fun currentItems(): List<MediaItem> = items.first()

    fun contains(mediaId: Int, mediaType: MediaType): Flow<Boolean> =
        items.map { list -> list.any { it.id == mediaId && it.mediaType == mediaType } }
            .distinctUntilChanged()

    /** Adds the item at the top of the list, or removes it when it is already there. */
    suspend fun toggle(item: MediaItem) {
        dataStore.edit { prefs ->
            val entries = decode(prefs[entriesKey])
            val isSameItem = { entry: MyListEntry ->
                entry.id == item.id && entry.mediaType == item.mediaType.value
            }
            val updated = if (entries.any(isSameItem)) {
                entries.filterNot(isSameItem)
            } else {
                listOf(item.toEntry()) + entries
            }
            prefs[entriesKey] = json.encodeToString(updated)
        }
    }

    private fun decode(stored: String?): List<MyListEntry> {
        if (stored.isNullOrBlank()) return emptyList()
        return try {
            json.decodeFromString<List<MyListEntry>>(stored)
        } catch (e: SerializationException) {
            Log.w(TAG, "Ignoring a malformed my list", e)
            emptyList()
        }
    }

    private fun MyListEntry.toMediaItem(): MediaItem? {
        val type = MediaType.fromValue(mediaType) ?: return null
        return MediaItem(
            id = id,
            title = title,
            posterUrl = posterUrl,
            backdropUrl = backdropUrl,
            releaseYear = releaseYear,
            voteAverage = voteAverage,
            mediaType = type,
        )
    }

    private fun MediaItem.toEntry() = MyListEntry(
        id = id,
        title = title,
        posterUrl = posterUrl,
        backdropUrl = backdropUrl,
        releaseYear = releaseYear,
        voteAverage = voteAverage,
        mediaType = mediaType.value,
    )

    private companion object {
        const val TAG = "MyListRepository"
    }
}
