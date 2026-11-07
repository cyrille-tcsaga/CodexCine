package dev.codexsoft.codexcine.ui.playback.web

import android.view.KeyEvent
import kotlin.math.sign

enum class RevealFocus { CURRENT, PROGRESS, PLAY_PAUSE }

sealed interface PlayerCommand {
    data object TogglePlayPause : PlayerCommand
    data object Play : PlayerCommand
    data object Pause : PlayerCommand
    data object Unmute : PlayerCommand

    /** Relative seek in seconds. Zero is a skipped auto-repeat: the key stays ours, but nothing moves. */
    data class SeekBy(val seconds: Int) : PlayerCommand

    /** Absolute seek as a percentage of the duration. */
    data class JumpToPercent(val percent: Int) : PlayerCommand
    data class Reveal(val focus: RevealFocus) : PlayerCommand
    data object NextEpisode : PlayerCommand
    data object PreviousOrRestart : PlayerCommand
    data object OpenSpeedSelector : PlayerCommand
    data object HideOverlay : PlayerCommand
    data object ConfirmExit : PlayerCommand

    /** Not ours: the view hierarchy or the system handles this key. */
    data object PassThrough : PlayerCommand
}

data class KeyContext(
    val isOverlayVisible: Boolean,
    val isProgressFocused: Boolean = false,
    val isSeries: Boolean = false,
    val isUnmuteHintVisible: Boolean = false,
)

/**
 * Accelerates repeated seeks: 10 s for the first 3 presses, 30 s up to the 8th, then 60 s. Stops
 * accelerating after a second without a seek key, or when the direction changes.
 */
class SeekAccelerator {
    private var presses = 0
    private var direction = 0
    private var lastPressMs = Long.MIN_VALUE

    /** Returns the signed step, or 0 for a key auto-repeat that is skipped to keep long presses usable. */
    fun next(signedDirection: Int, repeatCount: Int, eventTimeMs: Long): Int {
        if (repeatCount % REPEATS_PER_STEP != 0) return 0
        val expired = lastPressMs == Long.MIN_VALUE || eventTimeMs - lastPressMs > RESET_AFTER_MS
        if (expired || signedDirection.sign != direction) presses = 0
        direction = signedDirection.sign
        lastPressMs = eventTimeMs
        presses++
        val step = when {
            presses <= 3 -> 10
            presses <= 8 -> 30
            else -> 60
        }
        return step * direction
    }

    private companion object {
        const val RESET_AFTER_MS = 1_000L

        /** Keys auto-repeat around 20 times per second, far faster than anyone can read a seek preview. */
        const val REPEATS_PER_STEP = 3
    }
}

/** Translates a remote key into what it means right now. Holds no state beyond seek acceleration. */
class RemoteKeyMapper(private val accelerator: SeekAccelerator = SeekAccelerator()) {

    fun map(keyCode: Int, repeatCount: Int, eventTimeMs: Long, context: KeyContext): PlayerCommand {
        val visible = context.isOverlayVisible
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER -> when {
                visible -> PlayerCommand.PassThrough
                context.isUnmuteHintVisible -> PlayerCommand.Unmute
                else -> PlayerCommand.TogglePlayPause
            }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> PlayerCommand.TogglePlayPause
            KeyEvent.KEYCODE_MEDIA_PLAY -> PlayerCommand.Play
            KeyEvent.KEYCODE_MEDIA_PAUSE -> PlayerCommand.Pause
            KeyEvent.KEYCODE_DPAD_LEFT -> dpadSeek(-1, repeatCount, eventTimeMs, context)
            KeyEvent.KEYCODE_DPAD_RIGHT -> dpadSeek(1, repeatCount, eventTimeMs, context)
            KeyEvent.KEYCODE_DPAD_UP ->
                if (visible) PlayerCommand.PassThrough else PlayerCommand.Reveal(RevealFocus.PROGRESS)
            KeyEvent.KEYCODE_DPAD_DOWN ->
                if (visible) PlayerCommand.PassThrough else PlayerCommand.Reveal(RevealFocus.PLAY_PAUSE)
            KeyEvent.KEYCODE_MEDIA_REWIND -> PlayerCommand.SeekBy(-MEDIA_SEEK_S)
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> PlayerCommand.SeekBy(MEDIA_SEEK_S)
            KeyEvent.KEYCODE_MEDIA_NEXT ->
                if (context.isSeries) PlayerCommand.NextEpisode else PlayerCommand.PassThrough
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> PlayerCommand.PreviousOrRestart
            in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 ->
                PlayerCommand.JumpToPercent((keyCode - KeyEvent.KEYCODE_0) * 10)
            KeyEvent.KEYCODE_MENU -> PlayerCommand.OpenSpeedSelector
            KeyEvent.KEYCODE_BACK -> if (visible) PlayerCommand.HideOverlay else PlayerCommand.ConfirmExit
            // Volume keys, including mute, belong to the system.
            else -> PlayerCommand.PassThrough
        }
    }

    private fun dpadSeek(direction: Int, repeatCount: Int, eventTimeMs: Long, context: KeyContext): PlayerCommand {
        if (context.isOverlayVisible && !context.isProgressFocused) return PlayerCommand.PassThrough
        return PlayerCommand.SeekBy(accelerator.next(direction, repeatCount, eventTimeMs))
    }

    private companion object {
        const val MEDIA_SEEK_S = 30
    }
}
