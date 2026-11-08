package dev.codexsoft.codexcine.data.repository

import android.util.Log
import dev.codexsoft.codexcine.core.AppResult
import dev.codexsoft.codexcine.core.ErrorKind
import dev.codexsoft.codexcine.core.ImageUrls
import dev.codexsoft.codexcine.data.mapper.toEpisode
import dev.codexsoft.codexcine.data.mapper.toMediaDetails
import dev.codexsoft.codexcine.data.mapper.toMediaItem
import dev.codexsoft.codexcine.data.mapper.toPersonDetails
import dev.codexsoft.codexcine.data.mapper.toSearchResults
import dev.codexsoft.codexcine.data.mapper.toSaga
import dev.codexsoft.codexcine.data.mapper.toTrendingItems
import dev.codexsoft.codexcine.data.remote.TmdbApi
import dev.codexsoft.codexcine.data.remote.dto.TmdbMovieDto
import dev.codexsoft.codexcine.data.remote.dto.TmdbPaginatedResponse
import dev.codexsoft.codexcine.data.remote.dto.TmdbTvDto
import dev.codexsoft.codexcine.domain.model.CategoryType
import dev.codexsoft.codexcine.domain.model.DiscoverQuery
import dev.codexsoft.codexcine.domain.model.Genre
import dev.codexsoft.codexcine.domain.model.GridFilters
import dev.codexsoft.codexcine.domain.model.Episode
import dev.codexsoft.codexcine.domain.model.MediaDetails
import dev.codexsoft.codexcine.domain.model.MediaItem
import dev.codexsoft.codexcine.domain.model.MediaPage
import dev.codexsoft.codexcine.domain.model.MediaType
import dev.codexsoft.codexcine.domain.model.PersonDetails
import dev.codexsoft.codexcine.domain.model.Saga
import dev.codexsoft.codexcine.domain.model.SearchResults
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import java.time.LocalDate
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException

