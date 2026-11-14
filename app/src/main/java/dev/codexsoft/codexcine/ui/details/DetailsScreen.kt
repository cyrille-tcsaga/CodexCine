package dev.codexsoft.codexcine.ui.details

import androidx.compose.foundation.background
import dev.codexsoft.codexcine.ui.common.appContainer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.ViewModelProvider
import androidx.fragment.app.Fragment
import androidx.core.net.toUri
import android.widget.Toast
import android.content.Intent
import android.content.ActivityNotFoundException
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.codexsoft.codexcine.R
import dev.codexsoft.codexcine.core.ErrorKind
import dev.codexsoft.codexcine.core.Extras
import dev.codexsoft.codexcine.core.Formatters
import dev.codexsoft.codexcine.domain.model.CastMember
import dev.codexsoft.codexcine.domain.model.MediaDetails
import dev.codexsoft.codexcine.domain.model.MediaType
import dev.codexsoft.codexcine.domain.model.SagaRow
import dev.codexsoft.codexcine.domain.model.Season
import dev.codexsoft.codexcine.ui.common.openMediaItem
import dev.codexsoft.codexcine.ui.common.showErrorScreen
import dev.codexsoft.codexcine.ui.compose.ComposeScreenFragment
import dev.codexsoft.codexcine.ui.compose.components.BackdropScrim
import dev.codexsoft.codexcine.ui.compose.components.CardImage
import dev.codexsoft.codexcine.ui.compose.components.CardRow
import dev.codexsoft.codexcine.ui.compose.components.CardSizes
import dev.codexsoft.codexcine.ui.compose.components.CastCard
import dev.codexsoft.codexcine.ui.compose.components.LoadingIndicator
import dev.codexsoft.codexcine.ui.compose.components.MediaCard
import dev.codexsoft.codexcine.ui.compose.components.PillButton
import dev.codexsoft.codexcine.ui.compose.components.SeasonCard
import dev.codexsoft.codexcine.ui.compose.components.requestFocusWhenReady
import dev.codexsoft.codexcine.ui.compose.components.runtimeText
import dev.codexsoft.codexcine.ui.compose.theme.CodexTheme
import dev.codexsoft.codexcine.ui.person.PersonActivity
import dev.codexsoft.codexcine.ui.playback.PlaybackActivity
import dev.codexsoft.codexcine.ui.season.SeasonActivity
import kotlinx.coroutines.launch

/**
 * A title's page, in Compose: its artwork fills the screen under a scrim, with the facts and the rounded
 * buttons over it and the saga, seasons, cast and recommendations in rows underneath. The hero exists
 * before the network answers: Play only needs the intent extras, and keeps the focus when the data arrives.
 */
class ComposeDetailsFragment : ComposeScreenFragment() {

    private val viewModel: DetailsViewModel by viewModels { detailsViewModelFactory() }

    private val mediaId get() = requireActivity().intent.getIntExtra(Extras.MEDIA_ID, Extras.NO_VALUE)
    private val mediaType get() = MediaType.fromValue(requireActivity().intent.getStringExtra(Extras.MEDIA_TYPE))
    private val initialTitle get() = requireActivity().intent.getStringExtra(Extras.TITLE).orEmpty()

