package dev.codexsoft.codexcine.ui.compose.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val SETTLE_MS = 120L

/**
 * Put on the first item of a list: when the focus comes back to it, the list scrolls all the way up. Without it,
 * going back up stops as soon as the focused control is fully visible (a "Play" button) and the title and the
 * summary above it can no longer be reached.
 */
@Composable
fun Modifier.scrollToTopOnFocus(listState: LazyListState): Modifier {
    val scope = rememberCoroutineScope()
    return onFocusChanged {
        if (it.hasFocus) {
            scope.launch {
                // The focus change also asks the list to bring the focused item into view, and that request would
                // cancel this one: let it finish first, then go to the top.
                delay(SETTLE_MS)
                listState.animateScrollToItem(0)
            }
        }
    }
}