class CatalogRepository(
    private val api: TmdbApi,
    private val hasToken: Boolean,
) {
    private val configurationMutex = Mutex()
    private var configurationRequested = false

    /** Emits one result per remote home category, in completion order, at most 4 requests at a time. */
    fun homeRows(): Flow<Pair<CategoryType, AppResult<MediaPage>>> = channelFlow {
        ensureConfiguration()
        val permits = Semaphore(MAX_PARALLEL_REQUESTS)
        CategoryType.entries.filter { it.isRemote }.forEach { category ->
            launch {
                permits.withPermit {
                    send(category to categoryPage(category, page = 1))
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    suspend fun categoryPage(category: CategoryType, page: Int): AppResult<MediaPage> = call {
        when (category) {
            CategoryType.TRENDING -> api.getTrending(page).toPage(page) { it.toTrendingItems() }
            CategoryType.POPULAR_MOVIES -> api.getPopularMovies(page).toMoviePage(page)
            CategoryType.NOW_PLAYING_MOVIES -> api.getNowPlayingMovies(page).toMoviePage(page)
            CategoryType.TOP_RATED_MOVIES -> api.getTopRatedMovies(page).toMoviePage(page)
            CategoryType.UPCOMING_MOVIES -> api.getUpcomingMovies(page).toMoviePage(page)
            CategoryType.POPULAR_TV -> api.getPopularTv(page).toTvPage(page)
            CategoryType.TOP_RATED_TV -> api.getTopRatedTv(page).toTvPage(page)
            CategoryType.ON_THE_AIR_TV -> api.getOnTheAirTv(page).toTvPage(page)
            CategoryType.LATEST_MOVIES -> api.discoverLatestMovies(today(), page = page).toMoviePage(page)
            CategoryType.LATEST_TV -> api.discoverLatestTv(today(), page = page).toTvPage(page)
            CategoryType.GENRE_ACTION,
            CategoryType.GENRE_ANIMATION,
            CategoryType.GENRE_COMEDY ->
                api.discoverMoviesByGenre(requireNotNull(category.genreId), page = page).toMoviePage(page)
            CategoryType.CONTINUE_WATCHING,
            CategoryType.NEW_EPISODES,
            CategoryType.MY_LIST,
            CategoryType.ABOUT -> MediaPage(emptyList(), page, totalPages = page)
        }
    }

    suspend fun details(id: Int, type: MediaType): AppResult<MediaDetails> = call {
        when (type) {
            MediaType.MOVIE -> api.getMovieDetails(id).toMediaDetails()
            MediaType.TV -> api.getTvDetails(id).toMediaDetails()
            MediaType.PERSON -> throw IllegalArgumentException("A person has no details screen")
        }
    }

    suspend fun season(tvId: Int, seasonNumber: Int): AppResult<List<Episode>> = call {
        api.getTvSeason(tvId, seasonNumber).episodes.orEmpty().map { it.toEpisode(seasonNumber) }
    }

    /** A category narrowed by [filters]; without any, the category itself. */
    suspend fun filteredPage(category: CategoryType, filters: GridFilters, page: Int): AppResult<MediaPage> {
        if (!filters.isActive) return categoryPage(category, page)
        return call {
            val params = DiscoverQuery.build(category, filters, LocalDate.now())
            when (DiscoverQuery.mediaTypeOf(category)) {
                MediaType.TV -> api.discoverTv(params, page).toTvPage(page)
                else -> api.discoverMovies(params, page).toMoviePage(page)
            }
        }
    }

    /** TMDB's genres for movies or shows (their ids differ), in French, by name. */
    suspend fun genres(type: MediaType): AppResult<List<Genre>> = call {
        val list = if (type == MediaType.TV) api.getTvGenres() else api.getMovieGenres()
        list.genres.orEmpty()
            .mapNotNull { dto -> dto.name?.takeIf { it.isNotBlank() }?.let { Genre(dto.id, it) } }
            .sortedBy { it.name }
    }

    suspend fun saga(collectionId: Int): AppResult<Saga> = call { api.getCollection(collectionId).toSaga() }

    /** The show, with its most recently aired episode (null when TMDB knows none). */
    suspend fun showWithLastEpisode(tvId: Int): AppResult<Pair<MediaItem, Episode?>> = call {
        val show = api.getTvSummary(tvId)
        show.toMediaItem() to show.lastEpisodeToAir?.toEpisode(fallbackSeasonNumber = 0)
    }

    suspend fun search(query: String): AppResult<SearchResults> = call {
        api.searchMulti(query).results.orEmpty().toSearchResults()
    }

    suspend fun person(id: Int): AppResult<PersonDetails> = call {
        api.getPersonDetails(id).toPersonDetails()
    }

    /** Today's date as TMDB filters expect it, so the "latest" rows leave out announced titles. */
    private fun today(): String = LocalDate.now().toString()

    /** Fetches the image base URL once per process. A failure keeps the built-in default. */
    private suspend fun ensureConfiguration() {
        if (!hasToken) return
        configurationMutex.withLock {
            if (configurationRequested) return
            configurationRequested = true
            try {
                ImageUrls.setBaseUrl(api.getConfiguration().images?.secureBaseUrl)
            } catch (e: CancellationException) {
                configurationRequested = false
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Could not read the TMDB configuration, using the default image URL", e)
            }
        }
    }

    private suspend fun <T> call(block: suspend () -> T): AppResult<T> {
        if (!hasToken) return AppResult.Error(ErrorKind.INVALID_TOKEN)
        return withContext(Dispatchers.IO) {
            try {
                ensureConfiguration()
                AppResult.Success(block())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "TMDB request failed", e)
                AppResult.Error(e.toErrorKind(), e.message)
            }
        }
    }

    private companion object {
        const val TAG = "CatalogRepository"
        const val MAX_PARALLEL_REQUESTS = 4
    }
}

internal fun Throwable.toErrorKind(): ErrorKind = when {
    this is HttpException && code() == 401 -> ErrorKind.INVALID_TOKEN
    this is IOException -> ErrorKind.NO_CONNECTION
    else -> ErrorKind.GENERIC
}

private fun <T> TmdbPaginatedResponse<T>.toPage(
    requestedPage: Int,
    transform: (List<T>) -> List<MediaItem>,
): MediaPage = MediaPage(
    items = transform(results.orEmpty()),
    page = page ?: requestedPage,
    totalPages = totalPages ?: requestedPage,
)

private fun TmdbPaginatedResponse<TmdbMovieDto>.toMoviePage(page: Int) =
    toPage(page) { dtos -> dtos.map { it.toMediaItem() } }

private fun TmdbPaginatedResponse<TmdbTvDto>.toTvPage(page: Int) =
    toPage(page) { dtos -> dtos.map { it.toMediaItem() } }
