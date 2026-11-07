package dev.codexsoft.codexcine.ui.playback.web

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.webkit.WebView
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import dev.codexsoft.codexcine.BuildConfig
import java.util.UUID

/**
 * The only link between the app and the page, which runs in frames we do not control.
 *
 * Injects `player_bridge.js` into every frame and listens for its messages. What protects the app is
 * not the origin rule, which is `*` because the host chain is unknown, but the fact that messages are
 * schema-checked and can only ever report video state. See DECISIONS.md.
 *
 * Lives exactly as long as its WebView, so it has no way to detach: removing the listener or the
 * script while a page is running makes Chromium crash the whole process some time later.
 */
// Lint cannot see the check: this class is only ever created once isSupported() has returned true.
@SuppressLint("RequiresFeature")
class PlayerBridge(
    context: Context,
    webView: WebView,
    private val onMessage: (BridgeMessage) -> Unit,
) {
    private val nonce = UUID.randomUUID().toString()
    private val tracker = ActiveFrameTracker<JavaScriptReplyProxy>(System::currentTimeMillis)

    init {
        val script = context.applicationContext.assets.open(SCRIPT_ASSET).bufferedReader().use { it.readText() }
        WebViewCompat.addWebMessageListener(webView, OBJECT_NAME, ORIGIN_RULES) { _, message, _, _, proxy ->
            onPageMessage(message, proxy)
        }
        val prepared = script
            .replace(NONCE_PLACEHOLDER, nonce)
            .replace(DIAGNOSTICS_PLACEHOLDER, BuildConfig.DEBUG.toString())
        WebViewCompat.addDocumentStartJavaScript(webView, prepared, ORIGIN_RULES)
    }

    fun send(command: PageCommand) {
        val proxy = tracker.activeFrame ?: return
        val encoded = BridgeProtocol.encode(command, nonce) ?: return
        Log.d(TAG, "Command: ${command.name} ${command.value ?: ""}")
        proxy.postMessage(encoded)
    }

    private fun onPageMessage(raw: WebMessageCompat, proxy: JavaScriptReplyProxy) {
        when (val message = BridgeProtocol.parse(raw.data, nonce)) {
            null -> Log.d(TAG, "Dropped an invalid message")
            BridgeMessage.Ready -> Log.d(TAG, "A frame is ready")
            is BridgeMessage.Event -> if (tracker.isActive(proxy)) {
                Log.d(TAG, "Event: ${message.name}")
                onMessage(message)
            }
            is BridgeMessage.State -> {
                val wasActive = tracker.activeFrame
                if (tracker.onState(proxy, message.state)) {
                    if (wasActive !== proxy) Log.i(TAG, "Active video frame set, duration ${message.state.duration}")
                    onMessage(message)
                }
            }
        }
    }

    companion object {
        private const val TAG = "CodexBridge"
        private const val OBJECT_NAME = "CodexBridge"
        private const val SCRIPT_ASSET = "player_bridge.js"
        private const val NONCE_PLACEHOLDER = "__NONCE__"
        private const val DIAGNOSTICS_PLACEHOLDER = "__DIAGNOSTICS__"
        private val ORIGIN_RULES = setOf("*")

        /** False on an outdated WebView, where the player falls back to basic mode. */
        fun isSupported(): Boolean =
            WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER) &&
                WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
    }
}
