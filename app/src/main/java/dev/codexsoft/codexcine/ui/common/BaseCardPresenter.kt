package dev.codexsoft.codexcine.ui.common

import android.content.Context
import android.view.ViewGroup
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.leanback.widget.ImageCardView
import androidx.leanback.widget.Presenter
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DecodeFormat
import dev.codexsoft.codexcine.R
import kotlin.math.roundToInt

/** Every card in the app: an [ImageCardView] of a fixed size that changes colour when selected. */
abstract class BaseCardPresenter(
    private val widthDp: Int,
    private val heightDp: Int,
    @param:ColorRes private val defaultColor: Int = R.color.default_background,
) : Presenter() {

    private var widthPx = 0
    private var heightPx = 0

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val context = parent.context
        val unselected = ContextCompat.getColor(context, defaultColor)
        val selected = ContextCompat.getColor(context, R.color.selected_background)
        widthPx = context.dpToPx(widthDp)
        heightPx = context.dpToPx(heightDp)

        val card = object : ImageCardView(context) {
            override fun setSelected(isSelected: Boolean) {
                applyColor(if (isSelected) selected else unselected)
                super.setSelected(isSelected)
            }

            private fun applyColor(color: Int) {
                setBackgroundColor(color)
                setInfoAreaBackgroundColor(color)
            }
        }
        card.isFocusable = true
        card.isFocusableInTouchMode = true
        card.setBackgroundColor(unselected)
        card.setInfoAreaBackgroundColor(unselected)
        card.setMainImageDimensions(widthPx, heightPx)
        return ViewHolder(card)
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
    }

    private fun Context.dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).roundToInt()
}
