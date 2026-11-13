package dev.codexsoft.codexcine.ui.browse

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
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
import androidx.tv.material3.Text
import dev.codexsoft.codexcine.R
import dev.codexsoft.codexcine.ui.common.showErrorScreen
import dev.codexsoft.codexcine.ui.compose.ComposeScreenFragment
import dev.codexsoft.codexcine.ui.compose.components.LoadingIndicator
import dev.codexsoft.codexcine.ui.compose.components.dpadClickable
import dev.codexsoft.codexcine.ui.compose.theme.CodexTheme
import dev.codexsoft.codexcine.ui.search.SearchActivity

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

        Box(Modifier.fillMaxSize().background(CodexTheme.colors.night)) {
            when (state) {
                MainUiState.Loading -> LoadingIndicator(Modifier.align(Alignment.Center))
                is MainUiState.Content -> HomeShell(
                    openSearch = { startActivity(Intent(requireContext(), SearchActivity::class.java)) },
                ) { page -> HomePageContent(page) }
                is MainUiState.Error -> Unit
            }
        }
    }

    /** What a page shows. The rows come with the next stories. */
    @Composable
    private fun HomePageContent(page: HomePage) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(page.titleRes), style = MaterialTheme.typography.displayLarge, color = CodexTheme.colors.ivory)
        }
    }
}

/**
 * The sidebar, the page, and the buttons on top. [page] draws the selected page; it keeps the focus
 * while the sidebar is closed.
 */
@Composable
private fun HomeShell(openSearch: () -> Unit, page: @Composable (HomePage) -> Unit) {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    var sidebarOpen by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val entryFocus = remember { HomePage.entries.map { FocusRequester() } }
    val pageFocus = remember { FocusRequester() }

    fun openSidebar() {
        sidebarOpen = true
    }

    fun closeSidebar() {
        sidebarOpen = false
        runCatching { pageFocus.requestFocus() }
    }

    // Leanback opens the sidebar on Back from a page; a second Back leaves the app.
    BackHandler(enabled = !sidebarOpen) { openSidebar() }

    // The sidebar takes the focus when it opens, on the entry of the page shown.
    LaunchedEffect(sidebarOpen) {
        if (sidebarOpen) {
            androidx.compose.runtime.withFrameNanos { }
            runCatching { entryFocus[selected].requestFocus() }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .focusRequester(pageFocus)
                .focusGroup()
                // Left from the leftmost card has nowhere to go: that is the sidebar.
                .onKeyEvent { event ->
                    if (!sidebarOpen && event.type == KeyEventType.KeyDown && event.key == Key.DirectionLeft) {
                        if (!focusManager.moveFocus(FocusDirection.Left)) openSidebar()
                        true
                    } else {
                        false
                    }
                },
        ) {
            androidx.compose.runtime.key(selected) { page(HomePage.entries[selected]) }
        }

        val offset by animateDpAsState(if (sidebarOpen) 0.dp else -SidebarWidth, tween(220), label = "sidebar")
        Sidebar(
            modifier = Modifier.offset(x = offset),
            entryFocus = entryFocus,
            selected = selected,
            open = sidebarOpen,
            onSelect = { selected = it },
            onClose = ::closeSidebar,
        )

        // Over the sidebar, as in Leanback: the search button stays visible and reachable.
        TopButtons(onSearch = openSearch, searchEnabled = true)
    }
}

/** The search button at the top left and the logo at the top right. */
@Composable
private fun TopButtons(onSearch: () -> Unit, searchEnabled: Boolean) {
    Box(Modifier.fillMaxSize()) {
        if (searchEnabled) {
            SearchOrb(onSearch, Modifier.align(Alignment.TopStart).padding(start = SidebarPadding, top = 30.dp))
        }
        Image(
            painter = painterResource(R.drawable.logo_lockup),
            contentDescription = null,
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 30.dp, end = CodexTheme.dimens.heroPaddingStart).height(64.dp),
        )
    }
}

@Composable
private fun SearchOrb(onClick: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .size(62.dp)
            .dpadClickable(onClick)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clip(CircleShape)
            .background(if (focused) CodexTheme.colors.ivory else CodexTheme.colors.amber),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_search),
            contentDescription = stringResource(R.string.search_hint),
            modifier = Modifier.size(30.dp),
            colorFilter = ColorFilter.tint(CodexTheme.colors.night),
        )
    }
}

/** The page entries. Only focusable while [open], so that a closed sidebar never catches the focus. */
@Composable
private fun Sidebar(
    modifier: Modifier,
    entryFocus: List<FocusRequester>,
    selected: Int,
    open: Boolean,
    onSelect: (Int) -> Unit,
    onClose: () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(SidebarWidth)
            .background(colorResource(R.color.fastlane_background))
            .focusProperties { canFocus = open }
            .onKeyEvent { event ->
                if (open && event.type == KeyEventType.KeyDown && event.key == Key.DirectionRight) {
                    onClose()
                    true
                } else {
                    false
                }
            }
            .padding(start = SidebarPadding),
        verticalArrangement = Arrangement.Center,
    ) {
        HomePage.entries.forEachIndexed { index, entry ->
            SidebarEntry(
                page = entry,
                isSelected = index == selected,
                onFocus = { onSelect(index) },
                onClick = onClose,
                modifier = Modifier.focusRequester(entryFocus[index]),
            )
        }
    }
}

@Composable
private fun SidebarEntry(
    page: HomePage,
    isSelected: Boolean,
    onFocus: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val colors = CodexTheme.colors
    val background by animateColorAsState(if (focused) colors.ivory else androidx.compose.ui.graphics.Color.Transparent, label = "entry")
    val content = if (focused) colors.night else colors.ivory
    Row(
        modifier = modifier
            .padding(vertical = 10.dp)
            .alpha(if (focused || isSelected) 1f else 0.6f)
            .dpadClickable(onClick)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus()
            }
            .focusable()
            .background(background, RoundedCornerShape(percent = 50))
            .padding(start = 16.dp, end = 24.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(page.iconRes),
            contentDescription = null,
            modifier = Modifier.size(26.dp),
            colorFilter = ColorFilter.tint(content),
        )
        Text(
            text = stringResource(page.titleRes),
            style = MaterialTheme.typography.labelLarge,
            color = content,
            modifier = Modifier.padding(start = 16.dp),
        )
    }
}
