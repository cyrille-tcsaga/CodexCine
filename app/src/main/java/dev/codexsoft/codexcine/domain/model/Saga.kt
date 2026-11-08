package dev.codexsoft.codexcine.domain.model

/** A saga as TMDB groups it, with each film's release date (YYYY-MM-DD, or null when unknown). */
data class Saga(
    val name: String,
    val parts: List<Pair<MediaItem, String?>>,
)

/** What the details screen shows of a saga: the other films in release order, and where this one sits. */
data class SagaRow(
    val name: String,
    val others: List<MediaItem>,
    /** 1-based; null when the film is not among the parts TMDB lists. */
    val position: Int?,
    val total: Int,
)

object SagaRules {

    /** In release order, undated films (usually announced ones) last. Null when there is nothing else to show. */
    fun rowFor(saga: Saga, currentId: Int): SagaRow? {
        val ordered = saga.parts
            .distinctBy { (item, _) -> item.id }
            .sortedWith(compareBy<Pair<MediaItem, String?>> { (_, date) -> date.isNullOrBlank() }.thenBy { (_, date) -> date })
            .map { (item, _) -> item }
        val others = ordered.filterNot { it.id == currentId }
        if (others.isEmpty()) return null
        val index = ordered.indexOfFirst { it.id == currentId }
        return SagaRow(
            name = saga.name,
            others = others,
            position = (index + 1).takeIf { index >= 0 },
            total = ordered.size,
        )
    }
}