    @Composable
    override fun ScreenContent() {
        val type = mediaType
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        val availability by viewModel.playAvailability.collectAsStateWithLifecycle()
        val saga by viewModel.saga.collectAsStateWithLifecycle()

        LaunchedEffect(state, type) {
            if (type == null) showErrorScreen(ErrorKind.GENERIC)
            (state as? DetailsUiState.Error)?.let { showErrorScreen(it.kind) }
        }

        val content = state as? DetailsUiState.Content
        val details = content?.details
        DetailsBackground(details?.backdropUrl)
        Box(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize()) {
                item(key = "hero") {
                    Hero(
                        initialTitle = initialTitle,
                        type = details?.mediaType ?: type,
                        details = details,
                        isInMyList = content?.isInMyList,
                        availability = availability,
                        onPlay = ::play,
                        onTrailer = { details?.trailerKey?.let(::openTrailerOf) },
                        onToggleMyList = viewModel::toggleMyList,
                    )
                }
                if (details != null) {
                    saga?.let { sagaRow -> item(key = "saga") { SagaRowView(sagaRow) } }
                    if (details.seasons.isNotEmpty()) {
                        item(key = "seasons") {
                            CardRow(stringResource(R.string.row_seasons), details.seasons, key = { it.id }) { season ->
                                SeasonCard(season, onClick = { openSeason(season, details) })
                            }
                        }
                    }
                    if (details.cast.isNotEmpty()) {
                        item(key = "cast") {
                            CardRow(stringResource(R.string.row_cast), details.cast, key = { it.id }) { member ->
                                CastCard(member, onClick = { openPerson(member) })
                            }
                        }
                    }
                    if (details.recommendations.isNotEmpty()) {
                        item(key = "recommendations") {
                            CardRow(
                                stringResource(R.string.row_recommendations),
                                details.recommendations,
                                key = { "${it.mediaType.value}:${it.id}" },
                            ) { media -> MediaCard(media, onClick = { openMediaItem(media) }) }
                        }
                    }
                }
            }
            // In the screen's own container, never in the lists: a view added to one crashed the layout.
            if (state is DetailsUiState.Loading) LoadingIndicator(Modifier.align(Alignment.Center))
        }
    }

    @Composable
    private fun SagaRowView(saga: SagaRow) {
        val title = saga.position?.let { stringResource(R.string.row_saga_position, saga.name, it, saga.total) }
            ?: saga.name
        CardRow(title, saga.others, key = { "${it.mediaType.value}:${it.id}" }) { media ->
            MediaCard(media, onClick = { openMediaItem(media) })
        }
    }

    private fun play() {
        val type = mediaType ?: return
        val title = (viewModel.uiState.value as? DetailsUiState.Content)?.details?.title ?: initialTitle
        viewLifecycleOwner.lifecycleScope.launch {
            // A show has no single video: Play starts where the viewer left off.
            val episode = if (type == MediaType.TV) viewModel.episodeToStart() else null
            startActivity(
                PlaybackActivity.newIntent(
                    requireContext(),
                    mediaId = mediaId,
                    mediaType = type,
                    title = title,
                    seasonNumber = episode?.first,
                    episodeNumber = episode?.second,
                ),
            )
        }
    }

    private fun openPerson(member: CastMember) {
        startActivity(PersonActivity.newIntent(requireContext(), member.id, member.name))
    }

    private fun openSeason(season: Season, details: MediaDetails) {
        startActivity(SeasonActivity.newIntent(requireContext(), mediaId, season.seasonNumber, details.title))
    }
}

/** The artwork over the whole screen, then the scrim (`backdrop_scrim`) that keeps the text readable. */
@Composable
private fun DetailsBackground(backdropUrl: String?) {
    val colors = CodexTheme.colors
    BoxWithConstraints(Modifier.fillMaxSize().background(colors.night)) {
        CardImage(
            url = backdropUrl,
            width = maxWidth,
            height = maxHeight,
            modifier = Modifier.fillMaxSize(),
            errorIcon = null,
            placeholder = colors.night,
        )
        BackdropScrim()
    }
}

@Composable
private fun Hero(
    initialTitle: String,
    type: MediaType?,
    details: MediaDetails?,
    isInMyList: Boolean?,
    availability: PlayAvailability,
    onPlay: () -> Unit,
    onTrailer: () -> Unit,
    onToggleMyList: () -> Unit,
) {
    val colors = CodexTheme.colors
    val dimens = CodexTheme.dimens
    val playFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { playFocus.requestFocusWhenReady() }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = dimens.detailsHeroHeight)
            .padding(start = dimens.heroPaddingStart, end = dimens.heroPaddingEnd, bottom = 28.dp),
        verticalArrangement = Arrangement.Bottom,
    ) {
        if (type == MediaType.MOVIE || type == MediaType.TV) {
            Text(
                text = stringResource(if (type == MediaType.TV) R.string.details_label_tv else R.string.details_label_movie)
                    .uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = colors.amber,
            )
        }
        val title = details?.title ?: initialTitle
        Text(
            text = title,
            style = MaterialTheme.typography.displayLarge,
            color = colors.ivory,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(dimens.textColumnWidth).padding(top = 6.dp),
        )
        Meta(details, availability)
        if (!details?.genres.isNullOrEmpty()) {
            Text(
                text = details!!.genres.joinToString(stringResource(R.string.separator)),
                style = MaterialTheme.typography.bodyLarge.merge(TextStyle(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)),
                color = colors.mist,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(dimens.textColumnWidth).padding(top = 10.dp),
            )
        }
        if (details != null) {
            Text(
                text = details.overview ?: stringResource(R.string.details_no_overview),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.ivory,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(dimens.textColumnWidth).padding(top = 14.dp),
            )
        }
        Row(Modifier.padding(top = 24.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            PillButton(
                text = stringResource(R.string.action_play),
                onClick = onPlay,
                primary = true,
                icon = R.drawable.ic_player_play,
                modifier = Modifier.focusRequester(playFocus),
                description = "${stringResource(R.string.action_play)} $title",
            )
            if (details?.trailerKey != null) {
                PillButton(stringResource(R.string.action_trailer), onClick = onTrailer, icon = R.drawable.ic_trailer)
            }
            if (isInMyList != null) {
                PillButton(
                    text = stringResource(R.string.action_my_list),
                    onClick = onToggleMyList,
                    icon = if (isInMyList) R.drawable.ic_check else R.drawable.ic_add,
                    description = stringResource(if (isInMyList) R.string.action_remove_my_list else R.string.action_add_my_list),
                )
            }
        }
    }
}

