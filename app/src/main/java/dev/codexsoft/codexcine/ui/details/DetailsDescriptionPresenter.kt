package dev.codexsoft.codexcine.ui.details

import android.content.Context
import androidx.leanback.widget.AbstractDetailsDescriptionPresenter
import dev.codexsoft.codexcine.R
import dev.codexsoft.codexcine.core.Formatters
import dev.codexsoft.codexcine.domain.model.MediaDetails
import dev.codexsoft.codexcine.domain.model.MediaType
import dev.codexsoft.codexcine.ui.common.runtimeText

/** Binds a [MediaDetails], or just the title (a String) while the details are loading. */
class DetailsDescriptionPresenter : AbstractDetailsDescriptionPresenter() {

    override fun onBindDescription(vh: ViewHolder, item: Any) {
        val context = vh.view.context
        when (item) {
            is String -> vh.title.text = item
            is MediaDetails -> {
                vh.title.text = item.title
                vh.subtitle.text = context.subtitleOf(item)
                vh.body.text = item.overview ?: context.getString(R.string.details_no_overview)
            }
        }
    }

    private fun Context.subtitleOf(details: MediaDetails): String {
        val length = when (details.mediaType) {
            MediaType.TV -> details.seasonCount?.takeIf { it > 0 }?.let {
                resources.getQuantityString(R.plurals.season_count, it, it)
            }
            else -> runtimeText(details.runtimeMinutes)
        }
        val rating = details.voteAverage.takeIf { it > 0 }?.let {
            resources.getQuantityString(
                R.plurals.rating_with_votes,
                details.voteCount,
                Formatters.rating(it),
                Formatters.count(details.voteCount),
            )
        }
        return listOfNotNull(
            details.releaseYear,
            length,
            details.certification,
            details.genres.joinToString(", ").takeIf { it.isNotEmpty() },
            rating,
        ).joinToString(getString(R.string.separator))
    }
}
