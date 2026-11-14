package dev.codexsoft.codexcine.ui.error

import android.os.Bundle
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.codexsoft.codexcine.R
import dev.codexsoft.codexcine.ui.common.FragmentHostActivity
import dev.codexsoft.codexcine.ui.compose.ComposeScreenFragment
import dev.codexsoft.codexcine.ui.compose.components.PillButton
import dev.codexsoft.codexcine.ui.compose.components.requestFocusWhenReady
import dev.codexsoft.codexcine.ui.compose.theme.CodexTheme

/** The error screen, for every screen that fails: the one place that builds it. */
fun errorScreen(
    message: String,
    retryable: Boolean,
    title: String? = null,
    @DrawableRes image: Int = R.drawable.ic_error,
): Fragment = ComposeErrorFragment().apply {
    arguments = Bundle().apply {
        putString(ErrorArgs.TITLE, title)
        putString(ErrorArgs.MESSAGE, message)
        putBoolean(ErrorArgs.RETRYABLE, retryable)
        putInt(ErrorArgs.IMAGE, image)
    }
}

/** Argument keys of the error screen. */
internal object ErrorArgs {
    const val TITLE = "title"
    const val MESSAGE = "message"
    const val RETRYABLE = "retryable"
    const val IMAGE = "image"
}

class ComposeErrorFragment : ComposeScreenFragment() {

    @Composable
    override fun ScreenContent() {
        val args = requireArguments()
        val retryable = args.getBoolean(ErrorArgs.RETRYABLE)
        ErrorScreen(
            title = args.getString(ErrorArgs.TITLE) ?: stringResource(R.string.app_name),
            message = args.getString(ErrorArgs.MESSAGE).orEmpty(),
            image = args.getInt(ErrorArgs.IMAGE),
            buttonText = stringResource(if (retryable) R.string.retry else R.string.back),
            onButton = {
                val host = activity
                if (retryable && host is FragmentHostActivity) host.showContent() else host?.finish()
            },
        )
    }
}

/** Full-screen message with a single button, which has the focus at once. */
@Composable
fun ErrorScreen(
    title: String,
    message: String,
    @DrawableRes image: Int,
    buttonText: String,
    onButton: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocusWhenReady() }
    Box(Modifier.fillMaxSize().background(CodexTheme.colors.night)) {
        Text(
            text = title,
            style = MaterialTheme.typography.displayLarge.merge(TextStyle(fontSize = 45.sp)),
            color = CodexTheme.colors.ivory,
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 40.dp, end = 56.dp),
        )
        Column(
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 120.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Image(painterResource(image), contentDescription = null, modifier = Modifier.size(120.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = CodexTheme.colors.ivory,
                textAlign = TextAlign.Center,
            )
            PillButton(text = buttonText, onClick = onButton, height = 48.dp, modifier = Modifier.focusRequester(focus))
        }
    }
}
