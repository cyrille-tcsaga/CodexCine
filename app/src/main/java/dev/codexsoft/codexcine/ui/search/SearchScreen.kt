package dev.codexsoft.codexcine.ui.search

import android.app.Activity
import android.app.SearchManager
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.codexsoft.codexcine.R
import dev.codexsoft.codexcine.core.ErrorKind
import dev.codexsoft.codexcine.domain.model.MediaItem
import dev.codexsoft.codexcine.ui.common.openMediaItem
import dev.codexsoft.codexcine.ui.compose.ComposeScreenFragment
import dev.codexsoft.codexcine.ui.compose.components.CardRow
import dev.codexsoft.codexcine.ui.compose.components.MediaCard
import dev.codexsoft.codexcine.ui.compose.components.ScreenBackground
import dev.codexsoft.codexcine.ui.compose.components.dpadClickable
import dev.codexsoft.codexcine.ui.compose.theme.CodexTheme
import java.util.Locale

/**
 * Search in Compose: a round button for the voice, a text field for the keyboard, and the results in rows
 * (films, shows, people) under them. Voice goes through the system recogniser (`RecognizerIntent`), so
 * the app needs no microphone permission of its own; without a recogniser the button is hidden and the
 * keyboard still works. A query sent by the system search or the Assistant is searched at once.
 */
class ComposeSearchFragment : ComposeScreenFragment() {

    private val viewModel: SearchViewModel by viewModels { searchViewModelFactory() }

    @Composable
    override fun ScreenContent() {
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        val intent = requireActivity().intent
        val external = remember {
            intent.takeIf { it.action in SEARCH_ACTIONS }
                ?.let { SearchSuggestions.cleanQuery(it.getStringExtra(SearchManager.QUERY)) }
        }
        var query by rememberSaveable { mutableStateOf(external.orEmpty()) }
        LaunchedEffect(external) {
            if (external != null) viewModel.onQuerySubmitted(external)
        }
        val canListen = remember {
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).resolveActivity(requireContext().packageManager) != null
        }
        val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
                SearchSuggestions.cleanQuery(spoken)?.let {
                    query = it
                    viewModel.onQuerySubmitted(it)
                }
            }
        }
        val field = remember { FocusRequester() }
        LaunchedEffect(Unit) { runCatching { field.requestFocus() } }

        ScreenBackground {
            Column(
                query = query,
                onQuery = {
                    query = it
                    viewModel.onQueryChanged(it)
                },
                onSubmit = { viewModel.onQuerySubmitted(query) },
                onVoice = if (canListen) {
                    {
                        voice.launch(
                            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                                .putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.search_hint)),
                        )
                    }
                } else {
                    null
                },
                field = field,
                state = state,
                onOpen = ::openMediaItem,
            )
        }
    }
}

@Composable
private fun Column(
    query: String,
    onQuery: (String) -> Unit,
    onSubmit: () -> Unit,
    onVoice: (() -> Unit)?,
    field: FocusRequester,
    state: SearchUiState,
    onOpen: (MediaItem) -> Unit,
) {
    androidx.compose.foundation.layout.Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = CodexTheme.dimens.heroPaddingStart, end = CodexTheme.dimens.heroPaddingStart, top = 32.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onVoice != null) {
                SearchOrb(onClick = onVoice)
                Spacer(Modifier.size(24.dp))
            }
            SearchField(query, onQuery, onSubmit, field, Modifier.weight(1f))
        }
        LazyColumn(Modifier.fillMaxSize().padding(top = 16.dp)) {
            when (state) {
                SearchUiState.Idle -> Unit
                is SearchUiState.Failed -> item { Message(stringResource(errorMessage(state.kind))) }
                is SearchUiState.Results -> {
                    val found = state.results
                    if (found.isEmpty) {
                        item { Message(stringResource(R.string.search_no_results, state.query)) }
                    } else {
                        resultRow(R.string.row_movies, found.movies, onOpen)
                        resultRow(R.string.row_tv_shows, found.shows, onOpen)
                        resultRow(R.string.row_people, found.people, onOpen)
                    }
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.resultRow(
    titleRes: Int,
    items: List<MediaItem>,
    onOpen: (MediaItem) -> Unit,
) {
    if (items.isEmpty()) return
    item(key = "row:$titleRes") {
        CardRow(stringResource(titleRes), items, key = { "${it.mediaType.value}:${it.id}" }) { media ->
            MediaCard(media, onClick = { onOpen(media) })
        }
    }
}

@Composable
private fun Message(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        color = CodexTheme.colors.ivory,
        modifier = Modifier.padding(start = CodexTheme.dimens.heroPaddingStart, top = 8.dp),
    )
}

private fun errorMessage(kind: ErrorKind): Int = when (kind) {
    ErrorKind.INVALID_TOKEN -> R.string.error_invalid_token
    ErrorKind.NO_CONNECTION -> R.string.error_no_connection
    ErrorKind.GENERIC -> R.string.error_generic
}

/** The round amber button that starts a voice search; it turns ivory with focus. */
@Composable
private fun SearchOrb(onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .size(62.dp)
            .dpadClickable(onClick)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clip(CircleShape)
            .background(if (focused) CodexTheme.colors.ivory else CodexTheme.colors.amber),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_mic),
            contentDescription = null,
            modifier = Modifier.size(30.dp),
            colorFilter = ColorFilter.tint(CodexTheme.colors.night),
        )
    }
}

@Composable
private fun SearchField(
    query: String,
    onQuery: (String) -> Unit,
    onSubmit: () -> Unit,
    focus: FocusRequester,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val colors = CodexTheme.colors
    val focusManager = LocalFocusManager.current
    Box(
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (focused) colors.ivory else colors.pill)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        val textColor = if (focused) colors.night else colors.ivory
        if (query.isEmpty()) {
            Text(
                text = stringResource(R.string.search_hint),
                style = MaterialTheme.typography.bodyLarge.merge(TextStyle(fontSize = 24.sp)),
                color = if (focused) colors.night.copy(alpha = 0.55f) else colors.mist,
            )
        }
        BasicTextField(
            value = query,
            onValueChange = onQuery,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.merge(TextStyle(fontSize = 24.sp, color = textColor)),
            cursorBrush = SolidColor(textColor),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focus)
                // A text field keeps the arrow keys for its caret: let them leave it, as on every other screen.
                .onPreviewKeyEvent { event ->
                    val direction = when (event.key) {
                        Key.DirectionLeft -> FocusDirection.Left
                        Key.DirectionUp -> FocusDirection.Up
                        Key.DirectionDown -> FocusDirection.Down
                        else -> null
                    }
                    direction != null && event.type == KeyEventType.KeyDown && focusManager.moveFocus(direction)
                }
                .onFocusChanged { focused = it.isFocused },
        )
    }
}
