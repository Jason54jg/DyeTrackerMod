package com.dyetracker.ui.bounce

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pure-JVM tests for [CornerCelebration] (PBI 38, task 38-4): a burst spawns particles, alpha fades
 * monotonically to zero over a particle's life, position advances by velocity × dt, and the system
 * self-empties once every particle outlives [CornerCelebration.PARTICLE_LIFETIME_SECONDS]. Drawing
 * (the only `DrawContext`-dependent part) is exercised in-client, not here. The singleton is cleared
 * before each test.
 */
class CornerCelebrationTest {

    private val tol = 1e-3f
    private val lifetime = CornerCelebration.PARTICLE_LIFETIME_SECONDS

    @BeforeTest
    fun reset() {
        CornerCelebration.clear()
    }

    @Test
    fun `spawn emits a burst of particles`() {
        assertFalse(CornerCelebration.isActive(), "starts idle")
        CornerCelebration.spawn(rectX = 100f, rectY = 100f, rectW = 20f, rectH = 20f)
        assertTrue(CornerCelebration.particleCountForTest() > 0, "burst produced particles")
        assertTrue(CornerCelebration.isActive(), "active after spawn")
    }

    @Test
    fun `alpha fades monotonically from full to zero over a particle's life`() {
        val full = CornerCelebration.alphaForAge(0f)
        val mid = CornerCelebration.alphaForAge(lifetime / 2f)
        val end = CornerCelebration.alphaForAge(lifetime)
        val past = CornerCelebration.alphaForAge(lifetime * 2f)

        assertEquals(1f, full, tol, "full alpha at birth")
        assertTrue(full > mid && mid > end, "alpha decreases monotonically")
        assertEquals(0f, end, tol, "zero alpha at end of life")
        assertEquals(0f, past, tol, "stays clamped at zero past end of life")
    }

    @Test
    fun `advance moves a particle by velocity times dt`() {
        CornerCelebration.injectParticleForTest(
            CornerCelebration.Particle(x = 0f, y = 0f, vx = 10f, vy = 0f, colorRgb = 0xFFFFFF),
        )
        CornerCelebration.advance(0.1f)
        val p = CornerCelebration.particlesForTest().single()
        assertEquals(1f, p.x, tol, "x advanced by vx * dt")
        assertEquals(0.1f, p.ageSeconds, tol, "age accumulated by dt")
    }

    @Test
    fun `a zero delta frame neither moves nor ages a particle`() {
        CornerCelebration.injectParticleForTest(
            CornerCelebration.Particle(x = 5f, y = 5f, vx = 50f, vy = 50f, colorRgb = 0xFFFFFF),
        )
        CornerCelebration.advance(0f) // paused frame
        val p = CornerCelebration.particlesForTest().single()
        assertEquals(5f, p.x, tol)
        assertEquals(5f, p.y, tol)
        assertEquals(0f, p.ageSeconds, tol)
    }

    @Test
    fun `the system empties once all particles outlive their lifetime`() {
        CornerCelebration.spawn(rectX = 50f, rectY = 50f, rectW = 10f, rectH = 10f)
        assertTrue(CornerCelebration.isActive(), "burst is alive")

        CornerCelebration.advance(lifetime + 0.01f) // step past the full lifetime
        assertEquals(0, CornerCelebration.particleCountForTest(), "all particles pruned")
        assertFalse(CornerCelebration.isActive(), "no residue after the burst expires")
    }

    @Test
    fun `clear drops all particles immediately`() {
        CornerCelebration.spawn(rectX = 0f, rectY = 0f, rectW = 4f, rectH = 4f)
        assertTrue(CornerCelebration.isActive())
        CornerCelebration.clear()
        assertFalse(CornerCelebration.isActive())
        assertEquals(0, CornerCelebration.particleCountForTest())
    }
}
