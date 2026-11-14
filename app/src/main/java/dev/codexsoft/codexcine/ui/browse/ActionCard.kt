package dev.codexsoft.codexcine.ui.browse

import dev.codexsoft.codexcine.domain.model.CategoryType

/** Cards that lead somewhere rather than show a title. */
sealed class ActionCard {
    data class SeeAll(val category: CategoryType, val categoryTitle: String) : ActionCard()
    data object About : ActionCard()
}
