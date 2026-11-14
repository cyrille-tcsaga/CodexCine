package dev.codexsoft.codexcine.ui.browse

import android.content.Intent
import androidx.activity.compose.BackHandler
import dev.codexsoft.codexcine.ui.common.appContainer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.ViewModelProvider
import androidx.fragment.app.Fragment
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.rememberDrawerState
import androidx.tv.material3.NavigationDrawerScope
import androidx.tv.material3.NavigationDrawerItemDefaults
import androidx.tv.material3.NavigationDrawerItem
import androidx.tv.material3.ModalNavigationDrawer
import androidx.tv.material3.Icon
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.DrawerValue
import androidx.tv.material3.Text
import dev.codexsoft.codexcine.R
import dev.codexsoft.codexcine.data.progress.WatchProgress
import dev.codexsoft.codexcine.domain.model.CategoryType
import dev.codexsoft.codexcine.domain.model.HomeRow
import dev.codexsoft.codexcine.domain.model.MediaItem
import dev.codexsoft.codexcine.domain.model.NewEpisode
import dev.codexsoft.codexcine.ui.common.openMediaItem
import dev.codexsoft.codexcine.ui.common.showErrorScreen
import dev.codexsoft.codexcine.ui.common.titleRes
import dev.codexsoft.codexcine.ui.compose.components.CardRow
import dev.codexsoft.codexcine.ui.compose.components.ContinueWatchingCard
import dev.codexsoft.codexcine.ui.compose.components.LocalLeftEdge
import dev.codexsoft.codexcine.ui.compose.components.MediaCard
import dev.codexsoft.codexcine.ui.compose.components.MoreCard
import dev.codexsoft.codexcine.ui.compose.components.NewEpisodeCard
import dev.codexsoft.codexcine.ui.compose.components.requestFocusWhenReady
import dev.codexsoft.codexcine.ui.compose.ComposeScreenFragment
import dev.codexsoft.codexcine.ui.compose.components.LoadingIndicator
import dev.codexsoft.codexcine.ui.compose.components.dpadClickable
import dev.codexsoft.codexcine.ui.compose.theme.CodexTheme
import dev.codexsoft.codexcine.ui.search.SearchActivity

/** False while the sidebar is open: a page must not take the focus from it. */
private val LocalPageFocusEnabled = androidx.compose.runtime.compositionLocalOf { true }

private val SidebarWidth = 292.dp
private val SidebarPadding = 56.dp

/**
 * The home screen in Compose: a sidebar whose entries open pages, the search button and the logo on top.
 * The catalogue is loaded once, by the same [MainViewModel] the Leanback home uses.
 *
 * The sidebar is hidden while the viewer browses a page. Left from the first card (or Back) opens it,
 * Right (or choosing an entry) closes it, and the entry under the focus is the page shown, as in Leanback.
 */
class ComposeMainFragment : ComposeScreenFragment() {

    private val viewModel: MainViewModel by viewModels { mainViewModelFactory() }

    /** Where a card was long-pressed, and where the focus must go once it has been removed. */
    private var lastLongPress: RowPosition? = null
    private var refocus by mutableStateOf<RowPosition?>(null)

    private val continueWatchingMenu = ContinueWatchingMenu(this) {
        refocus = lastLongPress
        viewModel.removeFromContinueWatching(it)
    }

    override fun onDestroyView() {
        continueWatchingMenu.dismiss()
        super.onDestroyView()
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshLocalRows()
    }

    @Composable
    override fun ScreenContent() {
        val state by viewModel.uiState.collectAsStateWithLifecycle()

        LaunchedEffect(state) {
            when (val current = state) {
                MainUiState.Loading -> Unit
                is MainUiState.Content -> (activity as? MainActivity)?.onHomeReady()
                is MainUiState.Error -> {
                    (activity as? MainActivity)?.onHomeReady()
                    showErrorScreen(current.kind)
                }
            }
        }

        var backdrop by remember { mutableStateOf<String?>(null) }
        Box(Modifier.fillMaxSize().background(CodexTheme.colors.night)) {
            HomeBackdrop(requestedUrl = backdrop)
            when (state) {
                MainUiState.Loading -> LoadingIndicator(Modifier.align(Alignment.Center))
                is MainUiState.Content -> androidx.compose.runtime.CompositionLocalProvider(
                    LocalBackdrop provides { url -> if (url != null) backdrop = url },
                ) {
                    HomeShell(
                        openSearch = { startActivity(Intent(requireContext(), SearchActivity::class.java)) },
                    ) { page, onAtTop -> HomePageContent(page, onAtTop) }
                }
                is MainUiState.Error -> Unit
            }
        }
    }

