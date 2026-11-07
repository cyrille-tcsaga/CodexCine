package dev.codexsoft.codexcine.data.progress

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.IOException

private val Context.watchedEpisodesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "watched_episodes_prefs",
)

@Serializable
data class EpisodeNumber(val season: Int, val episode: Int) : Comparable<EpisodeNumber> {
    override fun compareTo(other: EpisodeNumber): Int =
        compareValuesBy(this, other, EpisodeNumber::season, EpisodeNumber::episode)
}

/** The episodes of one show the viewer has finished. */
@Serializable
data class WatchedShow(
    val showId: Int,
    val episodes: List<EpisodeNumber>,
    val updatedAtMs: Long,
) {
    /** The furthest episode watched, which is not always the last one watched. */
    val furthest: EpisodeNumber? get() = episodes.maxOrNull()
}

/** The pure rules behind the repository. */
object WatchedEpisodesRules {

    /** Enough for every show one follows; the least recently watched are dropped first. */
    const val MAX_SHOWS = 200

    fun markWatched(shows: List<WatchedShow>, showId: Int, episode: EpisodeNumber, nowMs: Long): List<WatchedShow> {
        val current = shows.firstOrNull { it.showId == showId }
        val episodes = (current?.episodes.orEmpty() + episode).distinct().sorted()
        val updated = WatchedShow(showId, episodes, nowMs)
        return (shows.filterNot { it.showId == showId } + updated)
            .sortedByDescending { it.updatedAtMs }
            .take(MAX_SHOWS)
    }
}

class WatchedEpisodesRepository(context: Context) {

    private val dataStore = context.applicationContext.watchedEpisodesDataStore
    private val showsKey = stringPreferencesKey("shows_json")
    private val json = Json { ignoreUnknownKeys = true }

    /** Most recently watched show first. */
    val shows: Flow<List<WatchedShow>> = dataStore.data
        .catch { error ->
            if (error is IOException) {
                Log.w(TAG, "Could not read watched episodes", error)
                emit(emptyPreferences())
            } else {
                throw error
            }
        }
        .map { prefs -> decode(prefs[showsKey]) }

    suspend fun currentShows(): List<WatchedShow> = shows.first()

    suspend fun markWatched(showId: Int, season: Int, episode: Int) {
        dataStore.edit { prefs ->
            val updated = WatchedEpisodesRules.markWatched(
                decode(prefs[showsKey]),
                showId,
                EpisodeNumber(season, episode),
                System.currentTimeMillis(),
            )
            prefs[showsKey] = json.encodeToString(updated)
        }
    }

    private fun decode(stored: String?): List<WatchedShow> {
        if (stored.isNullOrBlank()) return emptyList()
        return try {
            json.decodeFromString<List<WatchedShow>>(stored)
        } catch (e: SerializationException) {
            Log.w(TAG, "Ignoring malformed watched episodes", e)
            emptyList()
        }
    }

    private companion object {
        const val TAG = "WatchedEpisodes"
    }
}
