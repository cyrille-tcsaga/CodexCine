package dev.codexsoft.codexcine.ui.details

import androidx.leanback.widget.ImageCardView
import dev.codexsoft.codexcine.domain.model.CastMember
import dev.codexsoft.codexcine.ui.common.BaseCardPresenter

class CastCardPresenter : BaseCardPresenter(widthDp = 124, heightDp = 124, circular = true) {

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        val member = item as? CastMember ?: return
        val card = viewHolder.view as ImageCardView
        card.titleText = member.name
        card.contentText = member.character.orEmpty()
        card.loadImage(member.profileUrl)
    }
}
