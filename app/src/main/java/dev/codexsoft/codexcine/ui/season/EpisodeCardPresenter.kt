package dev.codexsoft.codexcine.ui.season

import android.content.Context
import androidx.leanback.widget.ImageCardView
import dev.codexsoft.codexcine.R
import dev.codexsoft.codexcine.core.Formatters
import dev.codexsoft.codexcine.domain.model.Episode
import dev.codexsoft.codexcine.ui.common.BaseCardPresenter
import dev.codexsoft.codexcine.ui.common.runtimeText

/** Landscape 16:9 card for one episode. */
class EpisodeCardPresenter : BaseCardPresenter(widthDp = 240, heightDp = 135) {

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        val episode = item as? Episode ?: return
        val card = viewHolder.view as ImageCardView
        val context = card.context
        card.titleText = if (episode.name.isBlank()) {
            context.getString(R.string.episode_title_untitled, episode.episodeNumber)
        } else {
            context.getString(R.string.episode_title, episode.episodeNumber, episode.name)
        }
        card.contentText = context.subtitleOf(episode)
        card.loadImage(episode.stillUrl)
    }

    private fun Context.subtitleOf(episode: Episode): String =
        listOfNotNull(Formatters.date(episode.airDate), runtimeText(episode.runtimeMinutes))
            .joinToString(getString(R.string.separator))
}
