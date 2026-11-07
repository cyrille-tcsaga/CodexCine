package dev.codexsoft.codexcine.di

import android.content.Context
import dev.codexsoft.codexcine.BuildConfig
import dev.codexsoft.codexcine.data.playback.SampleStreamResolver
import dev.codexsoft.codexcine.data.playback.StreamSourceResolver
import dev.codexsoft.codexcine.data.progress.WatchProgressRepository
import dev.codexsoft.codexcine.data.remote.RetryInterceptor
import dev.codexsoft.codexcine.data.remote.TmdbApi
import dev.codexsoft.codexcine.data.remote.TmdbAuthInterceptor
import dev.codexsoft.codexcine.data.remote.TmdbJson
import dev.codexsoft.codexcine.data.repository.CatalogRepository
import dev.codexsoft.codexcine.data.repository.MyListRepository
import dev.codexsoft.codexcine.data.vidzy.VidzyApi
import dev.codexsoft.codexcine.data.vidzy.VidzyJson
import dev.codexsoft.codexcine.data.vidzy.VidzyRepository
import dev.codexsoft.codexcine.data.vidzy.VidzyStreamSourceResolver
import dev.codexsoft.codexcine.data.vidzy.VidzyUrlBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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

    val watchProgressRepository: WatchProgressRepository by lazy { WatchProgressRepository(appContext) }

    /**
     * For writes that must outlive the screen requesting them, such as saving progress while the
     * player closes. Single-threaded so that writes are applied in the order they were requested.
     */
    val persistenceScope: CoroutineScope by lazy {
        CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))
    }

    /** Deliberately separate from [okHttpClient]: the TMDB token must never reach Vidzy. */
    private val vidzyHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .apply { networkLoggingInterceptor()?.let(::addInterceptor) }
            .build()
    }

    private val vidzyApi: Lazy<VidzyApi> = lazy {
        Retrofit.Builder()
            .baseUrl(VidzyUrlBuilder.BASE_URL)
            .client(vidzyHttpClient)
            .addConverterFactory(VidzyJson.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(VidzyApi::class.java)
    }

    val vidzyRepository: VidzyRepository by lazy { VidzyRepository(vidzyApi) }

    /** The active video source. To plug in another provider, register its resolver here. */
    val streamSourceResolver: StreamSourceResolver by lazy {
        if (BuildConfig.USE_SAMPLE_STREAM) SampleStreamResolver() else VidzyStreamSourceResolver(vidzyRepository)
    }

    private companion object {
        const val TMDB_BASE_URL = "https://api.themoviedb.org/3/"
        const val HTTP_CACHE_BYTES = 20L * 1024L * 1024L
    }
}
