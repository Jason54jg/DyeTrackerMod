package com.dyetracker.ui.bounce

import com.dyetracker.ui.core.RenderContext
import com.dyetracker.ui.core.UiDraw
import com.dyetracker.ui.theme.UiTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * Render-only corner-hit celebration for the DVD bounce mode (PBI 38, task 38-4). When a widget
 * lands an exact corner hit, [spawn] emits a brief burst of small square "spark" particles from the
 * widget; they fly outward, fall under a little gravity, fade out, and self-expire after
 * [PARTICLE_LIFETIME_SECONDS], leaving nothing behind.
 *
 * The effect is pure screen-space chrome: drawn with [UiDraw.fillRect], **no sound**, no vanilla
 * `ParticleEffect`, no world interaction. It advances on the host's pause-aware wall clock via a
 * [BounceClock], so it freezes while paused and does not advance while the HUD is hidden (F1) —
 * consistent with the widget motion. With bounce disabled no hits occur, so nothing ever spawns and
 * there is zero render cost (the host clears any leftover particles when bounce turns off).
 *
 * Singleton (mirrors [com.dyetracker.ui.hud.HudWidgetHost]); all access is on the client/render
 * thread. The particle integration/fade math is pure and unit-tested; only [draw] needs a
 * `DrawContext`.
 */
object CornerCelebration {

    /** Particles emitted per corner hit. */
    private const val PARTICLE_COUNT = 14

    /** How long each particle lives before it is pruned (seconds). Brief, so the payoff is snappy. */
    const val PARTICLE_LIFETIME_SECONDS = 0.7f

    /** Side length of each square spark (GUI pixels). */
    private const val PARTICLE_SIZE_PX = 2

    /** Initial outward speed range for sparks (px/s); randomized per particle for a natural spread. */
    private const val PARTICLE_MIN_SPEED = 30f
    private const val PARTICLE_MAX_SPEED = 90f

    /** Downward acceleration applied to sparks (px/s²) so the burst arcs and falls. */
    private const val GRAVITY_PX_PER_SEC2 = 120f

    /** Keeps only the RGB channels of an ARGB token; alpha is recomputed per-frame from the fade. */
    private const val RGB_MASK = 0x00FFFFFF
    private const val ALPHA_MAX = 0xFF
    private const val ALPHA_SHIFT = 24

    private val TWO_PI = 2.0 * PI

    /** Confetti palette, sourced from existing [UiTheme] tokens (gold, green, white, magenta). */
    private val PARTICLE_COLORS = intArrayOf(
        UiTheme.Colors.BOOST_3X and RGB_MASK,
        UiTheme.Colors.BOOST_2X and RGB_MASK,
        UiTheme.Colors.TEXT_PRIMARY and RGB_MASK,
        UiTheme.Colors.PROGRESS_FILL and RGB_MASK,
    )

    private val particles = ArrayList<Particle>()
    private val clock = BounceClock()
    private val random: Random = Random.Default

    /** Whether any particles are currently alive (used by the host to skip clearing when idle). */
    fun isActive(): Boolean = particles.isNotEmpty()

    /**
     * Emit a burst from the centre of the widget rect ([rectX]/[rectY] top-left, [rectW]×[rectH]).
     * Each spark gets a random outward direction and speed and one of the palette colours.
     */
    fun spawn(rectX: Float, rectY: Float, rectW: Float, rectH: Float) {
        val originX = rectX + rectW / 2f
        val originY = rectY + rectH / 2f
        repeat(PARTICLE_COUNT) {
            val angle = random.nextDouble(0.0, TWO_PI)
            val speed = random.nextDouble(PARTICLE_MIN_SPEED.toDouble(), PARTICLE_MAX_SPEED.toDouble()).toFloat()
            particles.add(
                Particle(
                    x = originX,
                    y = originY,
                    vx = (cos(angle) * speed).toFloat(),
                    vy = (sin(angle) * speed).toFloat(),
                    colorRgb = PARTICLE_COLORS[random.nextInt(PARTICLE_COLORS.size)],
                ),
            )
        }
    }

    /**
     * Advance the simulation to the current frame (delta from the pause-aware [ctx] clock) and draw
     * the surviving particles on top of the widgets. Called once per bounce frame by the HUD host.
     */
    fun drawAndAdvance(ctx: RenderContext) {
        advance(clock.tick(ctx.wallClockMs))
        draw(ctx)
    }

    /**
     * Drop all particles immediately and re-arm the frame clock (e.g. when bounce mode is turned
     * off), so a later re-enable starts cleanly with no stale-gap dt — symmetric with
     * [BounceController]'s reset on toggle.
     */
    fun clear() {
        particles.clear()
        clock.reset()
    }

    /**
     * Integrate every particle by [dtSeconds] (age, position, gravity) and prune any that reached the
     * end of their life. Pure of any `DrawContext` so the lifetime/fade/motion math is unit-testable.
     */
    internal fun advance(dtSeconds: Float) {
        if (particles.isEmpty()) return
        val dt = dtSeconds.coerceAtLeast(0f)
        val iterator = particles.iterator()
        while (iterator.hasNext()) {
            val p = iterator.next()
            p.ageSeconds += dt
            if (p.ageSeconds >= PARTICLE_LIFETIME_SECONDS) {
                iterator.remove()
                continue
            }
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.vy += GRAVITY_PX_PER_SEC2 * dt
        }
    }

    /** Draw each live particle as a small square whose alpha is its current fade level. */
    private fun draw(ctx: RenderContext) {
        if (particles.isEmpty()) return
        for (p in particles) {
            val alpha = alphaForAge(p.ageSeconds)
            if (alpha <= 0f) continue
            UiDraw.fillRect(
                ctx.drawContext,
                p.x.roundToInt(),
                p.y.roundToInt(),
                PARTICLE_SIZE_PX,
                PARTICLE_SIZE_PX,
                withAlpha(p.colorRgb, alpha),
            )
        }
    }

    /** Linear fade: full alpha at birth, zero at (and past) the end of life. */
    internal fun alphaForAge(ageSeconds: Float): Float =
        (1f - ageSeconds / PARTICLE_LIFETIME_SECONDS).coerceIn(0f, 1f)

    /** Combine an [alpha] in [0,1] with an [rgb] (low 24 bits) into a packed ARGB colour. */
    private fun withAlpha(rgb: Int, alpha: Float): Int {
        val a = (alpha.coerceIn(0f, 1f) * ALPHA_MAX).roundToInt() and ALPHA_MAX
        return (a shl ALPHA_SHIFT) or (rgb and RGB_MASK)
    }

    /** One spark: screen-space position (px), velocity (px/s), colour (RGB), and accumulated age. */
    internal class Particle(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        val colorRgb: Int,
        var ageSeconds: Float = 0f,
    )

    // --- Test seams (visible to the unit suite via the Kotlin test friend-module) -----------------

    /** Live particle count, for unit-test assertions on spawning/expiry. */
    internal fun particleCountForTest(): Int = particles.size

    /** Snapshot of the live particles, for unit-test assertions on motion/age. */
    internal fun particlesForTest(): List<Particle> = ArrayList(particles)

    /** Inject a particle with a known velocity so a test can assert deterministic integration. */
    internal fun injectParticleForTest(particle: Particle) {
        particles.add(particle)
    }
}
