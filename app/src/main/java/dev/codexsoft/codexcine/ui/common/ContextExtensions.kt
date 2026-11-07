package dev.codexsoft.codexcine.ui.common

import android.content.Context
import dev.codexsoft.codexcine.R
import dev.codexsoft.codexcine.core.Formatters

/** Formats a runtime such as `2 h 15 min` with the French patterns from the resources. */
fun Context.runtimeText(minutes: Int?): String? = Formatters.runtime(
    minutes,
    Formatters.RuntimePatterns(
        hoursAndMinutes = getString(R.string.runtime_hours_minutes),
        hoursOnly = getString(R.string.runtime_hours),
        minutesOnly = getString(R.string.runtime_minutes),
    ),
)
