package dev.codexsoft.codexcine.ui.compose.components

import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type

/**
 * Tells a click from a long press on the remote's OK key. The long press fires once, while the key is
 * still held; the release that follows it is not a click. A release with no press seen first (the focus
 * arrived while the key was down, say after a menu closed) is ignored.
 */
class DpadPressTracker {
    enum class Result { NONE, CLICK, LONG_CLICK }

    private var pressed = false
    private var longFired = false

    fun down(repeatCount: Int): Result {
        if (repeatCount == 0) {
            pressed = true
            longFired = false
        }
        if (pressed && !longFired && repeatCount >= LONG_PRESS_REPEAT) {
            longFired = true
            return Result.LONG_CLICK
        }
        return Result.NONE
    }

    fun up(): Result {
        val result = if (pressed && !longFired) Result.CLICK else Result.NONE
        pressed = false
        longFired = false
        return result
    }

    /** The first auto-repeat of a held key arrives after the system's long-press delay. */
    private companion object {
        const val LONG_PRESS_REPEAT = 1
    }
}

private val okKeys = setOf(Key.DirectionCenter, Key.Enter, Key.NumPadEnter)

/**
 * Click and long click on OK, like Leanback's `OnItemViewClickedListener` and `setOnLongClickListener`.
 * Place it before `focusable()`: key events reach it from the focused node below.
 */
fun Modifier.dpadClickable(onClick: () -> Unit, onLongClick: (() -> Unit)? = null): Modifier = composed {
    val tracker = remember { DpadPressTracker() }
    onKeyEvent { event ->
        if (event.key !in okKeys) return@onKeyEvent false
        when (event.type) {
            KeyEventType.KeyDown -> {
                if (tracker.down(event.nativeKeyEvent.repeatCount) == DpadPressTracker.Result.LONG_CLICK) {
                    onLongClick?.invoke()
                }
                true
            }
            KeyEventType.KeyUp -> {
                if (tracker.up() == DpadPressTracker.Result.CLICK) onClick()
                true
            }
            else -> false
        }
    }
}
