package dev.codexsoft.codexcine.ui.compose

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import dev.codexsoft.codexcine.domain.model.CategoryType
import dev.codexsoft.codexcine.domain.model.MediaType
import dev.codexsoft.codexcine.ui.details.DetailsActivity
import dev.codexsoft.codexcine.ui.error.AboutActivity
import dev.codexsoft.codexcine.ui.grid.GridActivity
import dev.codexsoft.codexcine.ui.person.PersonActivity
import dev.codexsoft.codexcine.ui.search.SearchActivity
import dev.codexsoft.codexcine.ui.season.SeasonActivity

/**
 * Debug only. Opens any screen from adb, including the ones that are not exported:
 * `adb shell am start -n dev.codexsoft.codexcine/.ui.compose.DebugLaunchActivity --es screen season --ei id 1399 --ei n 1`
 * Screens: details (`type` movie|tv, `id`), season (`id`, `n`), grid (`category`), person (`id`), search, about, host.
 */
class DebugLaunchActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getIntExtra("id", 550)
        val title = intent.getStringExtra("title") ?: "Debug"
        val target: Intent? = when (intent.getStringExtra("screen")) {
            "details" -> DetailsActivity.newIntent(
                this, id, MediaType.fromValue(intent.getStringExtra("type")) ?: MediaType.MOVIE, title,
            )
            "season" -> SeasonActivity.newIntent(this, id, intent.getIntExtra("n", 1), title)
            "grid" -> GridActivity.newIntent(
                this, CategoryType.valueOf(intent.getStringExtra("category") ?: "POPULAR_MOVIES"), title,
            )
            "person" -> PersonActivity.newIntent(this, id, title)
            "search" -> Intent(this, SearchActivity::class.java)
            "about" -> Intent(this, AboutActivity::class.java)
            "host" -> Intent(this, ComposeHostDemoActivity::class.java)
            else -> null
        }
        target?.let(::startActivity)
        finish()
    }
}