    /**
     * What a page shows: its rows, in the order of [HomePage.categories]. Rows and cards are keyed, so that a row
     * arriving late (the catalogue loads row by row), a title leaving "Ma liste" or a card removed from
     * "Continuer à regarder" never moves the focus.
     */
    @Composable
    private fun HomePageContent(page: HomePage, onAtTop: (Boolean) -> Unit) {
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        val allRows = (state as? MainUiState.Content)?.rows.orEmpty()
        val byCategory = allRows.associateBy { it.category }
        val rows = page.categories.mapNotNull { byCategory[it] }
        val listState = rememberLazyListState()
        val atTop by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0 } }
        LaunchedEffect(atTop) { onAtTop(atTop) }

        // Leanback selects the first card by itself; here the page asks once, as soon as it has one.
        val firstFocus = remember(page) { FocusRequester() }
        val focusEnabled = LocalPageFocusEnabled.current
        var focused by remember(page) { mutableStateOf(false) }
        LaunchedEffect(rows.isNotEmpty(), focused, focusEnabled) {
            if (rows.isEmpty() || focused || !focusEnabled) return@LaunchedEffect
            if (firstFocus.requestFocusWhenReady()) focused = true
        }

        // Picked once, from the first row that can feed it, and kept: rows arriving later never change it.
        var billboardItems by remember(page) { mutableStateOf<List<MediaItem>?>(null) }
        if (page.hasBillboard && billboardItems == null) {
            val source = byCategory[CategoryType.TRENDING]
                ?: byCategory[CategoryType.POPULAR_MOVIES]
                ?: allRows.firstOrNull { it.category.isRemote }
            val picked = source?.items.orEmpty()
                .filter { it.backdropUrl != null && !it.overview.isNullOrBlank() }
                .take(BILLBOARD_SIZE)
            if (picked.isNotEmpty()) billboardItems = picked
        }
        val setBackdrop = LocalBackdrop.current
        var billboardSelected by remember(page) { mutableStateOf(true) }

        if (rows.isEmpty() && billboardItems == null) {
            page.emptyMessageRes?.let { message ->
                Text(
                    text = stringResource(message),
                    style = MaterialTheme.typography.titleLarge,
                    color = CodexTheme.colors.ivory,
                    modifier = Modifier.padding(start = CodexTheme.dimens.heroPaddingStart, end = 48.dp, top = HomeRowsTop),
                )
            }
            return
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().focusRequester(firstFocus).focusRestorer(),
            contentPadding = PaddingValues(top = if (page.hasBillboard) 0.dp else HomeRowsTop, bottom = 48.dp),
        ) {
            billboardItems?.let { featured ->
                item(key = "billboard") {
                    Billboard(
                        items = featured,
                        onPlay = ::playFeatured,
                        onMoreInfo = ::openDetails,
                        onFeatured = { item -> if (billboardSelected) setBackdrop(item.backdropUrl) },
                        modifier = Modifier
                            .fillParentMaxHeight(BILLBOARD_HEIGHT_FRACTION)
                            .onFocusChanged {
                                if (it.hasFocus) {
                                    billboardSelected = true
                                    setBackdrop(featured.first().backdropUrl)
                                }
                            },
                    )
                }
            }
            items(rows, key = { it.category.ordinal }) { row ->
                val title = stringResource(row.category.titleRes)
                val cards = remember(row) { cardsOf(row, title) }
                val position = refocus?.takeIf { it.row == row.category.ordinal }
                CardRow(
                    title = title,
                    items = cards,
                    key = ::cardKey,
                    refocusIndex = position?.index,
                    refocusFromSize = position?.size ?: 0,
                    onRefocused = { refocus = null },
                ) { card ->
                    Box(
                        Modifier.onFocusChanged {
                            if (it.hasFocus) {
                                billboardSelected = false
                                backdropOf(card)?.let(setBackdrop)
                            }
                        },
                    ) { HomeCard(card, onLongPress = { lastLongPress = RowPosition(row.category.ordinal, cards.indexOf(card), cards.size) }) }
                }
            }
        }
    }

    @Composable
    private fun HomeCard(card: Any, onLongPress: () -> Unit) {
        when (card) {
            is MediaItem -> MediaCard(card, onClick = { openMediaItem(card) })
            is WatchProgress -> ContinueWatchingCard(
                card,
                onClick = { playProgress(card, autoResume = true) },
                onLongClick = {
                    onLongPress()
                    continueWatchingMenu.show(card)
                },
            )
            is NewEpisode -> NewEpisodeCard(card, onClick = { openNewEpisode(card) })
            is ActionCard.SeeAll -> MoreCard(card, onClick = { openSeeAll(card) })
            ActionCard.About -> MoreCard(ActionCard.About, onClick = { openAbout() })
        }
    }
}

