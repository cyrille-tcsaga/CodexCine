package dev.codexsoft.codexcine.ui.playback.web

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Bundle
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import dev.codexsoft.codexcine.R

/** The seek bar: a track, the buffered part, the played part and a thumb that grows with focus. */
class PlayerProgressView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    /** Called when an accessibility service asks to scroll: +1 forward, -1 backward. */
    var onAccessibilitySeek: ((Int) -> Unit)? = null

    private val density = resources.displayMetrics.density
    private val trackHeight = 6 * density
    private val thumbRadius = 8 * density
    private val focusedThumbRadius = 11 * density

    private val trackPaint = fillPaint(R.color.player_track)
    private val bufferedPaint = fillPaint(R.color.player_buffered)
    private val playedPaint = fillPaint(R.color.selected_background)
    private val thumbPaint = fillPaint(R.color.selected_background)
    private val focusRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3 * density
        color = ContextCompat.getColor(context, android.R.color.white)
    }

    private var position = 0.0
    private var buffered = 0.0
    private var duration = 0.0

    init {
        isFocusable = true
        isClickable = true
        ViewCompat.setAccessibilityDelegate(this, ProgressAccessibility())
    }

    fun setProgress(positionSeconds: Double, bufferedSeconds: Double, durationSeconds: Double) {
        if (position == positionSeconds && buffered == bufferedSeconds && duration == durationSeconds) return
        position = positionSeconds
        buffered = bufferedSeconds
        duration = durationSeconds
        invalidate()
    }

    /** The horizontal centre of the thumb at [seconds], in this view's coordinates. */
    fun xFor(seconds: Double): Float {
        val inset = focusedThumbRadius
        val fraction = if (duration > 0) (seconds / duration).coerceIn(0.0, 1.0) else 0.0
        return inset + (width - 2 * inset) * fraction.toFloat()
    }

    override fun onDraw(canvas: Canvas) {
        if (duration <= 0) return
        val centerY = height / 2f
        val top = centerY - trackHeight / 2
        val bottom = centerY + trackHeight / 2
        val start = xFor(0.0)
        val end = xFor(duration)
        canvas.drawRoundRect(start, top, end, bottom, trackHeight, trackHeight, trackPaint)
        canvas.drawRect(start, top, xFor(buffered), bottom, bufferedPaint)
        canvas.drawRect(start, top, xFor(position), bottom, playedPaint)

        val thumbX = xFor(position)
        if (isFocused) {
            canvas.drawCircle(thumbX, centerY, focusedThumbRadius, thumbPaint)
            canvas.drawCircle(thumbX, centerY, focusedThumbRadius, focusRingPaint)
        } else {
            canvas.drawCircle(thumbX, centerY, thumbRadius, thumbPaint)
        }
    }

    private fun fillPaint(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = ContextCompat.getColor(context, color)
    }

    private inner class ProgressAccessibility : AccessibilityDelegateCompat() {
        override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
            super.onInitializeAccessibilityNodeInfo(host, info)
            info.className = "android.widget.SeekBar"
            info.rangeInfo = AccessibilityNodeInfoCompat.RangeInfoCompat.obtain(
                AccessibilityNodeInfoCompat.RangeInfoCompat.RANGE_TYPE_FLOAT,
                0f,
                duration.toFloat(),
                position.toFloat(),
            )
            info.addAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_SCROLL_FORWARD)
            info.addAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_SCROLL_BACKWARD)
        }

        override fun performAccessibilityAction(host: View, action: Int, args: Bundle?): Boolean = when (action) {
            AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD -> {
                onAccessibilitySeek?.invoke(1)
                true
            }
            AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD -> {
                onAccessibilitySeek?.invoke(-1)
                true
            }
            else -> super.performAccessibilityAction(host, action, args)
        }
    }
}
