package dev.codexsoft.codexcine.di

import android.content.Context
import dev.codexsoft.codexcine.BuildConfig
import dev.codexsoft.codexcine.data.playback.NoStreamSourceResolver
import dev.codexsoft.codexcine.data.playback.SampleStreamResolver
import dev.codexsoft.codexcine.data.playback.StreamSourceResolver
import dev.codexsoft.codexcine.data.remote.RetryInterceptor
import dev.codexsoft.codexcine.data.remote.TmdbApi
import dev.codexsoft.codexcine.data.remote.TmdbAuthInterceptor
import dev.codexsoft.codexcine.data.remote.TmdbJson
import dev.codexsoft.codexcine.data.repository.CatalogRepository
import dev.codexsoft.codexcine.data.repository.MyListRepository
import okhttp3.Cache
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit

/** Hand-written dependency wiring. Everything is lazy so nothing is built before it is needed. */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .cache(Cache(File(appContext.cacheDir, "http"), HTTP_CACHE_BYTES))
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(TmdbAuthInterceptor(BuildConfig.TMDB_ACCESS_TOKEN))
            .addInterceptor(RetryInterceptor())
            .apply { networkLoggingInterceptor()?.let(::addInterceptor) }
            .build()
    }

    private val tmdbApi: TmdbApi by lazy {
        Retrofit.Builder()
            .baseUrl(TMDB_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(TmdbJson.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(TmdbApi::class.java)
    }

    val catalogRepository: CatalogRepository by lazy {
        CatalogRepository(tmdbApi, hasToken = BuildConfig.TMDB_ACCESS_TOKEN.isNotBlank())
    }

    val myListRepository: MyListRepository by lazy { MyListRepository(appContext) }

    /** Register a real implementation here to plug in an actual video source. */
    val streamSourceResolver: StreamSourceResolver by lazy {
        if (BuildConfig.USE_SAMPLE_STREAM) SampleStreamResolver() else NoStreamSourceResolver()
    }

    private companion object {
        const val TMDB_BASE_URL = "https://api.themoviedb.org/3/"
        const val HTTP_CACHE_BYTES = 20L * 1024L * 1024L
    }
}
