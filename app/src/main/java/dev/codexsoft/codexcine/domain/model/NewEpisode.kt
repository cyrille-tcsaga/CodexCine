package dev.codexsoft.codexcine.domain.model

import java.time.LocalDate
import java.time.format.DateTimeParseException

/** A recently aired episode of a show the viewer follows, not watched yet. */
data class NewEpisode(
    val show: MediaItem,
    val episode: Episode,
    /** True once the viewer has watched part of the show: the card then plays the episode itself. */
    val isShowStarted: Boolean,
)

/** Which followed shows get a "Nouveaux épisodes" card. Pure, so that it can be tested. */
object NewEpisodeRules {

    const val WINDOW_DAYS = 30L

    /** The shows looked up on TMDB, followed shows first: one request each. */
    const val MAX_SHOWS = 20

    /**
     * @param followed the shows to look at, each with its last aired episode (null if TMDB knows none)
     * @param furthestWatched by show id, the furthest episode the viewer has finished
     * @param inProgress [key]s of the episodes in "Continuer à regarder", already shown there
     * @param startedShows ids of the shows the viewer has watched some of
     */
    fun select(
        followed: List<Pair<MediaItem, Episode?>>,
        furthestWatched: Map<Int, Pair<Int, Int>>,
        inProgress: Set<String>,
        startedShows: Set<Int>,
        today: LocalDate,
    ): List<NewEpisode> {
        val oldest = today.minusDays(WINDOW_DAYS)
        return followed
            .mapNotNull { (show, episode) ->
                if (episode == null || episode.seasonNumber <= 0 || episode.episodeNumber <= 0) return@mapNotNull null
                val aired = episode.airDate.toDateOrNull() ?: return@mapNotNull null
                if (aired.isBefore(oldest) || aired.isAfter(today)) return@mapNotNull null
                val furthest = furthestWatched[show.id]
                if (furthest != null && !episode.isAfter(furthest)) return@mapNotNull null
                if (key(show.id, episode.seasonNumber, episode.episodeNumber) in inProgress) return@mapNotNull null
                Triple(show, episode, aired)
            }
            .distinctBy { (show, _, _) -> show.id }
            .sortedByDescending { (_, _, aired) -> aired }
            .map { (show, episode, _) -> NewEpisode(show, episode, isShowStarted = show.id in startedShows) }
    }

    fun key(showId: Int, season: Int, episode: Int) = "$showId:$season:$episode"

    private fun Episode.isAfter(other: Pair<Int, Int>): Boolean =
        seasonNumber > other.first || (seasonNumber == other.first && episodeNumber > other.second)

    private fun String?.toDateOrNull(): LocalDate? = try {
        this?.takeIf { it.isNotBlank() }?.let(LocalDate::parse)
    } catch (_: DateTimeParseException) {
        null
    }
}
