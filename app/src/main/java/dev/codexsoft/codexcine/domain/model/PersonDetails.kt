package dev.codexsoft.codexcine.domain.model

data class PersonDetails(
    val id: Int,
    val name: String,
    val filmography: List<MediaItem>,
)

data class SearchResults(
    val movies: List<MediaItem>,
    val shows: List<MediaItem>,
    val people: List<MediaItem>,
) {
    val isEmpty: Boolean get() = movies.isEmpty() && shows.isEmpty() && people.isEmpty()
}
