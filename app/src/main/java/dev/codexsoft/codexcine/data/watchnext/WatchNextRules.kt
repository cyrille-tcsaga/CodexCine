package dev.codexsoft.codexcine.data.watchnext

import dev.codexsoft.codexcine.data.progress.WatchProgress
import dev.codexsoft.codexcine.domain.model.MediaType
import dev.codexsoft.codexcine.domain.model.NewEpisode
import dev.codexsoft.codexcine.core.PlayLink

/** One card of the system home screen's "Play next" row, as the app sees it. */
data class WatchNextEntry(
    /** Unique per entry and kind: also how a card the viewer removed is recognised later. */
    val key: String,
    val kind: Kind,
    val mediaType: MediaType,
    val title: String,
    val season: Int?,
    val episode: Int?,
    val episodeTitle: String?,
    val imageUrl: String?,
    val positionMs: Long,
    val durationMs: Long,
    val lastEngagementMs: Long,
    val link: String,
) {
    enum class Kind(val prefix: String) { CONTINUE("continue:"), NEW("new:") }
}

/** What to publish. Pure, so that it can be tested. */
object WatchNextRules {

    /** The system row shows a handful; more would only be noise for the viewer. */
    const val MAX_CONTINUE = 10
    const val MAX_NEW = 5

    /**
     * @param dismissedAt by entry key, when the viewer removed that card from the home screen. A card stays
     *   removed until the title is watched again after that.
     */
    fun continueEntries(progress: List<WatchProgress>, dismissedAt: Map<String, Long>): List<WatchNextEntry> =
        progress
            .filter { it.tmdbId > 0 && it.positionSeconds > 0 && it.durationSeconds > 0 }
            .mapNotNull { entry ->
                val type = entry.type?.takeIf { it == MediaType.MOVIE || it == MediaType.TV } ?: return@mapNotNull null
                val isShow = type == MediaType.TV
                val key = WatchNextEntry.Kind.CONTINUE.prefix + entry.key
                if ((dismissedAt[key] ?: Long.MIN_VALUE) >= entry.updatedAtMs) return@mapNotNull null
                WatchNextEntry(
                    key = key,
                    kind = WatchNextEntry.Kind.CONTINUE,
                    mediaType = type,
                    title = entry.title,
                    season = entry.season.takeIf { isShow },
                    episode = entry.episode.takeIf { isShow },
                    episodeTitle = null,
                    imageUrl = entry.backdropUrl ?: entry.posterUrl,
                    positionMs = (entry.positionSeconds * MS).toLong(),
                    durationMs = (entry.durationSeconds * MS).toLong(),
                    lastEngagementMs = entry.updatedAtMs,
                    link = PlayLink(
                        type,
                        entry.tmdbId,
                        entry.season.takeIf { isShow },
                        entry.episode.takeIf { isShow },
                        entry.title,
                    ).toUri(),
                )
            }
            .sortedByDescending { it.lastEngagementMs }
            .take(MAX_CONTINUE)

    /** Only for shows already started: a new episode of a show never watched is not "next" for the viewer. */
    fun newEntries(newEpisodes: List<NewEpisode>, dismissed: Set<String>, nowMs: Long): List<WatchNextEntry> =
        newEpisodes
            .filter { it.isShowStarted }
            .map { item ->
                val episode = item.episode
                val key = WatchNextEntry.Kind.NEW.prefix +
                    "${item.show.id}:${episode.seasonNumber}:${episode.episodeNumber}"
                WatchNextEntry(
                    key = key,
                    kind = WatchNextEntry.Kind.NEW,
                    mediaType = MediaType.TV,
                    title = item.show.title,
                    season = episode.seasonNumber,
                    episode = episode.episodeNumber,
                    episodeTitle = episode.name.takeIf { it.isNotBlank() },
                    imageUrl = episode.stillUrl ?: item.show.backdropUrl,
                    positionMs = 0,
                    durationMs = episode.runtimeMinutes?.let { it * MINUTE_MS } ?: 0,
                    lastEngagementMs = nowMs,
                    link = PlayLink(MediaType.TV, item.show.id, episode.seasonNumber, episode.episodeNumber, item.show.title)
                        .toUri(),
                )
            }
            .filterNot { it.key in dismissed }
            .take(MAX_NEW)

    private const val MS = 1_000.0
    private const val MINUTE_MS = 60_000L
}
