package dev.codexsoft.codexcine.ui.playback.web

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.scrollBy
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import dev.codexsoft.codexcine.R
import dev.codexsoft.codexcine.core.Formatters
import dev.codexsoft.codexcine.ui.compose.components.LoadingIndicator
import dev.codexsoft.codexcine.ui.compose.components.dpadClickable
import dev.codexsoft.codexcine.ui.compose.components.requestFocusWhenReady
import dev.codexsoft.codexcine.ui.compose.theme.BrandDisplay
import dev.codexsoft.codexcine.ui.compose.theme.BrandText
import dev.codexsoft.codexcine.ui.compose.theme.CodexTheme
import kotlinx.coroutines.delay

private val SafeHorizontal = 48.dp
private val SafeVertical = 27.dp
private const val FOCUSED_SCALE = 1.1f
private const val FEEDBACK_MS = 800L
private const val COUNTDOWN_MAX_S = 5

private val PlayerBody = TextStyle(fontFamily = BrandText, fontSize = 18.sp, color = Color.White)
private const val TABULAR_NUMBERS = "tnum"

/** The focus requesters the controls expose, so that the fragment's requests can be honoured. */
private class PlayerFocus {
    val root = FocusRequester()
    val progress = FocusRequester()
    val playPause = FocusRequester()
    val exitContinue = FocusRequester()
    val resumeYes = FocusRequester()
    val nextNow = FocusRequester()
    val errorRetry = FocusRequester()
    val speedOptions = BridgeProtocol.ALLOWED_RATES.associateWith { FocusRequester() }
}

@Composable
internal fun PlayerOverlayContent(
    state: PlayerOverlayState,
    onAction: (OverlayAction) -> Unit,
    onRate: (Double) -> Unit,
    onAccessibilitySeek: (Int) -> Unit,
) {
    val focus = remember { PlayerFocus() }

    LaunchedEffect(state.focusRequest) {
        val target = state.focusRequest ?: return@LaunchedEffect
        val requester = when (target) {
            PlayerFocusTarget.PROGRESS -> focus.progress
            PlayerFocusTarget.PLAY_PAUSE -> focus.playPause
            PlayerFocusTarget.ROOT -> focus.root
            PlayerFocusTarget.SPEED_OPTION -> focus.speedOptions[state.rate] ?: focus.speedOptions.values.first()
            PlayerFocusTarget.EXIT -> focus.exitContinue
            PlayerFocusTarget.RESUME -> focus.resumeYes
            PlayerFocusTarget.NEXT -> focus.nextNow
            PlayerFocusTarget.ERROR -> focus.errorRetry
        }
        requester.requestFocusWhenReady()
        if (state.focusRequest == target) state.focusRequest = null
    }

    Box(
        Modifier
            .fillMaxSize()
            // Holds the focus inside the player when every control is hidden.
            .focusRequester(focus.root)
            .focusProperties { canFocus = !state.controlsVisible && state.panel == PlayerPanel.NONE }
            .focusable(),
    ) {
        val barsEnabled = state.controlsVisible && state.panel == PlayerPanel.NONE
        TopBar(state, Modifier.align(Alignment.TopStart))
        if (!state.isBasic) {
            BottomBar(state, focus, onAction, onAccessibilitySeek, barsEnabled, Modifier.align(Alignment.BottomStart))
        }
        Loading(state)
        Feedback(state)
        state.banner?.let { Banner(it) }
        Panels(state, focus, onAction, onRate)
    }
}

// region Bars

@Composable
private fun TopBar(state: PlayerOverlayState, modifier: Modifier) {
    val alpha by animateFloatAsState(if (state.controlsVisible) 1f else 0f, tween(200), label = "topBar")
    Row(
        modifier
            .fillMaxWidth()
            .alpha(alpha)
            .background(Brush.verticalGradient(listOf(colorResource(R.color.player_scrim_top), Color.Transparent)))
            .padding(start = SafeHorizontal, end = SafeHorizontal, top = SafeVertical, bottom = 56.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = state.title,
                style = TextStyle(fontFamily = BrandDisplay, fontWeight = FontWeight.Bold, fontSize = 26.sp, color = Color.White),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (state.subtitle.isNotEmpty()) {
                    Text(state.subtitle, style = PlayerBody, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.width(if (state.subtitle.isEmpty()) 0.dp else 12.dp))
                state.languages.forEach { label ->
                    Text(
                        text = label,
                        style = PlayerBody,
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .border(1.dp, Color.White, RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp),
                    )
                }
            }
        }
        Text(state.clock, style = PlayerBody)
    }
}

