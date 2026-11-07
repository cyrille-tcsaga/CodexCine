package dev.codexsoft.codexcine.di

import okhttp3.Interceptor
import okhttp3.logging.HttpLoggingInterceptor

/** Debug builds log one line per request. The token is redacted in case the level is ever raised. */
internal fun networkLoggingInterceptor(): Interceptor? =
    HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
        redactHeader("Authorization")
    }
