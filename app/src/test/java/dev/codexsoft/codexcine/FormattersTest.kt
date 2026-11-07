package dev.codexsoft.codexcine

import dev.codexsoft.codexcine.core.Formatters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FormattersTest {

    // Same patterns as res/values/strings.xml.
    private val patterns = Formatters.RuntimePatterns(
        hoursAndMinutes = "%1\$d h %2\$d min",
        hoursOnly = "%1\$d h",
        minutesOnly = "%1\$d min",
    )

    @Test
    fun `hours and minutes`() {
        assertEquals("2 h 15 min", Formatters.runtime(135, patterns))
    }

    @Test
    fun `minutes only`() {
        assertEquals("45 min", Formatters.runtime(45, patterns))
    }

    @Test
    fun `whole hours`() {
        assertEquals("2 h", Formatters.runtime(120, patterns))
    }

    @Test
    fun `unknown or zero runtime gives null`() {
        assertNull(Formatters.runtime(null, patterns))
        assertNull(Formatters.runtime(0, patterns))
    }

    @Test
    fun `rating has one decimal with a dot`() {
        assertEquals("7.8", Formatters.rating(7.81))
    }

    @Test
    fun `date is written in french`() {
        assertEquals("15 mars 2024", Formatters.date("2024-03-15"))
    }

    @Test
    fun `malformed or absent date gives null`() {
        assertNull(Formatters.date("bientôt"))
        assertNull(Formatters.date(""))
        assertNull(Formatters.date(null))
    }
}
