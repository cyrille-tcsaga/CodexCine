package dev.codexsoft.codexcine.ui.compose.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.codexsoft.codexcine.R
import dev.codexsoft.codexcine.core.Formatters
import dev.codexsoft.codexcine.data.progress.WatchProgress
import dev.codexsoft.codexcine.domain.model.CastMember
import dev.codexsoft.codexcine.domain.model.MediaItem
import dev.codexsoft.codexcine.domain.model.NewEpisode
import dev.codexsoft.codexcine.domain.model.Season
import dev.codexsoft.codexcine.ui.browse.ActionCard
import dev.codexsoft.codexcine.ui.common.meaningfulName
import dev.codexsoft.codexcine.ui.common.runtimeText
import dev.codexsoft.codexcine.ui.compose.theme.CodexTheme
import dev.codexsoft.codexcine.ui.season.EpisodeItem
import kotlin.math.ceil

/** Sizes (dp) of the Leanback presenters these cards replace. */
object CardSizes {
    const val POSTER_W = 156
    const val POSTER_H = 234
    const val STILL_W = 264
    const val STILL_H = 148
    const val SEASON_W = 120
    const val SEASON_H = 180
    const val CAST = 124
}

@Composable
private fun separator() = stringResource(R.string.separator)

/** Portrait poster for a film, a show or a person (`CardPresenter`). */
@Composable
fun MediaCard(item: MediaItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val rating = item.voteAverage.takeIf { it > 0 }
        ?.let { stringResource(R.string.rating_stars, Formatters.rating(it)) }
    CodexCard(
        title = item.title,
        subtitle = listOfNotNull(item.releaseYear, rating).joinToString(separator()),
        width = CardSizes.POSTER_W,
        height = CardSizes.POSTER_H,
        onClick = onClick,
        modifier = modifier,
    ) { CardImage(item.posterUrl, CardSizes.POSTER_W.dp, CardSizes.POSTER_H.dp, it) }
}

/** The title in progress, with a bar along the image (`ContinueWatchingCardPresenter`). */
@Composable
fun ContinueWatchingCard(
    progress: WatchProgress,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val minutesLeft = ceil((progress.durationSeconds - progress.positionSeconds) / 60).toInt()
    val timeLeft = context.runtimeText(minutesLeft)?.let { context.getString(R.string.card_time_left, it) }
    val episode = if (progress.episode > 0) {
        stringResource(R.string.playback_episode_subtitle, progress.season, progress.episode)
    } else {
        null
    }
    CodexCard(
        title = progress.title,
        subtitle = listOfNotNull(episode, timeLeft).joinToString(separator()),
        width = CardSizes.STILL_W,
        height = CardSizes.STILL_H,
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier,
        overlay = { ProgressBarOverlay(progress.fraction) },
    ) { CardImage(progress.backdropUrl ?: progress.posterUrl, CardSizes.STILL_W.dp, CardSizes.STILL_H.dp, it) }
}

/** A recent episode with an amber "NOUVEAU" tag (`NewEpisodeCardPresenter`). */
@Composable
fun NewEpisodeCard(item: NewEpisode, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val episode = item.episode
    CodexCard(
        title = item.show.title,
        subtitle = listOfNotNull(
            stringResource(R.string.playback_episode_subtitle, episode.seasonNumber, episode.episodeNumber),
            episode.meaningfulName(),
        ).joinToString(separator()),
        width = CardSizes.STILL_W,
        height = CardSizes.STILL_H,
        onClick = onClick,
        modifier = modifier,
        overlay = { NewTagOverlay() },
    ) {
        CardImage(
            episode.stillUrl ?: item.show.backdropUrl ?: item.show.posterUrl,
            CardSizes.STILL_W.dp, CardSizes.STILL_H.dp, it,
        )
    }
}

/** "Voir tout" and "À propos" (`MoreCardPresenter`). */
@Composable
fun MoreCard(action: ActionCard, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val (title, subtitle, icon, iconSize) = when (action) {
        is ActionCard.SeeAll -> MoreCardContent(
            stringResource(R.string.see_all), action.categoryTitle, R.drawable.ic_see_all, 64,
        )
        ActionCard.About -> MoreCardContent(
            stringResource(R.string.row_about), stringResource(R.string.about_card_content), R.drawable.ic_logo_mark, 96,
        )
    }
    CodexCard(
        title = title,
        subtitle = subtitle,
        width = CardSizes.POSTER_W,
        height = CardSizes.POSTER_H,
        onClick = onClick,
        modifier = modifier,
    ) {
        Box(it.background(CodexTheme.colors.card), contentAlignment = Alignment.Center) {
            Image(painterResource(icon), contentDescription = null, modifier = Modifier.size(iconSize.dp))
        }
    }
}

private data class MoreCardContent(val title: String, val subtitle: String, val icon: Int, val iconSize: Int)

/** A cast member: round picture, name and role centred (`CastCardPresenter`). */
@Composable
fun CastCard(member: CastMember, onClick: () -> Unit, modifier: Modifier = Modifier) {
    CodexCard(
        title = member.name,
        subtitle = member.character.orEmpty(),
        width = CardSizes.CAST,
        height = CardSizes.CAST,
        onClick = onClick,
        modifier = modifier,
        circular = true,
    ) { CardImage(member.profileUrl, CardSizes.CAST.dp, CardSizes.CAST.dp, it, errorIcon = R.drawable.ic_movie) }
}

/** A season of a show (`SeasonCardPresenter`). */
@Composable
fun SeasonCard(season: Season, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    CodexCard(
        title = season.name ?: context.getString(R.string.season_number, season.seasonNumber),
        subtitle = context.resources.getQuantityString(R.plurals.episode_count, season.episodeCount, season.episodeCount),
        width = CardSizes.SEASON_W,
        height = CardSizes.SEASON_H,
        onClick = onClick,
        modifier = modifier,
    ) { CardImage(season.posterUrl, CardSizes.SEASON_W.dp, CardSizes.SEASON_H.dp, it) }
}

/** An episode: dimmed and checked once watched, with a bar while part-watched (`EpisodeCardPresenter`). */
@Composable
fun EpisodeCard(
    item: EpisodeItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val episode = item.episode
    val name = episode.meaningfulName()
    val title = if (name == null) {
        context.getString(R.string.episode_title_untitled, episode.episodeNumber)
    } else {
        context.getString(R.string.episode_title, episode.episodeNumber, name)
    }
    val subtitle = listOfNotNull(
        context.getString(R.string.episode_watched).takeIf { item.isWatched },
        Formatters.date(episode.airDate),
        context.runtimeText(episode.runtimeMinutes),
    ).joinToString(context.getString(R.string.separator))
    CodexCard(
        title = title,
        subtitle = subtitle,
        width = CardSizes.STILL_W,
        height = CardSizes.STILL_H,
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier,
        overlay = {
            when {
                item.isWatched -> WatchedOverlay()
                item.progress != null -> ProgressBarOverlay(item.progress)
            }
        },
    ) { CardImage(episode.stillUrl, CardSizes.STILL_W.dp, CardSizes.STILL_H.dp, it) }
}
