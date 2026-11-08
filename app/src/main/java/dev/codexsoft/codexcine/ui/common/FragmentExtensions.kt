package dev.codexsoft.codexcine.ui.common

import android.app.Activity
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dev.codexsoft.codexcine.CodexCineApp
import dev.codexsoft.codexcine.R
import dev.codexsoft.codexcine.core.ErrorKind
import dev.codexsoft.codexcine.di.AppContainer
import dev.codexsoft.codexcine.ui.error.ErrorFragment
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

val Fragment.appContainer: AppContainer
    get() = requireActivity().appContainer

val Activity.appContainer: AppContainer
    get() = (application as CodexCineApp).container

/** Collects a flow on the main thread while this fragment's view is at least started. */
fun <T> Fragment.collectWhileStarted(flow: Flow<T>, collector: (T) -> Unit) {
    viewLifecycleOwner.lifecycleScope.launch {
        viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            flow.collect { collector(it) }
        }
    }
}

/** Replaces the current screen with an error. A rejected token cannot be fixed by retrying. */
fun Fragment.showErrorScreen(kind: ErrorKind) {
    val message = getString(
        when (kind) {
            ErrorKind.INVALID_TOKEN -> R.string.error_invalid_token
            ErrorKind.NO_CONNECTION -> R.string.error_no_connection
            ErrorKind.GENERIC -> R.string.error_generic
        },
    )
    showErrorScreen(message, retryable = kind != ErrorKind.INVALID_TOKEN)
}

fun Fragment.showErrorScreen(message: String, retryable: Boolean, title: String? = null) {
    val fragment = ErrorFragment.newInstance(message, retryable, title)
    (activity as? FragmentHostActivity)?.showError(fragment)
}
