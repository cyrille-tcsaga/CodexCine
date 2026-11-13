package dev.codexsoft.codexcine.ui.compose.components

import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester
import kotlinx.coroutines.delay

/**
 * Asks for the focus until it is granted. A screen that has just opened often cannot take it yet: its
 * items are not laid out, or the window has not got the focus. Leanback selected the first card by itself;
 * here every screen that wants an initial focus goes through this. Returns whether it got the focus.
 */
suspend fun FocusRequester.requestFocusWhenReady(attempts: Int = 60, intervalMs: Long = 100): Boolean {
    repeat(attempts) {
        withFrameNanos { }
        if (runCatching { requestFocus() }.getOrDefault(false)) return true
        delay(intervalMs)
    }
    return false
}
