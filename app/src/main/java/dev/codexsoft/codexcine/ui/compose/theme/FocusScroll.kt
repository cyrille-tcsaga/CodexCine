package dev.codexsoft.codexcine.ui.compose.theme

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec

/**
 * How a scrolling list follows the focus. On a TV, Compose's default scrolls the focused item to a pivot
 * (30 % of the list) at every focus change, even between two buttons that are both fully visible, which
 * made the details page jump when moving Left or Right. Here nothing moves while the focused item is
 * fully visible; when it is not, its leading edge goes to the pivot, so that rows and cards still land at
 * a steady position, as in Leanback.
 */
@OptIn(ExperimentalFoundationApi::class)
object FocusScrollSpec : BringIntoViewSpec {

    private const val PIVOT = 0.3f

    override val scrollAnimationSpec: AnimationSpec<Float> = tween(durationMillis = 250, easing = FastOutSlowInEasing)

    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val fullyVisible = offset >= 0f && offset + size <= containerSize
        if (fullyVisible) return 0f
        return offset - containerSize * PIVOT
    }
}
