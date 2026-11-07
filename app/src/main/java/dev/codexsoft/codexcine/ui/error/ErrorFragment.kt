package dev.codexsoft.codexcine.ui.error

import android.os.Bundle
import android.view.View
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.leanback.app.ErrorSupportFragment
import dev.codexsoft.codexcine.R
import dev.codexsoft.codexcine.ui.common.FragmentHostActivity

/**
 * Full-screen message with a single button. Everything lives in the arguments, so the screen
 * survives a process restart. The button either retries the host's content or closes the activity.
 */
class ErrorFragment : ErrorSupportFragment() {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val args = requireArguments()
        val retryable = args.getBoolean(ARG_RETRYABLE)

        title = args.getString(ARG_TITLE) ?: getString(R.string.app_name)
        message = args.getString(ARG_MESSAGE)
        imageDrawable = ContextCompat.getDrawable(requireContext(), args.getInt(ARG_IMAGE))
        setButtonText(getString(if (retryable) R.string.retry else R.string.back))
        setButtonClickListener {
            val host = activity
            if (retryable && host is FragmentHostActivity) host.showContent() else host?.finish()
        }
    }

    companion object {
        private const val ARG_TITLE = "title"
        private const val ARG_MESSAGE = "message"
        private const val ARG_RETRYABLE = "retryable"
        private const val ARG_IMAGE = "image"

        fun newInstance(
            message: String,
            retryable: Boolean,
            title: String? = null,
            @DrawableRes image: Int = R.drawable.ic_error,
        ) = ErrorFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_TITLE, title)
                putString(ARG_MESSAGE, message)
                putBoolean(ARG_RETRYABLE, retryable)
                putInt(ARG_IMAGE, image)
            }
        }
    }
}
