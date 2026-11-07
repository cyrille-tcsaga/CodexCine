package dev.codexsoft.codexcine.ui.common

import androidx.fragment.app.Fragment
import dev.codexsoft.codexcine.domain.model.MediaItem
import dev.codexsoft.codexcine.domain.model.MediaType
import dev.codexsoft.codexcine.ui.details.DetailsActivity
import dev.codexsoft.codexcine.ui.person.PersonActivity

/** Opens a card: a person goes to their filmography, anything else to its details screen. */
fun Fragment.openMediaItem(item: MediaItem) {
    val context = requireContext()
    when (item.mediaType) {
        MediaType.PERSON -> startActivity(PersonActivity.newIntent(context, item.id, item.title))
        MediaType.MOVIE, MediaType.TV ->
            startActivity(DetailsActivity.newIntent(context, item.id, item.mediaType, item.title))
    }
}
