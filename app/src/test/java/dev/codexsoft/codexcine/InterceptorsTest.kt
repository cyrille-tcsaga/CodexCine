package dev.codexsoft.codexcine

import dev.codexsoft.codexcine.core.ErrorKind
import dev.codexsoft.codexcine.data.remote.RetryInterceptor
import dev.codexsoft.codexcine.data.remote.TmdbAuthInterceptor
import dev.codexsoft.codexcine.data.repository.toErrorKind
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import retrofit2.HttpException
import java.io.IOException

class InterceptorsTest {

    /** Answers with the scripted responses instead of touching the network. */
    private class FakeServer(private vararg val script: Pair<Int, String?>) : Interceptor {
        val requests = mutableListOf<Request>()

        override fun intercept(chain: Interceptor.Chain): Response {
            val (code, retryAfter) = script[requests.size.coerceAtMost(script.lastIndex)]
            requests += chain.request()
            return Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("scripted")
                .apply { retryAfter?.let { header("Retry-After", it) } }
                .body("{}".toResponseBody("application/json".toMediaType()))
                .build()
        }
    }

    private fun execute(vararg interceptors: Interceptor): Response {
        val client = OkHttpClient.Builder().apply { interceptors.forEach { addInterceptor(it) } }.build()
        return client.newCall(Request.Builder().url("https://api.themoviedb.org/3/movie/popular").build()).execute()
    }

    @Test
    fun `token goes in the header, never in the query`() {
        val server = FakeServer(200 to null)
        execute(TmdbAuthInterceptor("secret"), server).close()

        val request = server.requests.single()
        assertEquals("Bearer secret", request.header("Authorization"))
        assertEquals("application/json", request.header("accept"))
        assertEquals("fr-FR", request.url.queryParameter("language"))
        assertEquals("false", request.url.queryParameter("include_adult"))
        assertEquals(false, request.url.toString().contains("secret"))
    }

    @Test
    fun `retries with exponential backoff, at most three times`() {
        val delays = mutableListOf<Long>()
        val server = FakeServer(503 to null)
        val response = execute(RetryInterceptor(sleep = { delays += it }), server)
        response.close()

        assertEquals(listOf(500L, 1_000L, 2_000L), delays)
        assertEquals(4, server.requests.size)
        assertEquals(503, response.code)
    }

    @Test
    fun `honours retry after on 429 and stops as soon as a call succeeds`() {
        val delays = mutableListOf<Long>()
        val server = FakeServer(429 to "2", 200 to null)
        val response = execute(RetryInterceptor(sleep = { delays += it }), server)
        response.close()

        assertEquals(listOf(2_000L), delays)
        assertEquals(200, response.code)
    }

    @Test
    fun `does not wait for an unreasonable retry after`() {
        val delays = mutableListOf<Long>()
        val server = FakeServer(429 to "3600")
        val response = execute(RetryInterceptor(sleep = { delays += it }), server)
        response.close()

        assertEquals(emptyList<Long>(), delays)
        assertEquals(429, response.code)
    }

    @Test
    fun `other errors are not retried`() {
        val server = FakeServer(404 to null)
        execute(RetryInterceptor(sleep = { throw AssertionError("must not sleep") }), server).close()

        assertEquals(1, server.requests.size)
        assertNull(server.requests.single().header("Authorization"))
    }

    @Test
    fun `errors map to their kind`() {
        fun http(code: Int) = HttpException(
            retrofit2.Response.error<String>(code, "{}".toResponseBody("application/json".toMediaType())),
        )

        assertEquals(ErrorKind.INVALID_TOKEN, http(401).toErrorKind())
        assertEquals(ErrorKind.GENERIC, http(500).toErrorKind())
        assertEquals(ErrorKind.NO_CONNECTION, IOException("offline").toErrorKind())
        assertEquals(ErrorKind.GENERIC, IllegalStateException("bug").toErrorKind())
    }
}