/** Under the search button and the logo, on the pages that have no billboard. */
private val HomeRowsTop = 132.dp

/** The cards of a row: what is in progress, the new episodes, the titles, then "Voir tout" or "À propos". */
private fun cardsOf(row: HomeRow, title: String): List<Any> = buildList {
    addAll(row.continueWatching)
    addAll(row.newEpisodes)
    addAll(row.items)
    when {
        row.category == CategoryType.ABOUT -> add(ActionCard.About)
        row.category.isRemote -> add(ActionCard.SeeAll(row.category, title))
    }
}

/** A key a Bundle can store: lazy lists save their items' state under it. */
private fun backdropOf(card: Any): String? = when (card) {
    is MediaItem -> card.backdropUrl
    is WatchProgress -> card.backdropUrl
    is NewEpisode -> card.show.backdropUrl
    else -> null
}

private const val BILLBOARD_SIZE = 6

/** A card's place on the page: its row (the category's ordinal), its position in it and the row's size. */
private data class RowPosition(val row: Int, val index: Int, val size: Int)

/** The billboard leaves just the next row's title showing at the bottom, as a hint to scroll. */
private const val BILLBOARD_HEIGHT_FRACTION = 0.95f

private fun cardKey(card: Any): String = when (card) {
    is MediaItem -> "m:${card.mediaType.value}:${card.id}"
    is WatchProgress -> "w:${card.key}"
    is NewEpisode -> "n:${card.show.id}:${card.episode.seasonNumber}:${card.episode.episodeNumber}"
    is ActionCard.SeeAll -> "all"
    else -> "about"
}

/** Width of the collapsed navigation rail: the page starts after it. */
private val RailWidth = 80.dp

/**
 * The home's navigation, as in the Android TV guide for the modal navigation drawer: a rail of icons on the
 * left that expands over the page, on a gradient scrim, with the logo on top and the destinations below.
 * "Recherche" is its first item; the others are the pages. As in the guide, a modal drawer changes the
 * page when an item is chosen, not when the focus passes over it.
 *
 * Moving the focus into the rail (Left from the first card, or Back) expands it; moving out of it, or
 * choosing an item, collapses it and gives the focus back to the page.
 */
