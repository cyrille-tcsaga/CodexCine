package dev.codexsoft.codexcine.ui.compose.theme

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import dev.codexsoft.codexcine.R

/** The colours that are not part of the Material scheme, read from the same resources as the XML screens. */
class CodexColors(
    val night: Color,
    val ink: Color,
    val amber: Color,
    val ivory: Color,
    val mist: Color,
    val card: Color,
    val badgeStroke: Color,
    val pill: Color,
    val scrimStrong: Color,
    val scrimSoft: Color,
    val dialogHighlight: Color,
)

val LocalCodexColors = staticCompositionLocalOf<CodexColors> { error("CodexCineTheme is missing") }

/** Shortcut for the brand colours: `CodexTheme.colors.amber`. */
object CodexTheme {
    val colors: CodexColors
        @Composable get() = LocalCodexColors.current
    val dimens: CodexDimens
        get() = CodexDimens
}

/**
 * The app's theme in Compose. Colours come from `res/values/colors.xml` and fonts from `res/font`, so a
 * change to the brand reaches both the Leanback screens and the Compose ones while they coexist.
 */
@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CodexCineTheme(content: @Composable () -> Unit) {
    val colors = CodexColors(
        night = colorResource(R.color.brand_night),
        ink = colorResource(R.color.brand_ink),
        amber = colorResource(R.color.brand_amber),
        ivory = colorResource(R.color.brand_ivory),
        mist = colorResource(R.color.brand_mist),
        card = colorResource(R.color.card_background),
        badgeStroke = colorResource(R.color.badge_stroke),
        pill = colorResource(R.color.pill_background),
        scrimStrong = colorResource(R.color.backdrop_scrim_strong),
        scrimSoft = colorResource(R.color.backdrop_scrim_soft),
        dialogHighlight = colorResource(R.color.dialog_highlight),
    )
    val scheme = darkColorScheme(
        primary = colors.amber,
        onPrimary = colors.night,
        secondary = colors.ivory,
        onSecondary = colors.night,
        background = colors.night,
        onBackground = colors.ivory,
        surface = colors.ink,
        onSurface = colors.ivory,
        surfaceVariant = colors.card,
        onSurfaceVariant = colors.mist,
        border = colors.badgeStroke,
    )
    CompositionLocalProvider(LocalCodexColors provides colors) {
        MaterialTheme(colorScheme = scheme, typography = codexTypography()) {
            // Text outside a Surface would otherwise be black on the night background.
            CompositionLocalProvider(
                LocalContentColor provides colors.ivory,
                LocalBringIntoViewSpec provides FocusScrollSpec,
                content = content,
            )
        }
    }
}
