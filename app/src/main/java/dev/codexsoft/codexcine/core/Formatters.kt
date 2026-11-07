package dev.codexsoft.codexcine.core

import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Pure formatting helpers. Anything the user reads comes from string resources, so the patterns are
 * passed in rather than hardcoded here.
 */
object Formatters {

    /** The UI is French on every device, whatever the system locale is. */
    private val FRENCH: Locale = Locale.FRANCE

    data class RuntimePatterns(
        val hoursAndMinutes: String,
        val hoursOnly: String,
        val minutesOnly: String,
    )

    fun runtime(minutes: Int?, patterns: RuntimePatterns): String? {
        if (minutes == null || minutes <= 0) return null
        val hours = minutes / 60
        val rest = minutes % 60
        return when {
            hours > 0 && rest > 0 -> String.format(FRENCH, patterns.hoursAndMinutes, hours, rest)
            hours > 0 -> String.format(FRENCH, patterns.hoursOnly, hours)
            else -> String.format(FRENCH, patterns.minutesOnly, rest)
        }
    }

    /** Formats a TMDB average with one decimal, for example `7.8`. */
    fun rating(voteAverage: Double): String = String.format(Locale.ROOT, "%.1f", voteAverage)

    /** Groups thousands the French way, for example `1 234`. */
    fun count(value: Int): String = NumberFormat.getIntegerInstance(FRENCH).format(value)

    /** Turns TMDB's `2024-03-15` into `15 mars 2024`. Null when the date is absent or malformed. */
    fun date(isoDate: String?): String? {
        if (isoDate.isNullOrBlank()) return null
        return try {
            LocalDate.parse(isoDate).format(
                DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(FRENCH)
            )
        } catch (_: DateTimeParseException) {
            null
        }
    }
}
