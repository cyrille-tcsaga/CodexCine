package dev.codexsoft.codexcine.data.update

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

@Serializable
data class GitHubRelease(
    @SerialName("tag_name") val tagName: String? = null,
    @SerialName("draft") val draft: Boolean = false,
    @SerialName("prerelease") val prerelease: Boolean = false,
    @SerialName("body") val body: String? = null,
    @SerialName("assets") val assets: List<GitHubAsset> = emptyList(),
)

@Serializable
data class GitHubAsset(
    @SerialName("name") val name: String = "",
    @SerialName("browser_download_url") val downloadUrl: String = "",
    @SerialName("size") val size: Long = 0,
)

/**
 * Looks for a newer release on GitHub and downloads its APK. [repository] is "owner/name" and must be
 * public: the app carries no GitHub credentials. Its own HTTP client: the TMDB token never goes there.
 */
class UpdateRepository(
    private val client: OkHttpClient,
    private val repository: String,
    private val installed: AppVersion?,
) {
    private val json = Json { ignoreUnknownKeys = true }

    val isEnabled: Boolean get() = REPOSITORY.matches(repository)

    /** The newer release, or null when there is none, or it could not be told. */
    suspend fun check(): AvailableUpdate? {
        if (!isEnabled) return null
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("https://api.github.com/repos/$repository/releases/latest")
                    .header("Accept", "application/vnd.github+json")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext null
                    val release = json.decodeFromString<GitHubRelease>(response.body.string())
                    UpdateRules.newerThan(installed, release)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                Log.w(TAG, "Update check failed", e)
                null
            } catch (e: SerializationException) {
                Log.w(TAG, "Unexpected release answer", e)
                null
            }
        }
    }

    /** Downloads the APK into [directory], replacing an earlier download. Null when it failed. */
    suspend fun download(update: AvailableUpdate, directory: File): File? = withContext(Dispatchers.IO) {
        val target = File(directory.apply { mkdirs() }, "codexcine-${update.version}.apk")
        try {
            directory.listFiles()?.forEach { it.delete() }
            client.newCall(Request.Builder().url(update.apkUrl).build()).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body
                if (body.contentLength() > UpdateRules.MAX_APK_BYTES) return@withContext null
                body.byteStream().use { input ->
                    target.outputStream().use { output ->
                        val buffer = ByteArray(BUFFER_BYTES)
                        var total = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            total += read
                            if (total > UpdateRules.MAX_APK_BYTES) throw IOException("Larger than an app can be")
                            output.write(buffer, 0, read)
                        }
                    }
                }
            }
            target
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            Log.w(TAG, "Update download failed", e)
            target.delete()
            null
        }
    }

    private companion object {
        const val TAG = "Update"
        const val BUFFER_BYTES = 64 * 1024
        val REPOSITORY = Regex("""[A-Za-z0-9-]{1,39}/[A-Za-z0-9._-]{1,100}""")
    }
}