@Composable
private fun BottomBar(
    state: PlayerOverlayState,
    focus: PlayerFocus,
    onAction: (OverlayAction) -> Unit,
    onAccessibilitySeek: (Int) -> Unit,
    enabled: Boolean,
    modifier: Modifier,
) {
    val alpha by animateFloatAsState(
        when {
            state.panel == PlayerPanel.ERROR -> 0f
            state.controlsVisible -> 1f
            else -> 0f
        },
        tween(200),
        label = "bottomBar",
    )
    var hint by remember { mutableStateOf("") }
    Column(
        modifier
            .fillMaxWidth()
            .alpha(alpha)
            .background(Brush.verticalGradient(listOf(Color.Transparent, colorResource(R.color.player_scrim_bottom))))
            .padding(start = SafeHorizontal, end = SafeHorizontal, top = 56.dp, bottom = SafeVertical)
            .focusProperties { canFocus = enabled }
            .onFocusChanged { state.bottomBarFocused = it.hasFocus },
    ) {
        SeekPreview(state)
        ProgressBar(state, focus.progress, focus.playPause, onAction, onAccessibilitySeek) { if (it) hint = "" }
        Box(Modifier.fillMaxWidth()) {
            Text(state.elapsed, style = PlayerBody.merge(TextStyle(fontFeatureSettings = TABULAR_NUMBERS)), modifier = Modifier.align(Alignment.CenterStart))
            Text(state.endTime, style = PlayerBody.merge(TextStyle(fontFeatureSettings = TABULAR_NUMBERS)), modifier = Modifier.align(Alignment.Center))
            Text(state.remaining, style = PlayerBody.merge(TextStyle(fontFeatureSettings = TABULAR_NUMBERS)), modifier = Modifier.align(Alignment.CenterEnd))
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp).onFocusChanged { if (!it.hasFocus) hint = "" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val speedLabel = Formatters.rate(state.rate)
            val speedText = stringResource(R.string.player_rate, speedLabel)
            val cdRewind = stringResource(R.string.player_cd_rewind)
            val cdPlay = stringResource(if (state.isPaused) R.string.player_cd_play else R.string.player_cd_pause)
            val cdForward = stringResource(R.string.player_cd_forward)
            val cdSpeed = stringResource(R.string.player_cd_speed, speedLabel)
            val cdMute = stringResource(if (state.isMuted) R.string.player_cd_unmute else R.string.player_cd_mute)
            val cdNext = stringResource(R.string.player_cd_next)
            val cdReload = stringResource(R.string.player_cd_reload)
            val cdQuit = stringResource(R.string.player_cd_quit)

            IconButton(R.drawable.ic_player_rewind, cdRewind, focus.progress, { hint = cdRewind }, { onAction(OverlayAction.REWIND) })
            IconButton(
                if (state.isPaused) R.drawable.ic_player_play else R.drawable.ic_player_pause,
                cdPlay,
                focus.progress,
                { hint = cdPlay },
                { onAction(OverlayAction.PLAY_PAUSE) },
                Modifier.focusRequester(focus.playPause),
            )
            IconButton(R.drawable.ic_player_forward, cdForward, focus.progress, { hint = cdForward }, { onAction(OverlayAction.FORWARD) })
            TextButton(speedText, { hint = cdSpeed }, { onAction(OverlayAction.SPEED) }, Modifier.padding(end = 12.dp), up = focus.progress)
            IconButton(
                if (state.isMuted) R.drawable.ic_player_muted else R.drawable.ic_player_volume,
                cdMute,
                focus.progress,
                { hint = cdMute },
                { onAction(OverlayAction.MUTE) },
            )
            if (state.hasNext) IconButton(R.drawable.ic_player_next, cdNext, focus.progress, { hint = cdNext }, { onAction(OverlayAction.NEXT) })
            IconButton(R.drawable.ic_player_reload, cdReload, focus.progress, { hint = cdReload }, { onAction(OverlayAction.RELOAD) })
            IconButton(R.drawable.ic_player_close, cdQuit, focus.progress, { hint = cdQuit }, { onAction(OverlayAction.QUIT) })
            // Icons alone say little at three metres: this names the focused control.
            Text(
                text = hint,
                style = PlayerBody,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = 12.dp),
            )
        }
    }
}

