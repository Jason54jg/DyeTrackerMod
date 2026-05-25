package com.dyetracker.ui.bounce

import com.dyetracker.ui.bounce.BounceMotion.BounceBody
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pure-JVM tests for [BounceController] (PBI 38, task 38-2): seeding from placement center with the
 * fixed speed in a randomized direction, independent per-widget trajectories, frame advance, corner
 * propagation, stale-id pruning + re-seed, and pause (dt = 0) freeze. The controller is a singleton,
 * so each test resets it first.
 */
class BounceControllerTest {

    private val tol = 1e-3f

    @BeforeTest
    fun reset() {
        BounceController.resetForTest()
    }

    private fun input(id: String, cx: Float, cy: Float) = BounceInput(id, cx, cy, WIDGET, WIDGET)

    @Test
    fun `enabling seeds one body per visible id at the placement center with the fixed speed`() {
        BounceController.random = Random(123)
        BounceController.setEnabled(true)
        // dt = 0 on the first frame, so the seeded body is reported unmoved at its placement center.
        BounceController.advance(listOf(input("a", 30f, 40f)), SCREEN, SCREEN, nowMs = 0L)

        val body = BounceController.bodySnapshot().getValue("a")
        assertEquals(30f, body.centerX, tol, "seeded at placement center x")
        assertEquals(40f, body.centerY, tol, "seeded at placement center y")
        val speed = sqrt(body.vx * body.vx + body.vy * body.vy)
        assertEquals(BounceMotion.BOUNCE_SPEED_PX_PER_SEC, speed, 1e-2f, "velocity magnitude == fixed speed")
    }

    @Test
    fun `seeding direction is deterministic for a fixed rng seed`() {
        fun seedVelocity(): Pair<Float, Float> {
            BounceController.resetForTest()
            BounceController.random = Random(999)
            BounceController.setEnabled(true)
            BounceController.advance(listOf(input("a", 50f, 50f)), SCREEN, SCREEN, nowMs = 0L)
            val b = BounceController.bodySnapshot().getValue("a")
            return b.vx to b.vy
        }
        val first = seedVelocity()
        val second = seedVelocity()
        assertEquals(first.first, second.first, tol)
        assertEquals(first.second, second.second, tol)
    }

    @Test
    fun `two widgets with the same start get distinct velocities`() {
        BounceController.random = Random(2024)
        BounceController.setEnabled(true)
        BounceController.advance(
            listOf(input("a", 50f, 50f), input("b", 50f, 50f)),
            SCREEN, SCREEN, nowMs = 0L,
        )
        val a = BounceController.bodySnapshot().getValue("a")
        val b = BounceController.bodySnapshot().getValue("b")
        assertTrue(a.vx != b.vx || a.vy != b.vy, "each widget seeds its own independent direction")
    }

    @Test
    fun `advance moves a body by its velocity between frames`() {
        BounceController.random = Random(5)
        BounceController.setEnabled(true)
        val widget = input("a", 200f, 200f) // centered on a large screen, far from any edge
        val p0 = BounceController.advance(listOf(widget), BIG_SCREEN, BIG_SCREEN, 0L).topLefts.getValue("a")
        val p1 = BounceController.advance(listOf(widget), BIG_SCREEN, BIG_SCREEN, 500L).topLefts.getValue("a")
        assertTrue(p0.x != p1.x || p0.y != p1.y, "body advanced over a 0.5s delta")
    }

    @Test
    fun `corner hits propagate through advance`() {
        BounceController.setEnabled(true)
        // Inject a body aimed straight into the bottom-right corner so the result is deterministic.
        // Velocity is large so it crosses both bounds within one clamped frame (dt is capped, so a
        // small velocity wouldn't reach the corner in a single step).
        BounceController.injectBodyForTest("w", BounceBody(centerX = 90f, centerY = 90f, vx = 200f, vy = 200f))
        val widget = input("w", 90f, 90f)

        // First frame: dt = 0 (clock just sampled), no movement, no corner.
        val seedFrame = BounceController.advance(listOf(widget), SCREEN, SCREEN, nowMs = 0L)
        assertTrue(seedFrame.cornerHits.isEmpty(), "no corner on the dt=0 frame")

        // Second frame: one (clamped) step drives the injected body into the corner.
        val hitFrame = BounceController.advance(listOf(widget), SCREEN, SCREEN, nowMs = 1000L)
        assertEquals(1, hitFrame.cornerHits.size)
        assertEquals("w", hitFrame.cornerHits.first().id)
    }

