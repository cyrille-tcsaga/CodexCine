package dev.codexsoft.codexcine.ui.browse

import android.content.Intent
import androidx.fragment.app.Fragment
import dev.codexsoft.codexcine.data.progress.WatchProgress
import dev.codexsoft.codexcine.domain.model.MediaType
import dev.codexsoft.codexcine.domain.model.NewEpisode
import dev.codexsoft.codexcine.ui.details.DetailsActivity
import dev.codexsoft.codexcine.ui.error.AboutActivity
import dev.codexsoft.codexcine.ui.grid.GridActivity
import dev.codexsoft.codexcine.ui.playback.PlaybackActivity

/*
 * What a card of the home page does, for the Leanback and the Compose home alike.
 */

/** Plays a title in progress. [startOver] ignores the saved position, which stays until the next save. */
internal fun Fragment.playProgress(progress: WatchProgress, autoResume: Boolean, startOver: Boolean = false) {
    val type = progress.type ?: return
    startActivity(
        PlaybackActivity.newIntent(
            requireContext(),
            mediaId = progress.tmdbId,
            mediaType = type,
            title = progress.title,
            seasonNumber = progress.season.takeIf { type == MediaType.TV },
            episodeNumber = progress.episode.takeIf { type == MediaType.TV },
            autoResume = autoResume,
            startOver = startOver,
        ),
    )
}

/** A show already started plays the new episode; otherwise its page opens, to start from the beginning. */
internal fun Fragment.openNewEpisode(item: NewEpisode) {
    val show = item.show
    if (!item.isShowStarted) {
        startActivity(DetailsActivity.newIntent(requireContext(), show.id, show.mediaType, show.title))
        return
    }
    startActivity(
        PlaybackActivity.newIntent(
            requireContext(),
            mediaId = show.id,
            mediaType = MediaType.TV,
            title = show.title,
            seasonNumber = item.episode.seasonNumber,
            episodeNumber = item.episode.episodeNumber,
        ),
    )
}

internal fun Fragment.openSeeAll(card: ActionCard.SeeAll) {
    startActivity(GridActivity.newIntent(requireContext(), card.category, card.categoryTitle))
}

internal fun Fragment.openAbout() {
    startActivity(Intent(requireContext(), AboutActivity::class.java))
}
