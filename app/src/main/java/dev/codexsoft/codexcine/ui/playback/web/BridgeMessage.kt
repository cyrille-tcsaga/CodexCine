package dev.codexsoft.codexcine.ui.playback.web

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** A snapshot of the video element, as reported by the page. */
data class VideoState(
    val currentTime: Double,
    val duration: Double,
    val isPaused: Boolean,
    val isBuffering: Boolean,
    val rate: Double,
    val volume: Double,
    val isMuted: Boolean,
    val isEnded: Boolean,
    val bufferedEnd: Double,
)

/** Validated messages from the page. The page can only ever report state, never give instructions. */
sealed interface BridgeMessage {
    data object Ready : BridgeMessage
    data class State(val state: VideoState) : BridgeMessage
    data class Event(val name: String) : BridgeMessage
}

enum class PageCommandName(val wireName: String) {
    PLAY("play"),
    PAUSE("pause"),
    TOGGLE("toggle"),
    SEEK_TO("seekTo"),
    SEEK_BY("seekBy"),
    RATE("rate"),
    MUTE("mute"),
    UNMUTE("unmute"),
}

data class PageCommand(val name: PageCommandName, val value: Double? = null)

object BridgeProtocol {

    /** Below this, a video is an ad or a preview clip and is not what the user asked to watch. */
    const val MIN_MAIN_VIDEO_DURATION_S = 60.0

    val ALLOWED_RATES = listOf(0.75, 1.0, 1.25, 1.5)

    const val EVENT_PLAYING = "playing"
    const val EVENT_PAUSE = "pause"
    const val EVENT_WAITING = "waiting"
    const val EVENT_ENDED = "ended"
    const val EVENT_ERROR = "error"
    const val EVENT_LOADED_METADATA = "loadedmetadata"
    const val EVENT_MUTED_AUTOPLAY = "mutedAutoplay"

    private val KNOWN_EVENTS = setOf(
        EVENT_PLAYING,
        EVENT_PAUSE,
        EVENT_WAITING,
        EVENT_ENDED,
        EVENT_ERROR,
        EVENT_LOADED_METADATA,
        EVENT_MUTED_AUTOPLAY,
    )

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class Inbound(
        val n: String? = null,
        val t: String? = null,
        val ct: Double? = null,
        val d: Double? = null,
        val p: Boolean? = null,
        val b: Boolean? = null,
        val r: Double? = null,
        val v: Double? = null,
        val m: Boolean? = null,
        val e: Boolean? = null,
        val bf: Double? = null,
        val name: String? = null,
    )

    @Serializable
    private data class Outbound(val n: String, val cmd: String, val value: Double? = null)

    /** Returns null for anything that is not a well-formed message carrying the right nonce. */
    fun parse(raw: String?, nonce: String): BridgeMessage? {
        if (raw == null || raw.length > MAX_MESSAGE_LENGTH) return null
        val message = try {
            json.decodeFromString<Inbound>(raw)
        } catch (_: SerializationException) {
            return null
        } catch (_: IllegalArgumentException) {
            return null
        }
        if (message.n != nonce) return null
        return when (message.t) {
            "ready" -> BridgeMessage.Ready
            "state" -> message.toState()?.let { BridgeMessage.State(it) }
            "event" -> message.name?.takeIf { it in KNOWN_EVENTS }?.let { BridgeMessage.Event(it) }
            else -> null
        }
    }

    private fun Inbound.toState(): VideoState? {
        val numbers = listOf(ct, d, r, v, bf ?: 0.0)
        if (numbers.any { it == null || !it.isFinite() || it < 0.0 }) return null
        return VideoState(
            currentTime = ct ?: return null,
            duration = d ?: return null,
            isPaused = p ?: return null,
            isBuffering = b ?: false,
            rate = r ?: return null,
            volume = v ?: return null,
            isMuted = m ?: false,
            isEnded = e ?: false,
            bufferedEnd = bf ?: 0.0,
        )
    }

    /** Returns null for a command that must not be sent, such as a rate outside the whitelist. */
    fun encode(command: PageCommand, nonce: String): String? {
        val value = command.value
        if (value != null && !value.isFinite()) return null
        if (command.name == PageCommandName.RATE && value !in ALLOWED_RATES) return null
        return json.encodeToString(Outbound(nonce, command.name.wireName, value))
    }

    private const val MAX_MESSAGE_LENGTH = 1024
}

/**
 * Chooses which frame owns the video being watched. Generic over the frame key so that the rules can
 * be tested without a WebView.
 */
class ActiveFrameTracker<K : Any>(private val clock: () -> Long) {

    private class Frame(var lastStateMs: Long, var duration: Double = 0.0, var isIdle: Boolean = true)

    private val frames = HashMap<K, Frame>()
    var activeFrame: K? = null
        private set

    /** Returns true when this state comes from the active frame and should be acted upon. */
    fun onState(key: K, state: VideoState): Boolean {
        val now = clock()
        val frame = frames[key]
        if (frame != null && now - frame.lastStateMs < MIN_STATE_INTERVAL_MS) return false
        val updated = frame ?: Frame(now).also { frames[key] = it }
        updated.lastStateMs = now
        updated.duration = state.duration
        updated.isIdle = state.isPaused || state.isEnded

        if (state.duration < BridgeProtocol.MIN_MAIN_VIDEO_DURATION_S) return false
        val active = activeFrame?.let { frames[it] }
        val shouldSwitch = active == null ||
            now - active.lastStateMs > STALE_FRAME_MS ||
            (active.isIdle && state.duration > active.duration)
        if (shouldSwitch) activeFrame = key
        return activeFrame == key
    }

    fun isActive(key: K) = activeFrame == key

    fun reset() {
        frames.clear()
        activeFrame = null
    }

    private companion object {
        const val MIN_STATE_INTERVAL_MS = 250L

        /** A reloaded frame gets a new key and the old one falls silent, so it must not stay active. */
        const val STALE_FRAME_MS = 5_000L
    }
}
