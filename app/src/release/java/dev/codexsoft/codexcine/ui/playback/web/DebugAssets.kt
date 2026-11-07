package dev.codexsoft.codexcine.ui.playback.web

import android.content.Context
import androidx.webkit.WebViewAssetLoader

/** Release builds have no test pages, and no reason to allow their host. */
internal fun debugAssetLoader(context: Context): WebViewAssetLoader? = null
