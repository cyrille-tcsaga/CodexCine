package dev.codexsoft.codexcine.ui.season

import dev.codexsoft.codexcine.data.progress.EpisodeNumber
import dev.codexsoft.codexcine.domain.model.Episode
import java.time.LocalDate
import java.time.format.DateTimeParseException

/** An episode as the season screen shows it. [progress] is set while it is part-watched. */
data class EpisodeItem(
    val episode: Episode,
    val isWatched: Boolean,
    val progress: Float? = null,
) {
    val number: EpisodeNumber get() = EpisodeNumber(episode.seasonNumber, episode.episodeNumber)
}

enum class EpisodeAction(val isWatched: Boolean) {
    MARK_WATCHED(true),
    MARK_UNWATCHED(false),
    MARK_WATCHED_UP_TO_HERE(true),
    MARK_SEASON_WATCHED(true),
    MARK_SEASON_UNWATCHED(false),
}

/** What the long-press menu offers, and which episodes each choice changes. Pure, so that it can be tested. */
object EpisodeMenu {

    fun actions(target: EpisodeItem, season: List<EpisodeItem>, today: LocalDate): List<EpisodeAction> = buildList {
        add(if (target.isWatched) EpisodeAction.MARK_UNWATCHED else EpisodeAction.MARK_WATCHED)
        if (earlier(target, season).any { !it.isWatched && it.hasAired(today) }) {
            add(EpisodeAction.MARK_WATCHED_UP_TO_HERE)
        }
        val aired = season.filter { it.hasAired(today) }
        when {
            aired.isNotEmpty() && aired.all { it.isWatched } -> add(EpisodeAction.MARK_SEASON_UNWATCHED)
            aired.size > 1 -> add(EpisodeAction.MARK_SEASON_WATCHED)
        }
    }

    fun episodesFor(
        action: EpisodeAction,
        target: EpisodeItem,
        season: List<EpisodeItem>,
        today: LocalDate,
    ): List<EpisodeNumber> = when (action) {
        EpisodeAction.MARK_WATCHED, EpisodeAction.MARK_UNWATCHED -> listOf(target.number)
        EpisodeAction.MARK_WATCHED_UP_TO_HERE ->
            (earlier(target, season).filter { it.hasAired(today) } + target).map { it.number }
        EpisodeAction.MARK_SEASON_WATCHED -> season.filter { it.hasAired(today) }.map { it.number }
        EpisodeAction.MARK_SEASON_UNWATCHED -> season.map { it.number }
    }

    private fun earlier(target: EpisodeItem, season: List<EpisodeItem>) =
        season.filter { it.episode.episodeNumber < target.episode.episodeNumber }

    /** Only a date in the future means not out yet: many episodes have no date at all. */
    private fun EpisodeItem.hasAired(today: LocalDate): Boolean {
        val date = try {
            episode.airDate?.takeIf { it.isNotBlank() }?.let(LocalDate::parse)
        } catch (_: DateTimeParseException) {
            null
        }
        return date == null || !date.isAfter(today)
    }
}
