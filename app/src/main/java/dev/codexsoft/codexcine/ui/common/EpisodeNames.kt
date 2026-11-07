package dev.codexsoft.codexcine.ui.common

import dev.codexsoft.codexcine.domain.model.Episode

// Accented capitals are listed: case-insensitive matching covers ASCII only on the JVM.
private val GENERIC_NAME = Regex("""(?i)[ÉéEe]pisode\s*\d+""")

/** The episode's title, or null when it has none worth showing: TMDB sends "Épisode 5" when it lacks one. */
fun Episode.meaningfulName(): String? = name.trim().takeIf { it.isNotEmpty() && !GENERIC_NAME.matches(it) }