/** The thumb's horizontal centre at [seconds], in a bar [widthPx] wide: the inset keeps the big thumb inside. */
private fun thumbX(seconds: Double, duration: Double, widthPx: Float, insetPx: Float): Float {
    val fraction = if (duration > 0) (seconds / duration).coerceIn(0.0, 1.0) else 0.0
    return insetPx + (widthPx - 2 * insetPx) * fraction.toFloat()
}

@Composable
private fun SeekPreview(state: PlayerOverlayState) {
    val inset = with(LocalDensity.current) { 11.dp.toPx() }
    val shown = state.seekPreview
    Text(
        text = state.seekPreviewText,
        style = TextStyle(fontFamily = BrandText, fontWeight = FontWeight.Bold, fontSize = 24.sp, color = Color.White),
        modifier = Modifier
            .alpha(if (shown == null) 0f else 1f)
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                val width = constraints.maxWidth
                layout(width, placeable.height) {
                    val centred = thumbX(shown ?: 0.0, state.duration, width.toFloat(), inset) - placeable.width / 2f
                    placeable.placeRelative(centred.coerceIn(0f, (width - placeable.width).toFloat().coerceAtLeast(0f)).toInt(), 0)
                }
            },
    )
}

@Composable
private fun ProgressBar(
    state: PlayerOverlayState,
    requester: FocusRequester,
    playPause: FocusRequester,
    onAction: (OverlayAction) -> Unit,
    onAccessibilitySeek: (Int) -> Unit,
    onFocusGained: (Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val track = colorResource(R.color.player_track)
    val buffered = colorResource(R.color.player_buffered)
    val played = colorResource(R.color.selected_background)
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(32.dp)
            .focusRequester(requester)
            // As nextFocusDown in the view layout: Compose would pick the nearest button, not play/pause.
            .focusProperties { down = playPause }
            .dpadClickable({ onAction(OverlayAction.PLAY_PAUSE) })
            .onFocusChanged {
                focused = it.isFocused
                state.progressFocused = it.isFocused
                if (it.isFocused) onFocusGained(true)
            }
            .focusable()
            .semantics {
                contentDescription = state.progressDescription
                scrollBy { x, y ->
                    onAccessibilitySeek(if (x + y >= 0) 1 else -1)
                    true
                }
            },
    ) {
        if (state.duration <= 0) return@Canvas
        val trackHeight = 6.dp.toPx()
        val thumb = 8.dp.toPx()
        val focusedThumb = 11.dp.toPx()
        val centerY = size.height / 2
        val top = centerY - trackHeight / 2
        fun x(seconds: Double) = thumbX(seconds, state.duration, size.width, focusedThumb)
        drawRoundRect(track, Offset(x(0.0), top), Size(x(state.duration) - x(0.0), trackHeight), androidx.compose.ui.geometry.CornerRadius(trackHeight))
        drawRect(buffered, Offset(x(0.0), top), Size((x(state.buffered) - x(0.0)).coerceAtLeast(0f), trackHeight))
        drawRect(played, Offset(x(0.0), top), Size((x(state.position) - x(0.0)).coerceAtLeast(0f), trackHeight))
        val thumbCenter = Offset(x(state.position), centerY)
        if (focused) {
            drawCircle(played, focusedThumb, thumbCenter)
            drawCircle(Color.White, focusedThumb, thumbCenter, style = Stroke(width = 3.dp.toPx()))
        } else {
            drawCircle(played, thumb, thumbCenter)
        }
    }
}

