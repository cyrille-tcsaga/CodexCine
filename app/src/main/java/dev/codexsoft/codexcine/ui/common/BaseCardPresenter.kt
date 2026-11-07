package dev.codexsoft.codexcine.ui.common

import android.content.Context
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.leanback.widget.ImageCardView
import androidx.leanback.widget.Presenter
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DecodeFormat
import dev.codexsoft.codexcine.R
import kotlin.math.roundToInt

/**
 * Every card in the app: an [ImageCardView] of a fixed size whose image has rounded corners, with its
 * title and details underneath, straight on the page. The selected card gets an ivory ring around
 * its image, on top of a slight zoom (see CardRows.kt). A [circular] card (a person) has a round
 * image, with its name centred underneath.
 */
abstract class BaseCardPresenter(
    private val widthDp: Int,
    private val heightDp: Int,
    private val circular: Boolean = false,
) : Presenter() {

    private val focusRing = if (circular) R.drawable.card_focus_ring_circle else R.drawable.card_focus_ring
    private var widthPx = 0
    private var heightPx = 0

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val context = parent.context
        val cornerRadius = context.resources.getDimension(R.dimen.card_corner_radius)
        widthPx = context.dpToPx(widthDp)
        heightPx = context.dpToPx(heightDp)

        val card = object : ImageCardView(context) {
            // BaseCardView filters focus and selection out of its drawable state, so a state list
            // foreground would never change: the ring is swapped in here instead.
            override fun setSelected(isSelected: Boolean) {
                super.setSelected(isSelected)
                refreshImageForeground()
            }
        }
        card.isFocusable = true
        card.isFocusableInTouchMode = true
        card.setBackgroundColor(Color.TRANSPARENT)
        card.setInfoAreaBackgroundColor(Color.TRANSPARENT)
        card.setMainImageDimensions(widthPx, heightPx)
        card.mainImageView?.apply {
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    if (circular) {
                        outline.setOval(0, 0, view.width, view.height)
                    } else {
                        outline.setRoundRect(0, 0, view.width, view.height, cornerRadius)
                    }
                }
            }
            clipToOutline = true
        }
        if (circular) {
            listOf(androidx.leanback.R.id.title_text, androidx.leanback.R.id.content_text).forEach { id ->
                card.findViewById<TextView>(id)?.apply {
                    layoutParams = layoutParams.apply { width = ViewGroup.LayoutParams.MATCH_PARENT }
                    gravity = Gravity.CENTER_HORIZONTAL
                }
            }
        }
        return ViewHolder(card)
    }

    /** Draws [overlay] (a progress bar, say) over the image, below the focus ring. */
    protected fun ImageCardView.setImageOverlay(overlay: Drawable?) {
        setTag(R.id.card_image_overlay, overlay)
        refreshImageForeground()
    }

    private fun ImageCardView.refreshImageForeground() {
        val image = mainImageView ?: return
        val layers = listOfNotNull(
            getTag(R.id.card_image_overlay) as? Drawable,
            if (isSelected) ContextCompat.getDrawable(context, focusRing) else null,
        )
        image.foreground = when (layers.size) {
            0 -> null
            1 -> layers.single()
            else -> LayerDrawable(layers.toTypedArray())
        }
    }

    /** Loads a remote image at exactly the card size. A null URL shows the error drawable. */
    protected fun ImageCardView.loadImage(url: String?) {
        // onUnbindViewHolder hides the image view through setMainImage(null). Glide writes straight
        // to the ImageView and would never show it again, so a recycled card would stay blank.
        setMainImage(ContextCompat.getDrawable(context, R.drawable.card_placeholder), false)
        if (url == null) {
            setMainImage(ContextCompat.getDrawable(context, R.drawable.card_error), false)
            return
        }
        val target = mainImageView ?: return
        Glide.with(context)
            .load(url)
            .override(widthPx, heightPx)
            .centerCrop()
            .format(DecodeFormat.PREFER_RGB_565)
            .placeholder(R.drawable.card_placeholder)
            .error(R.drawable.card_error)
            .into(target)
    }

    protected fun ImageCardView.showDrawable(@DrawableRes drawable: Int) {
        setMainImage(ContextCompat.getDrawable(context, drawable), false)
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        val card = viewHolder.view as ImageCardView
        // Leanback recycles cards while the activity is being destroyed, and Glide.with(activity) throws
        // by then. clear() finds the request whichever manager owns it, so the app context is enough.
        card.mainImageView?.let { Glide.with(card.context.applicationContext).clear(it) }
        card.mainImage = null
        card.setImageOverlay(null)
    }

    private fun Context.dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).roundToInt()
}
