package dev.codexsoft.codexcine.ui.common

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DecodeFormat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ExecutionException

private const val TAG = "BackgroundImages"

/**
 * Loads a full-screen backdrop as a bitmap the caller owns.
 *
 * Backgrounds cross-fade, so the previous bitmap is still drawn after the next one is ready. Glide
 * may recycle a bitmap once its request is cleared, which would crash that draw. Keeping every target
 * alive instead would pin a full-screen bitmap per card visited. Copying gives neither problem.
 *
 * Returns null when the image cannot be loaded.
 */
suspend fun loadBackgroundBitmap(context: Context, url: String, width: Int, height: Int): Bitmap? =
    withContext(Dispatchers.IO) {
        val glide = Glide.with(context.applicationContext)
        val request = glide.asBitmap()
            .load(url)
            .format(DecodeFormat.PREFER_RGB_565)
            .centerCrop()
            .submit(width, height)
        try {
            request.get().copy(Bitmap.Config.RGB_565, false)
        } catch (e: CancellationException) {
            throw e
        } catch (e: ExecutionException) {
            Log.w(TAG, "Could not load background $url", e)
            null
        } finally {
            glide.clear(request)
        }
    }
