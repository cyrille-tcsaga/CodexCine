package dev.codexsoft.codexcine.data.watchnext

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.media.tv.TvContract
import android.media.tv.TvContract.PreviewPrograms
import android.media.tv.TvContract.WatchNextPrograms
import android.util.Log
import androidx.core.content.edit
import dev.codexsoft.codexcine.data.progress.WatchProgress
import dev.codexsoft.codexcine.domain.model.MediaType
import dev.codexsoft.codexcine.domain.model.NewEpisode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Keeps the system home screen's "Play next" row in line with the app: titles in progress, and new
 * episodes of shows being watched. Each entry is found again through its key (the provider's internal id).
 *
 * The TV provider only exists on TV devices: elsewhere this does nothing. A failing provider never
 * reaches the viewer, it is only logged.
 */
class WatchNextPublisher(context: Context, private val scope: CoroutineScope) {

    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private val isSupported = appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)

    fun publishContinue(progress: List<WatchProgress>) {
        if (!isSupported) return
        scope.launch(Dispatchers.IO) {
            mutex.withLock {
                sync(WatchNextEntry.Kind.CONTINUE, WatchNextRules.continueEntries(progress, dismissedAt()))
            }
        }
    }

    fun publishNew(newEpisodes: List<NewEpisode>) {
        if (!isSupported) return
        scope.launch(Dispatchers.IO) {
            mutex.withLock {
                val entries = WatchNextRules.newEntries(newEpisodes, dismissedAt().keys, System.currentTimeMillis())
                sync(WatchNextEntry.Kind.NEW, entries)
            }
        }
    }

    /** The viewer removed a card from the home screen: it must not come back on the next sync. */
    fun onRemovedByViewer(programId: Long) {
        val key = prefs.getString(idKey(programId), null) ?: return
        prefs.edit {
            putLong(DISMISSED + key, System.currentTimeMillis())
            remove(idKey(programId))
        }
    }

    private fun dismissedAt(): Map<String, Long> = prefs.all
        .filterKeys { it.startsWith(DISMISSED) }
        .mapNotNull { (key, value) -> (value as? Long)?.let { key.removePrefix(DISMISSED) to it } }
        .toMap()

    private fun sync(kind: WatchNextEntry.Kind, wanted: List<WatchNextEntry>) {
        try {
            val published = publishedPrograms(kind)
            val wantedByKey = wanted.associateBy { it.key }
            published.forEach { (key, row) ->
                when {
                    !row.isBrowsable -> {
                        // Hidden by the viewer, as the broadcast may have been missed.
                        prefs.edit { putLong(DISMISSED + key, System.currentTimeMillis()) }
                        delete(row.id)
                    }
                    key !in wantedByKey -> delete(row.id)
                }
            }
            wanted.forEach { entry ->
                val row = published[entry.key]
                if (row != null && row.isBrowsable) {
                    resolver.update(TvContract.buildWatchNextProgramUri(row.id), entry.toValues(), null, null)
                } else if (row == null) {
                    resolver.insert(WatchNextPrograms.CONTENT_URI, entry.toValues())
                        ?.let(ContentUris::parseId)
                        ?.let { id -> prefs.edit { putString(idKey(id), entry.key) } }
                }
            }
        } catch (e: RuntimeException) {
            // SecurityException, IllegalArgumentException… from a provider that differs on this device.
            Log.w(TAG, "Could not update the Play next row", e)
        }
    }

    private data class ProgramRow(val id: Long, val isBrowsable: Boolean)

    /** Our programs of [kind], by key. The provider only ever returns this app's own rows. */
    private fun publishedPrograms(kind: WatchNextEntry.Kind): Map<String, ProgramRow> {
        val projection = arrayOf(
            WatchNextPrograms._ID,
            WatchNextPrograms.COLUMN_INTERNAL_PROVIDER_ID,
            WatchNextPrograms.COLUMN_BROWSABLE,
        )
        val rows = HashMap<String, ProgramRow>()
        resolver.query(WatchNextPrograms.CONTENT_URI, projection, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val key = cursor.getString(1) ?: continue
                if (!key.startsWith(kind.prefix)) continue
                rows[key] = ProgramRow(id = cursor.getLong(0), isBrowsable = cursor.getInt(2) != 0)
            }
        }
        return rows
    }

    private fun delete(programId: Long) {
        resolver.delete(TvContract.buildWatchNextProgramUri(programId), null, null)
        prefs.edit { remove(idKey(programId)) }
    }

    private fun WatchNextEntry.toValues() = ContentValues().apply {
        put(WatchNextPrograms.COLUMN_INTERNAL_PROVIDER_ID, key)
        put(
            WatchNextPrograms.COLUMN_WATCH_NEXT_TYPE,
            when (kind) {
                WatchNextEntry.Kind.CONTINUE -> WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE
                WatchNextEntry.Kind.NEW -> WatchNextPrograms.WATCH_NEXT_TYPE_NEW
            },
        )
        put(
            WatchNextPrograms.COLUMN_TYPE,
            if (mediaType == MediaType.TV) PreviewPrograms.TYPE_TV_EPISODE else PreviewPrograms.TYPE_MOVIE,
        )
        put(WatchNextPrograms.COLUMN_TITLE, title)
        season?.let { put(WatchNextPrograms.COLUMN_SEASON_DISPLAY_NUMBER, it.toString()) }
        episode?.let { put(WatchNextPrograms.COLUMN_EPISODE_DISPLAY_NUMBER, it.toString()) }
        episodeTitle?.let { put(WatchNextPrograms.COLUMN_EPISODE_TITLE, it) }
        imageUrl?.let {
            put(WatchNextPrograms.COLUMN_POSTER_ART_URI, it)
            put(WatchNextPrograms.COLUMN_POSTER_ART_ASPECT_RATIO, PreviewPrograms.ASPECT_RATIO_16_9)
        }
        if (durationMs > 0) put(WatchNextPrograms.COLUMN_DURATION_MILLIS, durationMs.toInt())
        if (positionMs > 0) put(WatchNextPrograms.COLUMN_LAST_PLAYBACK_POSITION_MILLIS, positionMs.toInt())
        put(WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS, lastEngagementMs)
        put(WatchNextPrograms.COLUMN_INTENT_URI, link)
    }

    private fun idKey(programId: Long) = ID + programId

    private companion object {
        const val TAG = "WatchNext"
        const val PREFS = "watch_next"
        const val ID = "id:"
        const val DISMISSED = "dismissed:"
    }
}
