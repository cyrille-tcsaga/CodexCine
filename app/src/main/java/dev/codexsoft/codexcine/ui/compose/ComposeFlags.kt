package dev.codexsoft.codexcine.ui.compose

import android.content.Context
import androidx.core.content.edit
import dev.codexsoft.codexcine.BuildConfig

/** The screens being moved from Leanback to Compose, one flag each (see doc/USER_STORIES_MIGRATION_COMPOSE_TV.md). */
enum class ComposeScreen {
    ERROR, ABOUT, PERSON, SEASON, GRID, SEARCH, DETAILS, HOME, PLAYER_OVERLAY, PLAYER_DIRECT,
}

/**
 * Chooses, per screen, the Compose implementation or the Leanback one. Leanback is the default until a
 * screen has passed its checklist. The default comes from the `composeScreens` Gradle property; a debug
 * build can override it at run time (see the debug-only receiver) to test both sides without rebuilding.
 */
object ComposeFlags {

    private const val PREFS = "compose_flags"
    private const val KEY_SCREENS = "screens"

    fun isEnabled(context: Context, screen: ComposeScreen): Boolean {
        val stored = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_SCREENS, null)
        return parse(stored ?: BuildConfig.COMPOSE_SCREENS).contains(screen)
    }

    /** Debug use: null removes the override. */
    fun override(context: Context, screens: String?) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            if (screens == null) remove(KEY_SCREENS) else putString(KEY_SCREENS, screens)
        }
    }

    /** "all", "none" or names separated by commas. Unknown names are ignored. */
    fun parse(value: String): Set<ComposeScreen> {
        val names = value.split(',').map { it.trim().uppercase() }.filter { it.isNotEmpty() }
        if ("ALL" in names) return ComposeScreen.entries.toSet()
        return names.mapNotNull { name -> ComposeScreen.entries.firstOrNull { it.name == name } }.toSet()
    }
}