// endregion

// region Buttons

@Composable
private fun IconButton(
    @androidx.annotation.DrawableRes icon: Int,
    description: String,
    up: FocusRequester,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) FOCUSED_SCALE else 1f, tween(150), label = "iconScale")
    val colors = CodexTheme.colors
    Box(
        modifier
            .padding(end = 12.dp)
            .size(56.dp)
            .scale(scale)
            .focusProperties { this.up = up }
            .dpadClickable(onClick)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .focusable()
            .semantics { contentDescription = description }
            .clip(CircleShape)
            .background(if (focused) colors.ivory else Color.Transparent),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(32.dp),
            colorFilter = ColorFilter.tint(if (focused) colors.night else colors.ivory),
        )
    }
}

@Composable
private fun TextButton(
    text: String,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fillWidth: Boolean = false,
    up: FocusRequester? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) FOCUSED_SCALE else 1f, tween(150), label = "textScale")
    val colors = CodexTheme.colors
    Box(
        modifier
            .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
            .heightIn(min = 56.dp)
            .widthIn(min = 56.dp)
            .scale(scale)
            .then(if (up != null) Modifier.focusProperties { this.up = up } else Modifier)
            .dpadClickable(onClick)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .focusable()
            .clip(RoundedCornerShape(50))
            .background(if (focused) colors.ivory else colors.pill)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = TextStyle(fontFamily = BrandDisplay, fontWeight = FontWeight.Bold, fontSize = 20.sp),
            color = if (focused) colors.night else colors.ivory,
        )
    }
}

// endregion

// region Loading, feedback, banner

@Composable
private fun BoxScope.Loading(state: PlayerOverlayState) {
    if (!state.isLoading && !state.isBuffering) return
    Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
        LoadingIndicator(diameter = 72.dp)
        if (state.isLoading) {
            Text(
                text = stringResource(R.string.player_loading),
                style = TextStyle(fontFamily = BrandText, fontSize = 20.sp, color = Color.White),
                modifier = Modifier.padding(top = 16.dp),
            )
            Text(state.providerName, style = PlayerBody, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun PanelSurface(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    // Intrinsic width: a panel is as wide as its widest line, not as wide as the screen.
    Box(modifier.width(IntrinsicSize.Max).background(colorResource(R.color.player_panel), RoundedCornerShape(16.dp))) { content() }
}

@Composable
private fun BoxScope.Feedback(state: PlayerOverlayState) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(state.feedbackSerial) {
        if (state.feedbackSerial == 0) return@LaunchedEffect
        visible = true
        delay(FEEDBACK_MS)
        visible = false
    }
    if (!visible) return
    PanelSurface(Modifier.align(Alignment.Center).widthIn(min = 160.dp)) {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            state.feedbackIcon?.let { Image(painterResource(it), contentDescription = null, modifier = Modifier.size(56.dp)) }
            Text(
                text = state.feedbackText,
                style = TextStyle(fontFamily = BrandText, fontWeight = FontWeight.Bold, fontSize = 24.sp, color = Color.White),
                modifier = Modifier.padding(top = if (state.feedbackIcon != null) 8.dp else 0.dp),
            )
        }
    }
}

@Composable
private fun BoxScope.Banner(text: String) {
    PanelSurface(Modifier.align(Alignment.TopCenter).padding(top = 96.dp)) {
        Text(text, style = PlayerBody, modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp))
    }
}

// endregion

// region Panels

private val PanelTitle = TextStyle(fontFamily = BrandDisplay, fontWeight = FontWeight.Bold, fontSize = 24.sp, color = Color.White)

