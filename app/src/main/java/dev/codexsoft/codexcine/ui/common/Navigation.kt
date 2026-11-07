package dev.codexsoft.codexcine.ui.common

import android.os.Bundle
import androidx.core.app.ActivityOptionsCompat
import androidx.fragment.app.Fragment
import androidx.leanback.widget.ImageCardView
import androidx.leanback.widget.Presenter
import dev.codexsoft.codexcine.domain.model.MediaItem
import dev.codexsoft.codexcine.domain.model.MediaType
import dev.codexsoft.codexcine.ui.details.DetailsActivity
import dev.codexsoft.codexcine.ui.person.PersonActivity

/** Opens a card: a person goes to their filmography, anything else to its details screen. */
fun Fragment.openMediaItem(item: MediaItem, card: Presenter.ViewHolder?) {
    val context = requireContext()
    when (item.mediaType) {
        MediaType.PERSON -> startActivity(PersonActivity.newIntent(context, item.id, item.title))
        MediaType.MOVIE, MediaType.TV -> startActivity(
            DetailsActivity.newIntent(context, item.id, item.mediaType, item.title),
            sharedPosterOptions(card),
        )
    }
}

/** Animates the card image into the details overview, as in the Leanback template. */
private fun Fragment.sharedPosterOptions(card: Presenter.ViewHolder?): Bundle? {
    val image = (card?.view as? ImageCardView)?.mainImageView ?: return null
    return ActivityOptionsCompat.makeSceneTransitionAnimation(
        requireActivity(),
        image,
        DetailsActivity.SHARED_ELEMENT_NAME,
    ).toBundle()
}
