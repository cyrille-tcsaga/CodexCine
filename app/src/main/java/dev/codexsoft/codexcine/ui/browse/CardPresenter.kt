package dev.codexsoft.codexcine.ui.browse

import android.content.Context
import androidx.leanback.widget.ImageCardView
import dev.codexsoft.codexcine.R
import dev.codexsoft.codexcine.core.Formatters
import dev.codexsoft.codexcine.domain.model.MediaItem
import dev.codexsoft.codexcine.ui.common.BaseCardPresenter

/** Portrait 2:3 poster card for movies, shows and people. */
class CardPresenter : BaseCardPresenter(widthDp = 156, heightDp = 234) {

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        val media = item as? MediaItem ?: return
        val card = viewHolder.view as ImageCardView
        card.titleText = media.title
        card.contentText = card.context.subtitleOf(media)
        card.loadImage(media.posterUrl)
    }

    private fun Context.subtitleOf(media: MediaItem): String {
        val rating = media.voteAverage.takeIf { it > 0 }
            ?.let { getString(R.string.rating_stars, Formatters.rating(it)) }
        return listOfNotNull(media.releaseYear, rating).joinToString(getString(R.string.separator))
    }
}
