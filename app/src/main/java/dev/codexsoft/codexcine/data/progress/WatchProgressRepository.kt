package dev.codexsoft.codexcine.data.progress

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.codexsoft.codexcine.domain.model.MediaType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.IOException

private val Context.watchProgressDataStore: DataStore<Preferences> by preferencesDataStore(name = "watch_progress_prefs")

@Serializable
data class WatchProgress(
    val mediaType: String,
    val tmdbId: Int,
    val season: Int = 0,
    val episode: Int = 0,
    val positionSeconds: Double,
    val durationSeconds: Double,
    val updatedAtMs: Long,
    val title: String,
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
) {
    val key: String get() = WatchProgressRules.key(mediaType, tmdbId, season, episode)
    val type: MediaType? get() = MediaType.fromValue(mediaType)
    val fraction: Float
        get() = if (durationSeconds > 0) (positionSeconds / durationSeconds).toFloat().coerceIn(0f, 1f) else 0f
}

/** The pure rules behind the repository. */
object WatchProgressRules {

    const val MAX_ENTRIES = 30
    private const val FINISHED_FRACTION = 0.93

    /** Season and episode are 0 for movies. */
    fun key(mediaType: String, tmdbId: Int, season: Int, episode: Int) = "$mediaType:$tmdbId:$season:$episode"

    fun isFinished(positionSeconds: Double, durationSeconds: Double, isEnded: Boolean = false): Boolean =
        isEnded || (durationSeconds > 0 && positionSeconds >= FINISHED_FRACTION * durationSeconds)

    /** Inserts or replaces [entry], or removes it when finished. Newest first, oldest dropped. */
    fun merge(entries: List<WatchProgress>, entry: WatchProgress, isEnded: Boolean = false): List<WatchProgress> {
        val others = entries.filterNot { it.key == entry.key }
        val updated = if (isFinished(entry.positionSeconds, entry.durationSeconds, isEnded)) {
            others
        } else {
            others + entry
        }
        return updated.sortedByDescending { it.updatedAtMs }.take(MAX_ENTRIES)
    }
}

/** The storage the player depends on, so that it can be tested without DataStore. */
interface WatchProgressStore {
    suspend fun get(key: String): WatchProgress?
    suspend fun save(entry: WatchProgress, isEnded: Boolean = false)
    suspend fun remove(key: String)
}

class WatchProgressRepository(context: Context) : WatchProgressStore {

    private val dataStore = context.applicationContext.watchProgressDataStore
    private val entriesKey = stringPreferencesKey("entries_json")
    private val json = Json { ignoreUnknownKeys = true }

    /** Most recently watched first. */
    val items: Flow<List<WatchProgress>> = dataStore.data
        .catch { error ->
            if (error is IOException) {
                Log.w(TAG, "Could not read watch progress", error)
                emit(emptyPreferences())
            } else {
                throw error
            }
        }
        .map { prefs -> decode(prefs[entriesKey]).sortedByDescending { it.updatedAtMs } }

    suspend fun currentItems(): List<WatchProgress> = items.first()

    override suspend fun get(key: String): WatchProgress? = currentItems().firstOrNull { it.key == key }

    /** The episode of [tmdbId] watched most recently, if any. */
    suspend fun latestEpisode(tmdbId: Int): WatchProgress? =
        currentItems().firstOrNull { it.tmdbId == tmdbId && it.type == MediaType.TV }

    override suspend fun save(entry: WatchProgress, isEnded: Boolean) {
        dataStore.edit { prefs ->
            val merged = WatchProgressRules.merge(decode(prefs[entriesKey]), entry, isEnded)
            prefs[entriesKey] = json.encodeToString(merged)
        }
    }

    override suspend fun remove(key: String) {
        dataStore.edit { prefs ->
            prefs[entriesKey] = json.encodeToString(decode(prefs[entriesKey]).filterNot { it.key == key })
        }
    }

    private fun decode(stored: String?): List<WatchProgress> {
        if (stored.isNullOrBlank()) return emptyList()
        return try {
            json.decodeFromString<List<WatchProgress>>(stored)
        } catch (e: SerializationException) {
            Log.w(TAG, "Ignoring malformed watch progress", e)
            emptyList()
        }
    }

    private companion object {
        const val TAG = "WatchProgress"
    }
}