@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
private fun HomeShell(openSearch: () -> Unit, page: @Composable (HomePage, (Boolean) -> Unit) -> Unit) {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val isOpen = drawerState.currentValue == DrawerValue.Open
    val pageFocus = remember { FocusRequester() }
    // One requester per destination: the search item is the first, then the pages in order.
    val itemFocus = remember { List(HomePage.entries.size + 1) { FocusRequester() } }
    val colors = CodexTheme.colors
    // The drawer opens when the focus enters it, on the item nearest to where the focus was: send it to the item
    // of the page that is shown instead.
    LaunchedEffect(isOpen) {
        if (isOpen) runCatching { itemFocus[selected + 1].requestFocus() }
    }

    var pageFocusRequests by remember { mutableIntStateOf(0) }
    LaunchedEffect(pageFocusRequests) {
        if (pageFocusRequests > 0) pageFocus.requestFocusWhenReady()
    }

    // Back from a page opens the drawer, a second Back leaves the app (Leanback did the same).
    BackHandler(enabled = !isOpen) { runCatching { itemFocus[selected + 1].requestFocus() } }

    ModalNavigationDrawer(
        drawerState = drawerState,
        scrimBrush = Brush.horizontalGradient(
            0f to colors.night.copy(alpha = 0.96f),
            0.55f to colors.night.copy(alpha = 0.7f),
            1f to Color.Transparent,
        ),
        drawerContent = { drawerValue ->
            Column(
                Modifier
                    .fillMaxHeight()
                    .background(
                        Brush.horizontalGradient(
                            listOf(colorResource(R.color.fastlane_background), colors.night.copy(alpha = 0.55f)),
                        ),
                    )
                    .padding(horizontal = 12.dp, vertical = 24.dp)
                    // Whatever the row the focus left, it enters the drawer on the page that is shown.
                    .selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // The top section is the app's logo: the mark alone when collapsed, the lockup when expanded.
                Image(
                    painter = painterResource(if (drawerValue == DrawerValue.Open) R.drawable.logo_lockup else R.drawable.ic_logo_mark),
                    contentDescription = stringResource(R.string.app_name),
                    modifier = Modifier.padding(start = 8.dp, bottom = 24.dp).height(40.dp),
                    alignment = Alignment.CenterStart,
                )
                DrawerEntry(
                    icon = R.drawable.ic_search,
                    label = stringResource(R.string.nav_search),
                    isSelected = false,
                    onClick = openSearch,
                    modifier = Modifier.focusRequester(itemFocus[0]),
                )
                HomePage.entries.forEachIndexed { index, entry ->
                    DrawerEntry(
                        icon = entry.iconRes,
                        label = stringResource(entry.titleRes),
                        isSelected = index == selected,
                        onClick = {
                            selected = index
                            // The page cannot take the focus while the drawer is open: close it first.
                            drawerState.setValue(DrawerValue.Closed)
                            // The page is recomposed for the new destination: its focus is asked once it exists.
                            pageFocusRequests++
                        },
                        modifier = Modifier.focusRequester(itemFocus[index + 1]),
                    )
                }
            }
        },
    ) {
        CompositionLocalProvider(LocalPageFocusEnabled provides !isOpen) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(start = RailWidth)
                    .focusRequester(pageFocus)
                    .focusGroup(),
            ) {
                androidx.compose.runtime.key(selected) { page(HomePage.entries[selected]) {} }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun NavigationDrawerScope.DrawerEntry(
    @androidx.annotation.DrawableRes icon: Int,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = CodexTheme.colors
    NavigationDrawerItem(
        selected = isSelected,
        onClick = onClick,
        modifier = modifier,
        leadingContent = { Icon(painter = painterResource(icon), contentDescription = null, modifier = Modifier.size(26.dp)) },
        colors = NavigationDrawerItemDefaults.colors(
            contentColor = colors.ivory.copy(alpha = 0.75f),
            inactiveContentColor = colors.ivory.copy(alpha = 0.75f),
            selectedContentColor = colors.amber,
            selectedContainerColor = Color.Transparent,
            focusedContainerColor = colors.ivory,
            focusedContentColor = colors.night,
            focusedSelectedContainerColor = colors.ivory,
            focusedSelectedContentColor = colors.night,
            pressedContainerColor = colors.ivory,
            pressedContentColor = colors.night,
            pressedSelectedContainerColor = colors.ivory,
            pressedSelectedContentColor = colors.night,
        ),
    ) {
        Text(text = label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

/** The factory for [MainViewModel]. */
internal fun Fragment.mainViewModelFactory(): ViewModelProvider.Factory = viewModelFactory {
    initializer {
        val container = appContainer
        MainViewModel(
            container.catalogRepository,
            container.myListRepository,
            container.watchProgressRepository,
            container.watchedEpisodesRepository,
            container.watchNextPublisher,
            container.persistenceScope,
        )
    }
}
