package dev.codexsoft.codexcine.ui.playback.web

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.http.SslError
import android.os.Message
import android.util.Log
import android.view.View
import android.webkit.CookieManager
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.SafeBrowsingResponse
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.graphics.createBitmap
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewFeature
import dev.codexsoft.codexcine.BuildConfig
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.ByteArrayInputStream

/** Which top-level pages the player may ever display. Everything else is refused. */
class MainFrameHostPolicy(private val allowedHosts: Set<String>) {

    fun isAllowed(url: String): Boolean {
        val parsed = url.toHttpUrlOrNull() ?: return false
        if (parsed.scheme != "https") return false
        val host = parsed.host.lowercase()
        // A plain suffix test would also accept a host like evilvidzy.org.
        return allowedHosts.any { host == it || host.endsWith(".$it") }
    }

    companion object {
        const val PROVIDER_HOST = "vidzy.org"
    }
}

/** Only frames using HTTP(S) may load. This keeps `intent:` and `market:` links from a subframe out. */
internal fun isAllowedSubframeUrl(url: String): Boolean =
    url.startsWith("https://", ignoreCase = true) || url.startsWith("http://", ignoreCase = true)

/**
 * What the screen must react to. All calls arrive on the main thread, and name their WebView because
 * one being thrown away can still report something.
 */
interface WebPlayerEvents {
    fun onMainFramePageFinished(view: WebView)
    fun onPageFailed(view: WebView, failure: PlayerFailure)
    fun onRendererGone(view: WebView)
    fun onShowFullscreen(view: View, callback: WebChromeClient.CustomViewCallback)
    fun onHideFullscreen()
}

/**
 * Creates the WebView that shows third-party embed pages. Those pages are known for popups, redirects
 * and aggressive ads, so everything not needed to play a video is turned off or refused.
 */
class HardenedWebViewFactory(
    context: Context,
    private val assetLoader: WebViewAssetLoader?,
) {
    private val appContext = context.applicationContext

    private val hostPolicy = MainFrameHostPolicy(
        buildSet {
            add(MainFrameHostPolicy.PROVIDER_HOST)
            if (assetLoader != null) add(WebViewAssetLoader.DEFAULT_DOMAIN)
        },
    )

    private val blocklist by lazy {
        AdHostBlocklist.parse(
            appContext.assets.open(AdHostBlocklist.ASSET_NAME).bufferedReader().use { it.readLines() }.asSequence(),
        )
    }

    // The embed cannot work without JavaScript. The page gets no interface to native code.
    @SuppressLint("SetJavaScriptEnabled")
    fun create(events: WebPlayerEvents): WebView {
        val webView = WebView(appContext)
        webView.setBackgroundColor(Color.BLACK)
        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = false
            allowContentAccess = false
            setGeolocationEnabled(false)
            setSupportMultipleWindows(false)
            javaScriptCanOpenWindowsAutomatically = false
            setSupportZoom(false)
            builtInZoomControls = false
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            // Some hosts reject WebViews. The rest of the agent string stays truthful.
            userAgentString = WebSettings.getDefaultUserAgent(appContext).replace("; wv", "")
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.SAFE_BROWSING_ENABLE)) {
            WebSettingsCompat.setSafeBrowsingEnabled(webView.settings, true)
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }
        webView.webViewClient = HardenedClient(events)
        webView.webChromeClient = HardenedChromeClient(events)
        return webView
    }

    private inner class HardenedClient(private val events: WebPlayerEvents) : WebViewClient() {

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val url = request.url.toString()
            val isAllowed = if (request.isForMainFrame) hostPolicy.isAllowed(url) else isAllowedSubframeUrl(url)
            if (!isAllowed && BuildConfig.DEBUG) Log.d(TAG, "Blocked navigation to $url")
            return !isAllowed
        }

        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
            assetLoader?.shouldInterceptRequest(request.url)?.let { return it }
            if (!blocklist.matches(request.url.host)) return null
            return WebResourceResponse(
                "text/plain",
                "utf-8",
                204,
                "No Content",
                emptyMap(),
                ByteArrayInputStream(ByteArray(0)),
            )
        }

        override fun onPageFinished(view: WebView, url: String) {
            if (url != BLANK_PAGE) events.onMainFramePageFinished(view)
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (request.isForMainFrame) events.onPageFailed(view, PlayerFailure.PAGE_ERROR)
        }

        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
            handler.cancel()
            events.onPageFailed(view, PlayerFailure.SSL_ERROR)
        }

        // The default interstitial needs a pointer, which a TV remote does not have.
        override fun onSafeBrowsingHit(
            view: WebView,
            request: WebResourceRequest,
            threatType: Int,
            callback: SafeBrowsingResponse,
        ) {
            callback.backToSafety(false)
            if (request.isForMainFrame) events.onPageFailed(view, PlayerFailure.PAGE_ERROR)
        }

        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            Log.w(TAG, "Renderer gone, crashed: ${detail.didCrash()}")
            events.onRendererGone(view)
            return true
        }
    }

    private class HardenedChromeClient(private val events: WebPlayerEvents) : WebChromeClient() {

        private val transparentPoster: Bitmap by lazy { createBitmap(1, 1, Bitmap.Config.ARGB_8888) }

        override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message) = false

        override fun onJsAlert(view: WebView, url: String, message: String, result: JsResult): Boolean {
            result.cancel()
            return true
        }

        override fun onJsConfirm(view: WebView, url: String, message: String, result: JsResult): Boolean {
            result.cancel()
            return true
        }

        override fun onJsPrompt(
            view: WebView,
            url: String,
            message: String,
            defaultValue: String?,
            result: JsPromptResult,
        ): Boolean {
            result.cancel()
            return true
        }

        override fun onJsBeforeUnload(view: WebView, url: String, message: String, result: JsResult): Boolean {
            result.cancel()
            return true
        }

        override fun onPermissionRequest(request: PermissionRequest) {
            request.deny()
        }

        override fun onShowCustomView(view: View, callback: CustomViewCallback) {
            events.onShowFullscreen(view, callback)
        }

        override fun onHideCustomView() {
            events.onHideFullscreen()
        }

        override fun getDefaultVideoPoster(): Bitmap = transparentPoster
    }

    companion object {
        private const val TAG = "HardenedWebView"
        const val BLANK_PAGE = "about:blank"
    }
}
