package com.dyetracker.ui.bounce

import com.dyetracker.ui.bounce.BounceMotion.BounceBody
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * One visible HUD widget handed to [BounceController.advance] for a frame. The seed center is the
 * widget's current static placement center (fractional center × screen size) and is used only when a
 * body is first created for this [id]; [width]/[height] are the widget's scaled size, used for
 * edge-reflection bounds. All values are scaled-window pixels.
 */
data class BounceInput(
    val id: String,
    val seedCenterX: Float,
    val seedCenterY: Float,
    val width: Float,
    val height: Float,
)

/** Top-left position (scaled-window pixels) where a bouncing widget should draw this frame. */
data class TopLeft(val x: Float, val y: Float)

/**
 * A corner hit produced this frame, for the celebration layer (38-4). Carries the widget's [id] and
 * its rect ([x]/[y] top-left + [width]/[height]) at the moment of the hit so a burst can be anchored
 * to it.
 */
data class CornerHit(
    val id: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
)

/** Result of advancing one frame: where each widget draws, plus any corner hits to celebrate. */
data class BounceFrame(
    val topLefts: Map<String, TopLeft>,
    val cornerHits: List<CornerHit>,
)

/**
 * Stateful driver of the DVD-bounce simulation (PBI 38). Owns one [BounceBody] per HUD widget id,
 * seeds each from the widget's placement center plus a randomized initial direction at the fixed
 * [BounceMotion.BOUNCE_SPEED_PX_PER_SEC], advances every body each frame via the pure [BounceMotion]
 * step, prunes bodies whose widgets disappear, and surfaces corner hits.
 *
 * Drift state is **purely ephemeral**: it lives only in memory and is cleared whenever the mode
 * toggles, so disabling (or restarting) restores every widget to its saved placement with nothing
 * persisted. The on/off flag itself is persisted by the command layer (38-5), not here.
 *
 * Singleton (mirrors [com.dyetracker.ui.hud.HudWidgetHost]) so the host (38-3) and command (38-5)
 * share one simulation. The motion math is in the pure [BounceMotion]; this layer holds the state.
 *
 * Thread contract: all access ([advance] from the HUD render loop, [setEnabled] from the client
 * command callback) happens on the client/render thread — the same single-threaded assumption as
 * [com.dyetracker.ui.hud.HudWidgetHost] — so the mutable state needs no synchronization.
 */
object BounceController {

    /** Quadrants of the unit circle; the seed direction picks one then an in-quadrant angle. */
    private const val QUADRANT_COUNT = 4
    private const val DEGREES_PER_QUADRANT = 90.0

    /**
     * Minimum angular separation (degrees) the seeded direction keeps from either axis. Keeping
     * directions out of the near-horizontal/near-vertical dead zones means every widget drifts
     * visibly diagonally (the classic look) and can actually reach corners over time.
     */
    private const val MIN_AXIS_SEPARATION_DEG = 20.0

    private val EMPTY_FRAME = BounceFrame(emptyMap(), emptyList())

    private val bodies = HashMap<String, BounceBody>()
    private var enabled = false
    private val clock = BounceClock()

    /**
     * RNG for seeding initial directions. Defaults to the shared [Random.Default]; overridable so
     * unit tests get deterministic, reproducible directions.
     */
    internal var random: Random = Random.Default

    fun isEnabled(): Boolean = enabled

    /**
     * Enable or disable bounce mode. Flipping either way clears all drift state and resets the clock
     * so re-enabling re-seeds every widget from its current placement — drift never carries across a
     * toggle and is never persisted. A no-op when already in the requested state.
     */
    fun setEnabled(value: Boolean) {
        if (value == enabled) return
        enabled = value
        bodies.clear()
        clock.reset()
    }

    /**
     * Advance every visible widget one frame and return its draw position plus any corner hits.
     *
     * [nowMs] is the host's pause-aware running clock (milliseconds); the per-frame delta comes from
     * [BounceClock], so motion freezes while paused (the clock stops → dt = 0) with no catch-up jump
     * on unpause, the first frame after enabling runs with dt = 0, and a long gap (F1-hide, lag) is
     * clamped so widgets resume near where they were instead of teleporting. Bodies for ids absent
     * from [inputs] are pruned. Returns an empty frame when disabled (the host uses the static path).
     */
    fun advance(inputs: List<BounceInput>, screenW: Float, screenH: Float, nowMs: Long): BounceFrame {
        if (!enabled) return EMPTY_FRAME

        val dt = clock.tick(nowMs)

        val topLefts = HashMap<String, TopLeft>(inputs.size)
        val cornerHits = ArrayList<CornerHit>()
        val liveIds = HashSet<String>(inputs.size)

        for (input in inputs) {
            liveIds.add(input.id)
            val body = bodies[input.id] ?: seed(input)
            val result = BounceMotion.step(body, screenW, screenH, input.width, input.height, dt)
            bodies[input.id] = result.body

            val left = result.body.centerX - input.width / 2f
            val top = result.body.centerY - input.height / 2f
            topLefts[input.id] = TopLeft(left, top)
            if (result.isCornerHit) {
                cornerHits.add(CornerHit(input.id, left, top, input.width, input.height))
            }
        }

        // Prune bodies whose widgets are no longer visible/present so state can't grow across churn.
        // Every input id was just inserted, so `bodies` is always a superset of `liveIds` here.
        if (bodies.size > liveIds.size) {
            bodies.keys.retainAll(liveIds)
        }

        return BounceFrame(topLefts, cornerHits)
    }

    /** Create a fresh body for [input] at its placement center with a randomized fixed-speed velocity. */
    private fun seed(input: BounceInput): BounceBody {
        val angle = randomDirectionRadians()
        val vx = (cos(angle) * BounceMotion.BOUNCE_SPEED_PX_PER_SEC).toFloat()
        val vy = (sin(angle) * BounceMotion.BOUNCE_SPEED_PX_PER_SEC).toFloat()
        return BounceBody(input.seedCenterX, input.seedCenterY, vx, vy)
    }

    /**
     * A random direction kept at least [MIN_AXIS_SEPARATION_DEG] off both axes: pick a quadrant
     * uniformly, then an angle within it. Independent per widget, so each drifts on its own path.
     */
    private fun randomDirectionRadians(): Double {
        val quadrant = random.nextInt(QUADRANT_COUNT)
        val inQuadrant = random.nextDouble(MIN_AXIS_SEPARATION_DEG, DEGREES_PER_QUADRANT - MIN_AXIS_SEPARATION_DEG)
        return Math.toRadians(quadrant * DEGREES_PER_QUADRANT + inQuadrant)
    }

    // --- Test seams (visible to the unit suite via the Kotlin test friend-module) -----------------

    /** Snapshot of the live bodies, for unit-test assertions on seeding/pruning. */
    internal fun bodySnapshot(): Map<String, BounceBody> = HashMap(bodies)

    /** Inject a body with a known velocity so a test can drive a deterministic corner hit. */
    internal fun injectBodyForTest(id: String, body: BounceBody) {
        bodies[id] = body
    }

    /** Reset all state to defaults for test isolation (the singleton persists across tests). */
    internal fun resetForTest() {
        bodies.clear()
        enabled = false
        clock.reset()
        random = Random.Default
    }
}