/** Year · length [age rating] ★ rating [VF] [VOSTFR]: plain facts, then outlined tags. */
@Composable
private fun Meta(details: MediaDetails?, availability: PlayAvailability) {
    val colors = CodexTheme.colors
    val separator = stringResource(R.string.separator)
    val length = when {
        details == null -> null
        details.mediaType == MediaType.TV -> details.seasonCount?.takeIf { it > 0 }?.let {
            androidx.compose.ui.res.pluralStringResource(R.plurals.season_count, it, it)
        }
        else -> runtimeText(details.runtimeMinutes)
    }
    val facts = listOfNotNull(details?.releaseYear, length).joinToString(separator)
    val hasMeta = facts.isNotEmpty() || details?.certification?.isNotBlank() == true ||
        (details?.voteAverage ?: 0.0) > 0 || availability != PlayAvailability.Unknown
    if (!hasMeta) return
    Row(
        modifier = Modifier.padding(top = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (facts.isNotEmpty()) Text(facts, style = MaterialTheme.typography.bodyLarge, color = colors.ivory)
        details?.certification?.takeIf { it.isNotBlank() }?.let { Badge(it) }
        details?.voteAverage?.takeIf { it > 0 }?.let { rating ->
            val text = stringResource(R.string.rating_stars, Formatters.rating(rating))
            Text(
                text = buildAnnotatedString {
                    val star = text.indexOf('★')
                    append(text)
                    if (star >= 0) addStyle(SpanStyle(color = colors.amber), star, star + 1)
                },
                style = MaterialTheme.typography.bodyLarge,
                color = colors.ivory,
            )
        }
        when (availability) {
            PlayAvailability.Unknown -> Unit
            PlayAvailability.Unavailable -> Badge(stringResource(R.string.action_play_unavailable), warn = true)
            is PlayAvailability.Available -> availability.languages.forEach { Badge(it.uppercase()) }
        }
    }
}

/** An outlined tag (`bg_badge`): the age rating and the audio versions. */
@Composable
private fun Badge(text: String, warn: Boolean = false) {
    val colors = CodexTheme.colors
    val shape = RoundedCornerShape(6.dp)
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.merge(TextStyle(fontWeight = androidx.compose.ui.text.font.FontWeight.Normal)),
        color = if (warn) colors.amber else colors.ivory,
        modifier = Modifier.border(1.5.dp, colors.badgeStroke, shape).padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

/** The details screen's view model, for the Leanback and the Compose fragments alike. */
internal fun Fragment.detailsViewModelFactory(): ViewModelProvider.Factory = viewModelFactory {
    initializer {
        val container = appContainer
        val intent = requireActivity().intent
        DetailsViewModel(
            mediaId = intent.getIntExtra(Extras.MEDIA_ID, Extras.NO_VALUE),
            mediaType = MediaType.fromValue(intent.getStringExtra(Extras.MEDIA_TYPE)) ?: MediaType.MOVIE,
            catalog = container.catalogRepository,
            myList = container.myListRepository,
            vidzy = container.vidzyRepository,
            watchProgress = container.watchProgressRepository,
        )
    }
}

/** Opens a trailer in the YouTube app, else in a browser, else says that nothing can play it. */
internal fun Fragment.openTrailerOf(key: String) {
    val inApp = Intent(Intent.ACTION_VIEW, "vnd.youtube:$key".toUri())
    val inBrowser = Intent(Intent.ACTION_VIEW, "https://www.youtube.com/watch?v=$key".toUri())
    if (!tryStart(inApp) && !tryStart(inBrowser)) {
        Toast.makeText(requireContext(), R.string.no_trailer_app, Toast.LENGTH_LONG).show()
    }
}

private fun Fragment.tryStart(intent: Intent): Boolean = try {
    startActivity(intent)
    true
} catch (_: ActivityNotFoundException) {
    false
}

