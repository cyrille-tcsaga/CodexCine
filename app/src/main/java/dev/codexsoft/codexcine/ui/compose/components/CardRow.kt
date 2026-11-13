package dev.codexsoft.codexcine.ui.compose.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.codexsoft.codexcine.ui.compose.theme.CodexTheme

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
    card: @Composable (T) -> Unit,
) {
    val horizontal = CodexTheme.dimens.heroPaddingStart
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
            items(items, key = key) { card(it) }
        }
    }
}
