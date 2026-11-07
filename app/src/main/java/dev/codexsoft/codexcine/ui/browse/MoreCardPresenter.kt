package dev.codexsoft.codexcine.ui.browse

import androidx.leanback.widget.ImageCardView
import dev.codexsoft.codexcine.R
import dev.codexsoft.codexcine.domain.model.CategoryType
import dev.codexsoft.codexcine.ui.common.BaseCardPresenter

/** Cards that lead somewhere rather than show a title. */
sealed class ActionCard {
    data class SeeAll(val category: CategoryType, val categoryTitle: String) : ActionCard()
    data object About : ActionCard()
}

class MoreCardPresenter : BaseCardPresenter(widthDp = 156, heightDp = 234) {
    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        val card = viewHolder.view as ImageCardView
        val context = card.context
        when (item) {
            is ActionCard.SeeAll -> {
                card.titleText = context.getString(R.string.see_all)
                card.contentText = item.categoryTitle
                card.showDrawable(R.drawable.card_see_all)
            }
            ActionCard.About -> {
                card.titleText = context.getString(R.string.row_about)
                card.contentText = context.getString(R.string.about_card_content)
                card.showDrawable(R.drawable.card_about)
            }
            else -> Unit
        }
    }
}
