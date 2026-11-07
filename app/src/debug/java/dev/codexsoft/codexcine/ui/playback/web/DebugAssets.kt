package dev.codexsoft.codexcine.ui.playback.web

import android.content.Context
import androidx.webkit.WebViewAssetLoader

/** Serves the test pages in `src/debug/assets/test` over HTTPS, as the bridge expects of a real host. */
internal fun debugAssetLoader(context: Context): WebViewAssetLoader? =
    WebViewAssetLoader.Builder()
        .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context.applicationContext))
        .build()
