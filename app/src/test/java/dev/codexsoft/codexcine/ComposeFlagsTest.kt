package dev.codexsoft.codexcine

import dev.codexsoft.codexcine.ui.compose.ComposeFlags
import dev.codexsoft.codexcine.ui.compose.ComposeScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ComposeFlagsTest {

    @Test
    fun `empty means Leanback everywhere`() {
        assertTrue(ComposeFlags.parse("").isEmpty())
        assertTrue(ComposeFlags.parse("none").isEmpty())
    }

    @Test
    fun `names are case and space insensitive, unknown ones are ignored`() {
        assertEquals(
            setOf(ComposeScreen.DETAILS, ComposeScreen.ERROR),
            ComposeFlags.parse(" details , Error,nope"),
        )
    }

    @Test
    fun `all switches every screen`() {
        assertEquals(ComposeScreen.entries.toSet(), ComposeFlags.parse("all"))
    }
}