@Composable
private fun BoxScope.Panels(
    state: PlayerOverlayState,
    focus: PlayerFocus,
    onAction: (OverlayAction) -> Unit,
    onRate: (Double) -> Unit,
) {
    when (state.panel) {
        PlayerPanel.NONE -> Unit
        PlayerPanel.SPEED -> PanelSurface(Modifier.align(Alignment.Center)) {
            Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.player_speed_title), style = PanelTitle, modifier = Modifier.padding(bottom = 16.dp))
                BridgeProtocol.ALLOWED_RATES.forEach { rate ->
                    val label = Formatters.rate(rate)
                    TextButton(
                        text = stringResource(if (rate == state.rate) R.string.player_rate_selected else R.string.player_rate, label),
                        onFocused = {},
                        onClick = { onRate(rate) },
                        modifier = Modifier.padding(bottom = 8.dp).focusRequester(focus.speedOptions.getValue(rate)),
                        fillWidth = true,
                    )
                }
            }
        }
        PlayerPanel.EXIT -> PanelSurface(Modifier.align(Alignment.Center)) {
            Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.player_exit_title), style = PanelTitle, modifier = Modifier.padding(bottom = 24.dp))
                Row {
                    TextButton(
                        stringResource(R.string.player_continue), {}, { onAction(OverlayAction.EXIT_CONTINUE) },
                        Modifier.padding(end = 12.dp).focusRequester(focus.exitContinue),
                    )
                    TextButton(stringResource(R.string.player_quit), {}, { onAction(OverlayAction.EXIT_QUIT) })
                }
            }
        }
        PlayerPanel.RESUME -> PanelSurface(Modifier.align(Alignment.Center)) {
            Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(state.resumeTitle, style = PanelTitle, modifier = Modifier.padding(bottom = 24.dp))
                Row {
                    TextButton(
                        stringResource(R.string.player_resume), {}, { onAction(OverlayAction.RESUME_YES) },
                        Modifier.padding(end = 12.dp).focusRequester(focus.resumeYes),
                    )
                    TextButton(stringResource(R.string.player_from_start), {}, { onAction(OverlayAction.RESUME_NO) })
                }
            }
        }
        PlayerPanel.NEXT -> PanelSurface(Modifier.align(Alignment.BottomEnd).padding(end = SafeHorizontal, bottom = SafeVertical)) {
            Column(Modifier.padding(32.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CountdownRing(state.nextSecondsLeft, Modifier.padding(end = 16.dp))
                    Text(state.nextTitle, style = PlayerBody, modifier = Modifier.widthIn(max = 480.dp))
                }
                Row(Modifier.padding(top = 16.dp)) {
                    TextButton(
                        stringResource(R.string.player_play_now), {}, { onAction(OverlayAction.NEXT_NOW) },
                        Modifier.padding(end = 12.dp).focusRequester(focus.nextNow),
                    )
                    TextButton(stringResource(R.string.player_cancel), {}, { onAction(OverlayAction.NEXT_CANCEL) })
                }
            }
        }
        PlayerPanel.ERROR -> PanelSurface(Modifier.align(Alignment.Center)) {
            Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(state.errorTitle, style = PanelTitle, modifier = Modifier.padding(bottom = 8.dp))
                Text(
                    text = state.errorMessage,
                    style = PlayerBody,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 640.dp).padding(bottom = 24.dp),
                )
                Row {
                    TextButton(
                        stringResource(R.string.retry), {}, { onAction(OverlayAction.ERROR_RETRY) },
                        Modifier.padding(end = 12.dp).focusRequester(focus.errorRetry),
                    )
                    TextButton(stringResource(R.string.back), {}, { onAction(OverlayAction.ERROR_BACK) })
                }
            }
        }
    }
}

/** The seconds left in a ring that empties (`ring_player_countdown`), with the number in the middle. */
@Composable
private fun CountdownRing(secondsLeft: Int, modifier: Modifier = Modifier) {
    val track = colorResource(R.color.player_buffered)
    val bar = colorResource(R.color.selected_background)
    Box(modifier.size(56.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = size.minDimension / 12f
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            drawArc(
                bar, -90f, 360f * secondsLeft.coerceIn(0, COUNTDOWN_MAX_S) / COUNTDOWN_MAX_S, false,
                Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Butt),
            )
        }
        Text(stringResource(R.string.player_countdown, secondsLeft), style = PlayerBody)
    }
}

// endregion
