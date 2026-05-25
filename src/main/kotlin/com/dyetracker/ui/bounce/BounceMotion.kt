package com.dyetracker.ui.bounce

/**
 * Pure, Minecraft-free heart of the DVD-bounce feature (PBI 38). A single motion body is a point
 * (the widget *center*) carrying a velocity; one [step] integrates it over a time delta and reflects
 * it off the four screen edges using the widget's scaled bounds so the full widget always stays
 * on-screen. The step also reports whether it produced an **exact corner hit** (both axes reflecting
 * in the same step) so the celebration layer can fire.
 *
 * All inputs are plain numbers in scaled-window pixels (and seconds); there are no `DrawContext`,
 * `MinecraftClient`, or Fabric types here, so this runs in the pure-JVM unit suite — the project's
 * testing gate. Randomized initial direction is the controller's concern (38-2), not this module's:
 * [step] is fully deterministic given its inputs.
 */
object BounceMotion {

    /**
     * Fixed default drift speed in scaled-window pixels per second. A classic slow screensaver
     * drift. Speed is intentionally fixed — in-game speed configuration is out of PBI-38 scope.
     */
    const val BOUNCE_SPEED_PX_PER_SEC: Float = 50f

    /**
     * Pixel tolerance that classifies a near-simultaneous two-edge contact as a corner hit. A body
     * almost never crosses both bounds in the exact same frame, so when one axis reflects and the
     * other lands within this many pixels of one of its bounds, the step still counts as a corner
     * hit. Tunable; kept small so the corner payoff stays special rather than firing on every bounce.
     */
    const val CORNER_HIT_TOLERANCE_PX: Float = 2.0f

    /**
     * Immutable motion state for one widget: its [centerX]/[centerY] in scaled-window pixels and its
     * velocity [vx]/[vy] in px/s. The center (not the top-left) is tracked so reflection is symmetric
     * about the widget's middle.
     */
    data class BounceBody(
        val centerX: Float,
        val centerY: Float,
        val vx: Float,
        val vy: Float,
    )

    /**
     * Result of one [step]: the advanced [body] plus which axes reflected this step and the derived
     * [isCornerHit]. [isCornerHit] is true when both axes reflect, or when one axis reflects while the
     * other lands within [CORNER_HIT_TOLERANCE_PX] of a bound (see the constant's rationale).
     */
    data class StepResult(
        val body: BounceBody,
        val xReflected: Boolean,
        val yReflected: Boolean,
        val isCornerHit: Boolean,
    )

    /**
     * Advance [body] by [dtSeconds] on a [screenW]×[screenH] screen for a widget whose scaled size is
     * [widgetW]×[widgetH], reflecting off any edge it would cross.
     *
     * Behavior of degenerate inputs (documented; for finite inputs, never throws or produces NaN):
     * - **dt ≤ 0** (first frame after enable, or paused): no integration; the body is returned with
     *   no reflection so motion freezes with zero accumulation.
     * - **zero velocity**: the body does not move and no edge reflects.
     * - **widget larger than the screen on an axis**: the valid center range collapses, so that axis
     *   parks the center at the screen midpoint with zero velocity instead of oscillating forever.
     */
    fun step(
        body: BounceBody,
        screenW: Float,
        screenH: Float,
        widgetW: Float,
        widgetH: Float,
        dtSeconds: Float,
    ): StepResult {
        // Negative dt is treated as 0 so a clock glitch can never fling a widget or produce NaN.
        val dt = if (dtSeconds > 0f) dtSeconds else 0f

        val x = reflectAxis(body.centerX, body.vx, dt, screenW, widgetW)
        val y = reflectAxis(body.centerY, body.vy, dt, screenH, widgetH)

        // Corner hit: both axes reflect, or one reflects while the other merely grazes a bound
        // (within tolerance) — the realistic "nearly hit the corner" case in a discrete simulation.
        val isCornerHit =
            (x.reflected && y.reflected) ||
                (x.reflected && y.nearBound) ||
                (y.reflected && x.nearBound)

        return StepResult(
            body = BounceBody(x.pos, y.pos, x.vel, y.vel),
            xReflected = x.reflected,
            yReflected = y.reflected,
            isCornerHit = isCornerHit,
        )
    }

    /** Per-axis outcome of integration + reflection: new position/velocity and contact flags. */
    private class AxisResult(
        val pos: Float,
        val vel: Float,
        val reflected: Boolean,
        val nearBound: Boolean,
    )

    /**
     * Integrate and reflect one axis. The center is constrained to `[half, size - half]` so the full
     * widget (half-extent on each side) stays on-screen. On crossing a bound the center is reflected
     * back across it and the velocity component is inverted; a final clamp guards the pathological
     * case of a delta so large it would overshoot the opposite bound in one step.
     */
    private fun reflectAxis(
        center: Float,
        velocity: Float,
        dt: Float,
        screenSize: Float,
        widgetSize: Float,
    ): AxisResult {
        val half = widgetSize / 2f
        val minBound = half
        val maxBound = screenSize - half

        // Widget too big to fit on this axis: park at the screen midpoint, no motion (no oscillation).
        if (minBound > maxBound) {
            return AxisResult(screenSize / 2f, 0f, reflected = false, nearBound = false)
        }

        var pos = center + velocity * dt
        var vel = velocity
        var reflected = false

        if (pos < minBound) {
            pos = 2f * minBound - pos // reflect across the lower bound
            vel = -vel
            reflected = true
        } else if (pos > maxBound) {
            pos = 2f * maxBound - pos // reflect across the upper bound
            vel = -vel
            reflected = true
        }

        // Safety clamp for an over-large dt that reflected past the far bound; keeps the widget
        // strictly on-screen in a single pass (no reflect loop).
        if (pos < minBound) pos = minBound
        if (pos > maxBound) pos = maxBound

        val nearBound = (pos - minBound) <= CORNER_HIT_TOLERANCE_PX || (maxBound - pos) <= CORNER_HIT_TOLERANCE_PX
        return AxisResult(pos, vel, reflected, nearBound)
    }
}