    @Test
    fun `stale ids are pruned and a returning id re-seeds from its placement`() {
        BounceController.random = Random(7)
        BounceController.setEnabled(true)
        val a = input("a", 50f, 50f)
        val b = input("b", 60f, 60f)

        BounceController.advance(listOf(a, b), SCREEN, SCREEN, 0L)
        assertEquals(setOf("a", "b"), BounceController.bodySnapshot().keys, "both seeded")

        // 'a' drifts away from (50,50) this frame; 'b' is gone from the visible set.
        BounceController.advance(listOf(a), SCREEN, SCREEN, 1000L)
        assertEquals(setOf("a"), BounceController.bodySnapshot().keys, "absent id pruned")

        // Drop 'a' too, then bring it back on the same clock tick (dt = 0) so the re-seeded body is
        // reported exactly at its placement center — proving its prior drift was discarded.
        BounceController.advance(emptyList(), SCREEN, SCREEN, 2000L)
        assertTrue(BounceController.bodySnapshot().isEmpty(), "all pruned")
        BounceController.advance(listOf(a), SCREEN, SCREEN, 2000L)

        val reAdded = BounceController.bodySnapshot().getValue("a")
        assertEquals(50f, reAdded.centerX, tol, "re-seeded fresh at placement center")
        assertEquals(50f, reAdded.centerY, tol)
    }

    @Test
    fun `a paused frame with no clock progress does not move any body`() {
        BounceController.random = Random(11)
        BounceController.setEnabled(true)
        val widget = input("a", 200f, 200f)
        BounceController.advance(listOf(widget), BIG_SCREEN, BIG_SCREEN, 0L) // seed
        val moved = BounceController.advance(listOf(widget), BIG_SCREEN, BIG_SCREEN, 1000L).topLefts.getValue("a")
        // Same nowMs => dt = 0 (paused): position must not change.
        val paused = BounceController.advance(listOf(widget), BIG_SCREEN, BIG_SCREEN, 1000L).topLefts.getValue("a")
        assertEquals(moved.x, paused.x, tol)
        assertEquals(moved.y, paused.y, tol)
    }

    @Test
    fun `disabling clears drift and re-enabling re-seeds from the current placement`() {
        BounceController.random = Random(3)
        BounceController.setEnabled(true)
        assertTrue(BounceController.isEnabled())

        val widget = input("a", 200f, 200f) // centered on a large screen, far from any edge
        BounceController.advance(listOf(widget), BIG_SCREEN, BIG_SCREEN, 0L) // seed at (200,200)
        BounceController.advance(listOf(widget), BIG_SCREEN, BIG_SCREEN, 1000L) // drift away
        val drifted = BounceController.bodySnapshot().getValue("a")
        assertTrue(drifted.centerX != 200f || drifted.centerY != 200f, "body drifted off its placement")

        BounceController.setEnabled(false)
        assertFalse(BounceController.isEnabled())
        assertTrue(BounceController.bodySnapshot().isEmpty(), "disabling clears all drift state")

        // Re-enable: first frame runs dt = 0, so the re-seeded body reports at the placement center,
        // not the position it had drifted to before — proving nothing carried across the toggle.
        BounceController.setEnabled(true)
        BounceController.advance(listOf(widget), BIG_SCREEN, BIG_SCREEN, 5000L)
        val reSeeded = BounceController.bodySnapshot().getValue("a")
        assertEquals(200f, reSeeded.centerX, tol, "re-enable re-seeds at current placement")
        assertEquals(200f, reSeeded.centerY, tol)
    }

    @Test
    fun `seeded directions stay off-axis so every widget drifts visibly diagonally`() {
        BounceController.random = Random(42)
        BounceController.setEnabled(true)
        val inputs = (1..20).map { input("w$it", 200f, 200f) }
        BounceController.advance(inputs, BIG_SCREEN, BIG_SCREEN, 0L)

        // Directions are kept >= MIN_AXIS_SEPARATION_DEG (20 deg) off each axis, so both velocity
        // components are at least speed * sin(20 deg) in magnitude.
        val minComponent = BounceMotion.BOUNCE_SPEED_PX_PER_SEC * sin(Math.toRadians(MIN_AXIS_SEPARATION_DEG)).toFloat()
        for ((id, body) in BounceController.bodySnapshot()) {
            assertTrue(abs(body.vx) >= minComponent - tol, "$id stays off the horizontal axis")
            assertTrue(abs(body.vy) >= minComponent - tol, "$id stays off the vertical axis")
        }
    }

    @Test
    fun `advance while disabled returns an empty frame and seeds nothing`() {
        val frame = BounceController.advance(listOf(input("a", 50f, 50f)), SCREEN, SCREEN, 0L)
        assertTrue(frame.topLefts.isEmpty())
        assertTrue(frame.cornerHits.isEmpty())
        assertTrue(BounceController.bodySnapshot().isEmpty(), "no state created while disabled")
    }

    private companion object {
        const val SCREEN = 100f
        const val BIG_SCREEN = 400f
        const val WIDGET = 10f

        /** Mirrors the controller's private MIN_AXIS_SEPARATION_DEG; kept in sync by the off-axis test. */
        const val MIN_AXIS_SEPARATION_DEG = 20.0
    }
}
