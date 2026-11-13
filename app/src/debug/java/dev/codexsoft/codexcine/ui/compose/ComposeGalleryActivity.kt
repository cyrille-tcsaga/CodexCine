package dev.codexsoft.codexcine.ui.compose

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import dev.codexsoft.codexcine.data.progress.WatchProgress
import dev.codexsoft.codexcine.domain.model.CastMember
import dev.codexsoft.codexcine.domain.model.CategoryType
import dev.codexsoft.codexcine.domain.model.Episode
import dev.codexsoft.codexcine.domain.model.MediaItem
import dev.codexsoft.codexcine.domain.model.MediaType
import dev.codexsoft.codexcine.domain.model.NewEpisode
import dev.codexsoft.codexcine.domain.model.Season
import dev.codexsoft.codexcine.ui.browse.ActionCard
import dev.codexsoft.codexcine.ui.compose.components.CardRow
import dev.codexsoft.codexcine.ui.compose.components.CastCard
import dev.codexsoft.codexcine.ui.compose.components.ContinueWatchingCard
import dev.codexsoft.codexcine.ui.compose.components.EpisodeCard
import dev.codexsoft.codexcine.ui.compose.components.MediaCard
import dev.codexsoft.codexcine.ui.compose.components.MoreCard
import dev.codexsoft.codexcine.ui.compose.components.NewEpisodeCard
import dev.codexsoft.codexcine.ui.compose.components.SeasonCard
import dev.codexsoft.codexcine.ui.compose.theme.CodexCineTheme
import dev.codexsoft.codexcine.ui.compose.theme.CodexTheme
import dev.codexsoft.codexcine.ui.season.EpisodeItem

private const val IMG = "https://image.tmdb.org/t/p/w500"
private val poster = "$IMG/pB8BM7pdSp6B6Ih7QZ4DrQ3PmJK.jpg"
private val backdrop = "$IMG/hZkgoQYus5vegHoetLkCJzb17zJ.jpg"

private val films = listOf(
    MediaItem(1, "Fight Club", poster, backdrop, "1999", 8.4, MediaType.MOVIE),
    MediaItem(2, "Sans affiche, avec un très long titre qui déborde", null, null, "2024", 0.0, MediaType.MOVIE),
    MediaItem(3, "Film trois", poster, backdrop, "2001", 7.1, MediaType.MOVIE),
    MediaItem(4, "Film quatre", poster, backdrop, "2010", 6.5, MediaType.MOVIE),
    MediaItem(5, "Film cinq", poster, backdrop, "2015", 7.9, MediaType.MOVIE),
    MediaItem(6, "Film six", poster, backdrop, "2020", 8.0, MediaType.MOVIE),
    MediaItem(7, "Film sept", poster, backdrop, "2022", 5.5, MediaType.MOVIE),
    MediaItem(8, "Film huit", poster, backdrop, "2023", 6.2, MediaType.MOVIE),
)
private val progressItems = listOf(
    WatchProgress("movie", 1, 0, 0, 2400.0, 6000.0, 1L, "Fight Club", poster, backdrop),
    WatchProgress("tv", 2, 1, 4, 600.0, 3000.0, 2L, "Une série", null, backdrop),
)
private val episode = Episode(1, 3, 2, "Un épisode", null, backdrop, "2026-10-01", 52)
private val episodes = listOf(
    EpisodeItem(episode, isWatched = true),
    EpisodeItem(episode.copy(id = 2, episodeNumber = 4, name = "Épisode 4"), isWatched = false, progress = 0.4f),
    EpisodeItem(episode.copy(id = 3, episodeNumber = 5), isWatched = false),
)

/** Debug only. Shows the theme and the shared components; the last click or long click is printed on top. */
class ComposeGalleryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { CodexCineTheme { Gallery() } }
    }
}

@Composable
private fun Gallery() {
    var last by remember { mutableStateOf("(rien)") }
    Column(Modifier.fillMaxSize().background(CodexTheme.colors.night).padding(top = 12.dp)) {
    Text("Dernier événement : $last", modifier = Modifier.padding(start = 58.dp, bottom = 8.dp))
    LazyColumn {
        item {
            CardRow("MediaCard", films, key = { it.id }) { f ->
                MediaCard(f, onClick = { last = "clic ${f.title}" })
            }
        }
        item {
            CardRow("ContinueWatchingCard (appui long)", progressItems, key = { it.key }) { p ->
                ContinueWatchingCard(p, onClick = { last = "clic ${p.title}" }, onLongClick = { last = "LONG ${p.title}" })
            }
        }
        item {
            CardRow("NewEpisodeCard", listOf(NewEpisode(films[0], episode, true)), key = { "n" }) { n ->
                NewEpisodeCard(n, onClick = { last = "clic nouveau" })
            }
        }
        item {
            CardRow(
                "MoreCard",
                listOf<ActionCard>(ActionCard.SeeAll(CategoryType.POPULAR_MOVIES, "Films populaires"), ActionCard.About),
                key = { it.toString() },
            ) { a -> MoreCard(a, onClick = { last = "clic $a" }) }
        }
        item {
            CardRow("CastCard", listOf(CastMember(1, "Brad Pitt", "Tyler", null), CastMember(2, "Edward Norton", null, null)), key = { it.id }) { m ->
                CastCard(m, onClick = { last = "clic ${m.name}" })
            }
        }
        item {
            CardRow("SeasonCard", listOf(Season(1, 1, null, 10, poster), Season(2, 2, "Spéciaux", 1, null)), key = { it.id }) { s ->
                SeasonCard(s, onClick = { last = "clic saison ${s.seasonNumber}" })
            }
        }
        item {
            CardRow("EpisodeCard (vu, en cours, neuf)", episodes, key = { it.episode.id }) { e ->
                EpisodeCard(e, onClick = { last = "clic E${e.episode.episodeNumber}" }, onLongClick = { last = "LONG E${e.episode.episodeNumber}" })
            }
        }
    }
    }
}
