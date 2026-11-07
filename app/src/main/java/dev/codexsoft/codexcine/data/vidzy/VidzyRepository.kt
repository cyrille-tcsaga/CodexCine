package dev.codexsoft.codexcine.data.vidzy

import android.util.Log
import dev.codexsoft.codexcine.core.AppResult
import dev.codexsoft.codexcine.core.ErrorKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import java.io.IOException

data class VidzyAvailability(
    val isAvailable: Boolean,
    val detectedType: String?,
    val languages: List<String>,
)

/** Availability per TMDB id. Successes are cached for 15 minutes, failures are never cached. */
class VidzyRepository(
    private val api: Lazy<VidzyApi>,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private class CacheEntry(val value: VidzyAvailability, val fetchedAtMs: Long)

    private val mutex = Mutex()
    private val cache = object : LinkedHashMap<Int, CacheEntry>(16, 0.75f, /* accessOrder = */ true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, CacheEntry>?) = size > MAX_ENTRIES
    }

    suspend fun availability(tmdbId: Int): AppResult<VidzyAvailability> {
        cached(tmdbId)?.let { return AppResult.Success(it) }
        return try {
            // Building the client and Retrofit's first call are slow enough to freeze a screen on a TV box.
            val value = withContext(Dispatchers.IO) { api.value.availability(tmdbId) }.toDomain()
            mutex.withLock { cache[tmdbId] = CacheEntry(value, clock()) }
            AppResult.Success(value)
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            failure(e, ErrorKind.NO_CONNECTION)
        } catch (e: retrofit2.HttpException) {
            failure(e, ErrorKind.GENERIC)
        } catch (e: SerializationException) {
            failure(e, ErrorKind.GENERIC)
        }
    }

    private suspend fun cached(tmdbId: Int): VidzyAvailability? = mutex.withLock {
        val entry = cache[tmdbId] ?: return@withLock null
        if (clock() - entry.fetchedAtMs < TTL_MS) {
            entry.value
        } else {
            cache.remove(tmdbId)
            null
        }
    }

    private fun failure(e: Exception, kind: ErrorKind): AppResult.Error {
        Log.w(TAG, "Availability request failed: ${e.javaClass.simpleName}")
        return AppResult.Error(kind, e.message)
    }

    private fun VidzyAvailabilityDto.toDomain() = VidzyAvailability(
        isAvailable = available == true,
        detectedType = detectedType,
        languages = languages.orEmpty().filter { it.isNotBlank() },
    )

    private companion object {
        const val TAG = "VidzyRepository"
        const val MAX_ENTRIES = 50
        const val TTL_MS = 15L * 60L * 1000L
    }
}
