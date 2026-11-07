package dev.codexsoft.codexcine.ui.common

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import dev.codexsoft.codexcine.R

/** Drawn over a card's image (see BaseCardPresenter.setImageOverlay), as ImageCardView has no room for views. */
abstract class CardOverlay : Drawable() {
    override fun setAlpha(alpha: Int) = Unit

    override fun setColorFilter(colorFilter: ColorFilter?) = Unit

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

/** How far the viewer got, as a thin bar along the bottom of the image. */
class ProgressBarOverlay(context: Context, private val fraction: Float) : CardOverlay() {
    private val heightPx = BAR_HEIGHT_DP * context.resources.displayMetrics.density
    private val barPaint = Paint().apply { color = ContextCompat.getColor(context, R.color.selected_background) }
    private val trackPaint = Paint().apply { color = ContextCompat.getColor(context, R.color.player_track) }

    override fun draw(canvas: Canvas) {
        val box = bounds
        val top = box.bottom - heightPx
        val end = box.left + box.width() * fraction
        canvas.drawRect(end, top, box.right.toFloat(), box.bottom.toFloat(), trackPaint)
        canvas.drawRect(box.left.toFloat(), top, end, box.bottom.toFloat(), barPaint)
    }

    private companion object {
        const val BAR_HEIGHT_DP = 5
    }
}

/** A watched episode: the image dimmed, with an ivory disc and a check in the top corner. */
class WatchedOverlay(context: Context) : CardOverlay() {
    private val density = context.resources.displayMetrics.density
    private val dimPaint = Paint().apply { color = ContextCompat.getColor(context, R.color.backdrop_scrim_soft) }
    private val discPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.brand_ivory)
    }
    private val check: Drawable? = ContextCompat.getDrawable(context, R.drawable.ic_check)?.mutate()?.apply {
        setTint(ContextCompat.getColor(context, R.color.brand_night))
    }

    override fun draw(canvas: Canvas) {
        val box = bounds
        canvas.drawRect(box, dimPaint)
        val radius = DISC_DP * density / 2
        val margin = MARGIN_DP * density
        val cx = box.right - margin - radius
        val cy = box.top + margin + radius
        canvas.drawCircle(cx, cy, radius, discPaint)
        val half = (radius * CHECK_SCALE).toInt()
        check?.setBounds(cx.toInt() - half, cy.toInt() - half, cx.toInt() + half, cy.toInt() + half)
        check?.draw(canvas)
    }

    private companion object {
        const val DISC_DP = 32
        const val MARGIN_DP = 10
        const val CHECK_SCALE = 0.7f
    }
}
