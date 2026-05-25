package com.dyetracker.ui.bounce

import com.dyetracker.ui.bounce.BounceMotion.BounceBody
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pure-JVM tests for the bounce motion core (PBI 38, task 38-1): integration, four-edge reflection
 * with the widget's scaled bounds, corner-hit detection, and degenerate-input safety. All cases use
 * exact integer-valued floats so the arithmetic is deterministic.
 *
 * Screen is 100×100 and the widget is 10×10 (half-extent 5) unless noted, so the valid center range
 * is [5, 95] on each axis.
 */
class BounceMotionTest {

    private val tol = 1e-4f
    private val screen = 100f
    private val widget = 10f
    private val minBound = widget / 2f // 5
    private val maxBound = screen - widget / 2f // 95

    private fun step(body: BounceBody, dt: Float = 1f) =
        BounceMotion.step(body, screen, screen, widget, widget, dt)

    @Test
    fun `integrates center by velocity when no edge is near`() {
        val result = step(BounceBody(centerX = 50f, centerY = 50f, vx = 10f, vy = 0f))
        assertEquals(60f, result.body.centerX, tol)
        assertEquals(50f, result.body.centerY, tol)
        assertEquals(10f, result.body.vx, tol)
        assertEquals(0f, result.body.vy, tol)
        assertFalse(result.xReflected)
        assertFalse(result.yReflected)
        assertFalse(result.isCornerHit)
    }

    @Test
    fun `reflects off the right edge and inverts only vx`() {
        // 90 + 20 = 110 (past max 95) -> 2*95 - 110 = 80, vx flips.
        val result = step(BounceBody(centerX = 90f, centerY = 50f, vx = 20f, vy = 0f))
        assertEquals(80f, result.body.centerX, tol)
        assertEquals(-20f, result.body.vx, tol)
        assertEquals(50f, result.body.centerY, tol)
        assertEquals(0f, result.body.vy, tol)
        assertTrue(result.xReflected)
        assertFalse(result.yReflected)
    }

    @Test
    fun `reflects off the left edge and inverts only vx`() {
        // 10 - 20 = -10 (below min 5) -> 2*5 - (-10) = 20, vx flips.
        val result = step(BounceBody(centerX = 10f, centerY = 50f, vx = -20f, vy = 0f))
        assertEquals(20f, result.body.centerX, tol)
        assertEquals(20f, result.body.vx, tol)
        assertTrue(result.xReflected)
        assertFalse(result.yReflected)
    }

    @Test
    fun `reflects off the top edge and inverts only vy`() {
        val result = step(BounceBody(centerX = 50f, centerY = 10f, vx = 0f, vy = -20f))
        assertEquals(20f, result.body.centerY, tol)
        assertEquals(20f, result.body.vy, tol)
        assertFalse(result.xReflected)
        assertTrue(result.yReflected)
    }

    @Test
    fun `reflects off the bottom edge and inverts only vy`() {
        val result = step(BounceBody(centerX = 50f, centerY = 90f, vx = 0f, vy = 20f))
        assertEquals(80f, result.body.centerY, tol)
        assertEquals(-20f, result.body.vy, tol)
        assertFalse(result.xReflected)
        assertTrue(result.yReflected)
    }

    @Test
    fun `widget stays fully on-screen after reflection`() {
        // A large step that strongly overshoots both bounds must still land the full bounds on-screen.
        val result = step(BounceBody(centerX = 90f, centerY = 90f, vx = 500f, vy = 500f))
        val halfW = widget / 2f
        assertTrue(result.body.centerX - halfW >= 0f, "left edge on-screen")
        assertTrue(result.body.centerX + halfW <= screen, "right edge on-screen")
        assertTrue(result.body.centerY - halfW >= 0f, "top edge on-screen")
        assertTrue(result.body.centerY + halfW <= screen, "bottom edge on-screen")
    }

    @Test
    fun `corner hit sets isCornerHit when both axes reflect`() {
        // Aimed straight into the bottom-right corner: both axes cross in one step.
        val result = step(BounceBody(centerX = 90f, centerY = 90f, vx = 20f, vy = 20f))
        assertTrue(result.xReflected)
        assertTrue(result.yReflected)
        assertTrue(result.isCornerHit)
    }

    @Test
    fun `corner hit fires via tolerance when one axis reflects and the other grazes a bound`() {
        // x reflects off the right edge; y lands exactly on the bottom bound (95) without crossing,
        // so the near-bound tolerance promotes it to a corner hit.
        val result = step(BounceBody(centerX = 90f, centerY = 94f, vx = 20f, vy = 1f))
        assertTrue(result.xReflected)
        assertFalse(result.yReflected)
        assertTrue(result.isCornerHit)
    }

    @Test
    fun `corner tolerance fires just inside the threshold and not just outside`() {
        // x reflects off the right edge in both cases; only how close y grazes the bottom bound varies.
        // Tolerance is 2px: y landing 1.5px from the bound is a corner; 2.5px from it is not.
        val inside = step(BounceBody(centerX = 90f, centerY = 93f, vx = 20f, vy = 0.5f)) // y -> 93.5, gap 1.5
        assertTrue(inside.xReflected)
        assertFalse(inside.yReflected)
        assertTrue(inside.isCornerHit, "graze within tolerance is a corner hit")

        val outside = step(BounceBody(centerX = 90f, centerY = 92f, vx = 20f, vy = 0.5f)) // y -> 92.5, gap 2.5
        assertTrue(outside.xReflected)
        assertFalse(outside.yReflected)
        assertFalse(outside.isCornerHit, "graze beyond tolerance is not a corner hit")
    }

    @Test
    fun `single edge hit does not set isCornerHit`() {
        // Reflects off the right edge while y stays in the vertical middle (far from any bound).
        val result = step(BounceBody(centerX = 90f, centerY = 50f, vx = 20f, vy = 0f))
        assertTrue(result.xReflected)
        assertFalse(result.yReflected)
        assertFalse(result.isCornerHit)
    }

    @Test
    fun `widget larger than the screen parks at center with zero velocity`() {
        val big = 200f
        val result = BounceMotion.step(
            BounceBody(centerX = 90f, centerY = 90f, vx = 30f, vy = 30f),
            screen, screen, big, big, dtSeconds = 1f,
        )
        assertEquals(screen / 2f, result.body.centerX, tol)
        assertEquals(screen / 2f, result.body.centerY, tol)
        assertEquals(0f, result.body.vx, tol)
        assertEquals(0f, result.body.vy, tol)
        assertFalse(result.isCornerHit)
        assertTrue(result.body.centerX.isFinite() && result.body.centerY.isFinite(), "no NaN/inf")
    }

    @Test
    fun `zero or negative dt does not move the body`() {
        val body = BounceBody(centerX = 50f, centerY = 50f, vx = 25f, vy = 25f)
        for (dt in listOf(0f, -1f)) {
            val result = step(body, dt)
            assertEquals(50f, result.body.centerX, tol, "dt=$dt holds x")
            assertEquals(50f, result.body.centerY, tol, "dt=$dt holds y")
            assertFalse(result.xReflected, "dt=$dt no x reflect")
            assertFalse(result.yReflected, "dt=$dt no y reflect")
        }
    }

    @Test
    fun `zero velocity does not move and does not reflect`() {
        val result = step(BounceBody(centerX = 50f, centerY = 50f, vx = 0f, vy = 0f))
        assertEquals(50f, result.body.centerX, tol)
        assertEquals(50f, result.body.centerY, tol)
        assertFalse(result.xReflected)
        assertFalse(result.yReflected)
        assertFalse(result.isCornerHit)
    }
}
