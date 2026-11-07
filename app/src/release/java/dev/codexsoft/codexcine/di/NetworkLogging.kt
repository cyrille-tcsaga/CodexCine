package dev.codexsoft.codexcine.di

import okhttp3.Interceptor

/** Release builds never log network traffic, and do not ship the logging interceptor at all. */
internal fun networkLoggingInterceptor(): Interceptor? = null
