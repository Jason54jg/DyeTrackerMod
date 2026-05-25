package com.dyetracker.ui.bounce

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pure-JVM tests for [BounceClock] (PBI 38): the first tick is 0, normal gaps convert to seconds, a
 * long gap (F1-hide / lag) is clamped so the simulation can't teleport, a stale/backwards clock
 * yields 0, and [BounceClock.reset] re-arms the zero-first-tick.
 */
class BounceClockTest {

    private val tol = 1e-4f

    @Test
    fun `first tick after construction is zero`() {
        assertEquals(0f, BounceClock().tick(1234L), tol)
    }

    @Test
    fun `a normal gap converts to seconds`() {
        val clock = BounceClock()
        clock.tick(1000L)
        assertEquals(0.016f, clock.tick(1016L), tol) // ~one 60fps frame
    }

    @Test
    fun `a long gap is clamped to the max frame delta`() {
        val clock = BounceClock()
        clock.tick(0L)
        // A 5s gap (e.g. HUD hidden with F1) must not fast-forward the simulation.
        assertEquals(BounceClock.MAX_FRAME_DT_SECONDS, clock.tick(5000L), tol)
    }

    @Test
    fun `a stale or backwards clock yields zero, never negative`() {
        val clock = BounceClock()
        clock.tick(2000L)
        assertEquals(0f, clock.tick(2000L), tol, "same timestamp -> dt 0 (paused frame)")
        assertEquals(0f, clock.tick(1000L), tol, "backwards clock -> dt 0, never negative")
    }

    @Test
    fun `reset re-arms the zero first tick`() {
        val clock = BounceClock()
        clock.tick(1000L)
        assertTrue(clock.tick(1016L) > 0f, "ticking normally after a prior sample")
        clock.reset()
        assertEquals(0f, clock.tick(9999L), tol, "first tick after reset is 0 (no catch-up)")
    }
}
