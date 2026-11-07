package dev.codexsoft.codexcine.ui.season

import android.content.Context
import androidx.leanback.widget.ImageCardView
import dev.codexsoft.codexcine.R
import dev.codexsoft.codexcine.core.Formatters
import dev.codexsoft.codexcine.ui.common.BaseCardPresenter
import dev.codexsoft.codexcine.ui.common.ProgressBarOverlay
import dev.codexsoft.codexcine.ui.common.WatchedOverlay
import dev.codexsoft.codexcine.ui.common.meaningfulName
import dev.codexsoft.codexcine.ui.common.runtimeText

/** Landscape 16:9 card for one episode: dimmed and checked once watched, with a bar while part-watched. */
class EpisodeCardPresenter : BaseCardPresenter(widthDp = 240, heightDp = 135) {

    /** A long press on OK, for the watched menu. */
    var onLongPress: ((EpisodeItem) -> Unit)? = null

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        val episodeItem = item as? EpisodeItem ?: return
        val episode = episodeItem.episode
        val card = viewHolder.view as ImageCardView
        val context = card.context
        val name = episode.meaningfulName()
        card.titleText = if (name == null) {
            context.getString(R.string.episode_title_untitled, episode.episodeNumber)
        } else {
            context.getString(R.string.episode_title, episode.episodeNumber, name)
        }
        card.contentText = context.subtitleOf(episodeItem)
        card.loadImage(episode.stillUrl)
        card.setImageOverlay(
            when {
                episodeItem.isWatched -> WatchedOverlay(context)
                episodeItem.progress != null -> ProgressBarOverlay(context, episodeItem.progress)
                else -> null
            },
        )
        card.setOnLongClickListener {
            onLongPress?.invoke(episodeItem)
            true
        }
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        viewHolder.view.setOnLongClickListener(null)
        super.onUnbindViewHolder(viewHolder)
    }

    private fun Context.subtitleOf(item: EpisodeItem): String = listOfNotNull(
        getString(R.string.episode_watched).takeIf { item.isWatched },
        Formatters.date(item.episode.airDate),
        runtimeText(item.episode.runtimeMinutes),
    ).joinToString(getString(R.string.separator))
}
