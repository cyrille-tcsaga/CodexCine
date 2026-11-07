package dev.codexsoft.codexcine.ui.playback.web

import dev.codexsoft.codexcine.core.AppResult
import dev.codexsoft.codexcine.data.repository.CatalogRepository
import dev.codexsoft.codexcine.domain.model.Episode
import dev.codexsoft.codexcine.domain.model.MediaType
import dev.codexsoft.codexcine.domain.model.Season
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.format.DateTimeParseException

data class EpisodeRef(val season: Int, val episode: Int, val name: String? = null)

/** What the resolver needs from TMDB. Null means the request failed, as opposed to an empty answer. */
interface EpisodeCatalog {
    suspend fun seasons(showId: Int): List<Season>?
    suspend fun episodes(showId: Int, season: Int): List<Episode>?
}

class TmdbEpisodeCatalog(private val catalog: CatalogRepository) : EpisodeCatalog {
    override suspend fun seasons(showId: Int): List<Season>? =
        (catalog.details(showId, MediaType.TV) as? AppResult.Success)?.data?.seasons

    override suspend fun episodes(showId: Int, season: Int): List<Episode>? =
        (catalog.season(showId, season) as? AppResult.Success)?.data
}

/** Finds the neighbours of an episode. Successful answers are cached for the player's lifetime. */
class NextEpisodeResolver(
    private val catalog: EpisodeCatalog,
    private val today: () -> LocalDate = LocalDate::now,
) {
    private val mutex = Mutex()
    private val seasonsByShow = HashMap<Int, List<Season>>()
    private val episodesBySeason = HashMap<Pair<Int, Int>, List<Episode>>()

    suspend fun next(showId: Int, season: Int, episode: Int): EpisodeRef? {
        val inSeason = episodes(showId, season)
            .orEmpty()
            .firstOrNull { it.episodeNumber == episode + 1 }
        if (inSeason != null) {
            // A date in the future means not out yet. No date at all does not prove that.
            return if (inSeason.isInFuture()) null else inSeason.toRef()
        }

        val following = seasons(showId).orEmpty().firstOrNull { it.seasonNumber == season + 1 } ?: return null
        val first = episodes(showId, following.seasonNumber)
            .orEmpty()
            .minByOrNull { it.episodeNumber }
            ?: return null
        return if (first.hasAired()) first.toRef() else null
    }

    suspend fun previous(showId: Int, season: Int, episode: Int): EpisodeRef? {
        episodes(showId, season).orEmpty()
            .firstOrNull { it.episodeNumber == episode - 1 }
            ?.let { return it.toRef() }
        return episodes(showId, season - 1).orEmpty().maxByOrNull { it.episodeNumber }?.toRef()
    }

    suspend fun episodeName(showId: Int, season: Int, episode: Int): String? =
        episodes(showId, season).orEmpty().firstOrNull { it.episodeNumber == episode }?.name

    private suspend fun seasons(showId: Int): List<Season>? = mutex.withLock {
        seasonsByShow[showId] ?: catalog.seasons(showId)?.also { seasonsByShow[showId] = it }
    }

    private suspend fun episodes(showId: Int, season: Int): List<Episode>? {
        if (season < 0) return null
        val key = showId to season
        return mutex.withLock {
            episodesBySeason[key] ?: catalog.episodes(showId, season)?.also { episodesBySeason[key] = it }
        }
    }

    private fun Episode.airDateOrNull(): LocalDate? = try {
        airDate?.takeIf { it.isNotBlank() }?.let(LocalDate::parse)
    } catch (_: DateTimeParseException) {
        null
    }

    private fun Episode.isInFuture(): Boolean = airDateOrNull()?.isAfter(today()) == true

    private fun Episode.hasAired(): Boolean = airDateOrNull()?.isAfter(today()) == false

    private fun Episode.toRef() = EpisodeRef(seasonNumber, episodeNumber, name)
}
