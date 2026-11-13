package dev.codexsoft.codexcine.ui.browse

import android.app.AlertDialog
import androidx.fragment.app.Fragment
import dev.codexsoft.codexcine.R
import dev.codexsoft.codexcine.data.progress.WatchProgress

/**
 * The menu of a long press on a "Continuer à regarder" card: resume, start over (the saved position stays
 * until the next save), or take the title out of the row. Shared by the Leanback and the Compose home.
 */
class ContinueWatchingMenu(
    private val fragment: Fragment,
    private val onRemove: (WatchProgress) -> Unit,
) {
    private enum class Action(val labelRes: Int) {
        RESUME(R.string.continue_menu_resume),
        RESTART(R.string.continue_menu_restart),
        REMOVE(R.string.continue_menu_remove),
    }

    private var dialog: AlertDialog? = null

    fun show(progress: WatchProgress) {
        if (fragment.view == null) return
        val actions = Action.entries
        val title = if (progress.episode > 0) {
            fragment.getString(R.string.continue_menu_title_episode, progress.title, progress.season, progress.episode)
        } else {
            progress.title
        }
        dialog?.dismiss()
        dialog = AlertDialog.Builder(fragment.requireContext(), R.style.CodexCine_Dialog)
            .setTitle(title)
            .setItems(actions.map { fragment.getString(it.labelRes) }.toTypedArray()) { _, which ->
                when (actions[which]) {
                    Action.RESUME -> fragment.playProgress(progress, autoResume = true)
                    Action.RESTART -> fragment.playProgress(progress, autoResume = false, startOver = true)
                    Action.REMOVE -> onRemove(progress)
                }
            }
            .show()
    }

    fun dismiss() {
        dialog?.dismiss()
        dialog = null
    }
}
