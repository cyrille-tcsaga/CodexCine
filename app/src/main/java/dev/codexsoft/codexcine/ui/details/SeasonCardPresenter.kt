package dev.codexsoft.codexcine.ui.details

import androidx.leanback.widget.ImageCardView
import dev.codexsoft.codexcine.R
import dev.codexsoft.codexcine.domain.model.Season
import dev.codexsoft.codexcine.ui.common.BaseCardPresenter

class SeasonCardPresenter : BaseCardPresenter(widthDp = 120, heightDp = 180) {

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        val season = item as? Season ?: return
        val card = viewHolder.view as ImageCardView
        val resources = card.context.resources
        card.titleText = season.name ?: resources.getString(R.string.season_number, season.seasonNumber)
        card.contentText = resources.getQuantityString(
            R.plurals.episode_count,
            season.episodeCount,
            season.episodeCount,
        )
        card.loadImage(season.posterUrl)
    }
}
