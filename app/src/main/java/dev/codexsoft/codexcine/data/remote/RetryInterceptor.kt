package dev.codexsoft.codexcine.data.remote

import okhttp3.Interceptor
import okhttp3.Response
import java.io.InterruptedIOException

/**
 * Retries HTTP 429 and 502/503/504, honouring `Retry-After` when present and otherwise backing off
 * exponentially. It blocks an OkHttp worker thread, never the main thread.
 */
class RetryInterceptor(
    private val sleep: (Long) -> Unit = { Thread.sleep(it) },
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        var response = chain.proceed(request)
        var attempt = 0

        while (response.code in RETRYABLE_CODES && attempt < BACKOFF_MS.size) {
            val delayMs = retryAfterMs(response) ?: BACKOFF_MS[attempt]
            // A server asking for a long wait gets its answer surfaced rather than a frozen screen.
            if (delayMs > MAX_DELAY_MS) return response

            response.close()
            try {
                sleep(delayMs)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                throw InterruptedIOException("Interrupted while waiting to retry")
            }
            attempt++
            response = chain.proceed(request)
        }
        return response
    }

    private fun retryAfterMs(response: Response): Long? =
        response.header("Retry-After")?.trim()?.toLongOrNull()?.takeIf { it >= 0 }?.times(1_000L)

    private companion object {
        val RETRYABLE_CODES = setOf(429, 502, 503, 504)
        val BACKOFF_MS = listOf(500L, 1_000L, 2_000L)
        const val MAX_DELAY_MS = 15_000L
    }
}
