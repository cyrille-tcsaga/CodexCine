package dev.codexsoft.codexcine.ui.compose.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp

private val ColumnGap = 8.dp
private val EdgePadding = 12.dp
private const val INITIAL_FOCUS_ATTEMPTS = 20

/**
 * A grid of [columns] cards of [cardWidth] dp, centred like Leanback's `VerticalGridPresenter`. Keyed, so
 * that an update keeps the focused card in place; the focus comes back to the last card when the grid is
 * entered again. A key must be storable in a Bundle (a String or an Int): lazy lists save each item's state
 * under it. [onSelected] gets the position of the card that takes the focus (to load the next page).
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun <T> CardGrid(
    items: List<T>,
    columns: Int,
    cardWidth: Int,
    key: (T) -> Any,
    modifier: Modifier = Modifier,
    onSelected: (Int) -> Unit = {},
    card: @Composable (T) -> Unit,
) {
    val gridFocus = remember { FocusRequester() }
    // Leanback selects the first card by itself; here the grid asks for the focus once it has cards.
    LaunchedEffect(items.isNotEmpty()) {
        if (items.isEmpty()) return@LaunchedEffect
        // The cards exist only after a layout pass, and the window may not have the focus yet: try again.
        repeat(INITIAL_FOCUS_ATTEMPTS) {
            withFrameNanos { }
            if (runCatching { gridFocus.requestFocus() }.getOrDefault(false)) return@LaunchedEffect
        }
    }
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = Modifier
                .width((columns * cardWidth).dp + ColumnGap * (columns - 1) + EdgePadding * 2)
                .fillMaxHeight()
                .focusRequester(gridFocus)
                .focusRestorer(),
            contentPadding = PaddingValues(horizontal = EdgePadding, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(ColumnGap),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            itemsIndexed(items, key = { _, item -> key(item) }) { index, item ->
                Box(Modifier.onFocusChanged { if (it.hasFocus) onSelected(index) }) { card(item) }
            }
        }
    }
}
