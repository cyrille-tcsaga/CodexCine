package dev.codexsoft.codexcine.data.remote

import okhttp3.Interceptor
import okhttp3.Response

class TmdbAuthInterceptor(private val apiToken: String) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val originalUrl = originalRequest.url

        val newUrl = originalUrl.newBuilder().apply {
            if (originalUrl.queryParameter("language") == null) {
                addQueryParameter("language", "fr-FR")
            }
            if (originalUrl.queryParameter("include_adult") == null) {
                addQueryParameter("include_adult", "false")
            }
        }.build()

        val newRequest = originalRequest.newBuilder()
            .url(newUrl)
            .header("Authorization", "Bearer $apiToken")
            .header("accept", "application/json")
            .build()

        return chain.proceed(newRequest)
    }
}
