package dev.codexsoft.codexcine.ui.browse

import androidx.leanback.widget.ImageCardView
import dev.codexsoft.codexcine.R
import dev.codexsoft.codexcine.data.progress.WatchProgress
import dev.codexsoft.codexcine.ui.common.BaseCardPresenter
import dev.codexsoft.codexcine.ui.common.ProgressBarOverlay
import dev.codexsoft.codexcine.ui.common.runtimeText
import kotlin.math.ceil

/** A wide still with how far the viewer got, drawn as a thin bar along the bottom of the image. */
class ContinueWatchingCardPresenter : BaseCardPresenter(widthDp = 264, heightDp = 148) {

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        val progress = item as? WatchProgress ?: return
        val card = viewHolder.view as ImageCardView
        val context = card.context
        card.titleText = progress.title

        val minutesLeft = ceil((progress.durationSeconds - progress.positionSeconds) / 60).toInt()
        val timeLeft = context.runtimeText(minutesLeft)?.let { context.getString(R.string.card_time_left, it) }
        val episode = if (progress.episode > 0) {
            context.getString(R.string.playback_episode_subtitle, progress.season, progress.episode)
        } else {
            null
        }
        card.contentText = listOfNotNull(episode, timeLeft).joinToString(context.getString(R.string.separator))
        card.loadImage(progress.backdropUrl ?: progress.posterUrl)
        card.setImageOverlay(ProgressBarOverlay(context, progress.fraction))
    }
}
