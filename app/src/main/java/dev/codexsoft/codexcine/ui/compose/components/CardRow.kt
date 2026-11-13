package dev.codexsoft.codexcine.ui.compose.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import androidx.tv.material3.Text
import dev.codexsoft.codexcine.ui.compose.theme.CodexTheme

private const val REFOCUS_WAIT_MS = 3_000L

/** Called when the viewer presses Left on the first card of a row: the home opens its sidebar. */
val LocalLeftEdge = androidx.compose.runtime.staticCompositionLocalOf<(() -> Unit)?> { null }

/**
 * A titled row of cards. Items are keyed, so that updating the list keeps the focused card in place, and
 * `focusRestorer` gives the focus back to the card that had it when the row is entered again.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun <T> CardRow(
    title: String,
    items: List<T>,
    key: (T) -> Any,
    modifier: Modifier = Modifier,
    refocusIndex: Int? = null,
    refocusFromSize: Int = 0,
    onRefocused: () -> Unit = {},
    card: @Composable (T) -> Unit,
) {
    val horizontal = CodexTheme.dimens.heroPaddingStart
    val onLeftEdge = LocalLeftEdge.current
    // After a card left the row, the focus goes to the one that took its place (or the last). The request
    // is armed with the row's size and acts once the row is smaller: asking at once would land on the card
    // that is about to disappear.
    val refocus = remember { FocusRequester() }
    val currentSize = rememberUpdatedState(items.size)
    val target = refocusIndex?.coerceAtMost(items.lastIndex)
    LaunchedEffect(refocusIndex) {
        if (refocusIndex == null) return@LaunchedEffect
        val armedSize = refocusFromSize
        // Gives up after a while: the removal may have failed.
        withTimeoutOrNull(REFOCUS_WAIT_MS) { snapshotFlow { currentSize.value }.first { it < armedSize } }
        if (currentSize.value > 0) refocus.requestFocusWhenReady(attempts = 20)
        onRefocused()
    }
    Column(modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = CodexTheme.colors.ivory,
            modifier = Modifier.padding(start = horizontal),
        )
        LazyRow(
            modifier = Modifier.focusRestorer(),
            contentPadding = PaddingValues(start = horizontal, end = horizontal, top = 12.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(CodexTheme.dimens.cardGap),
        ) {
            itemsIndexed(items, key = { _, item -> key(item) }) { index, item ->
                // The same wrapper for every card, whatever happens to the others: a wrapper that appears or
                // goes away would recreate the card and drop its focus. Only the modifiers change.
                Box(
                    Modifier
                        .then(if (index == target) Modifier.focusRequester(refocus) else Modifier)
                        .then(
                            if (index == 0 && onLeftEdge != null) {
                                Modifier.onKeyEvent { event ->
                                    val isLeft = event.type == KeyEventType.KeyDown && event.key == Key.DirectionLeft
                                    if (isLeft) onLeftEdge()
                                    isLeft
                                }
                            } else {
                                Modifier
                            },
                        )
                        .focusGroup(),
                ) { card(item) }
            }
        }
    }
}
