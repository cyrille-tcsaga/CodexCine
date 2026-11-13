package dev.codexsoft.codexcine

import dev.codexsoft.codexcine.ui.compose.components.DpadPressTracker
import dev.codexsoft.codexcine.ui.compose.components.DpadPressTracker.Result
import org.junit.Assert.assertEquals
import org.junit.Test

class DpadPressTrackerTest {

    @Test
    fun `a short press is a click on release`() {
        val tracker = DpadPressTracker()
        assertEquals(Result.NONE, tracker.down(0))
        assertEquals(Result.CLICK, tracker.up())
    }

    @Test
    fun `a held key long-clicks once, and its release is not a click`() {
        val tracker = DpadPressTracker()
        assertEquals(Result.NONE, tracker.down(0))
        assertEquals(Result.LONG_CLICK, tracker.down(1))
        assertEquals(Result.NONE, tracker.down(2))
        assertEquals(Result.NONE, tracker.up())
    }

    @Test
    fun `a release without a press seen first is ignored`() {
        // The focus arrived while the key was down, for instance after a menu closed.
        val tracker = DpadPressTracker()
        assertEquals(Result.NONE, tracker.up())
    }

    @Test
    fun `repeats without a first press never long-click`() {
        val tracker = DpadPressTracker()
        assertEquals(Result.NONE, tracker.down(3))
        assertEquals(Result.NONE, tracker.up())
    }

    @Test
    fun `the next press starts clean after a long press`() {
        val tracker = DpadPressTracker()
        tracker.down(0)
        tracker.down(1)
        tracker.up()
        tracker.down(0)
        assertEquals(Result.CLICK, tracker.up())
    }
}
