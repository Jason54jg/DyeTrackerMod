package com.dyetracker.ui.bounce

/**
 * Turns the HUD host's monotonic, pause-frozen wall clock (milliseconds) into a per-frame delta in
 * seconds for the bounce simulation and its corner celebration (PBI 38).
 *
 * The delta is clamped to [MAX_FRAME_DT_SECONDS] so a long gap between ticks — the HUD hidden with
 * F1, the window minimized, a GC/lag spike — can never fast-forward the simulation into a teleport;
 * motion simply resumes within one slow frame of where it was (the F1 "no jump beyond a single-frame
 * delta" behavior). The first tick after construction or [reset] returns 0, so enabling bounce (or
 * any fresh start) never produces a startup jump. Because the source clock already freezes while the
 * game is paused, a paused frame produces dt = 0 with no special handling.
 *
 * Pure JVM (no Minecraft types); unit-tested directly.
 */
class BounceClock {

    private var lastMs: Long = UNSET

    /**
     * Per-frame delta in seconds for [nowMs]: 0 on the first tick after construction/[reset],
     * otherwise `(nowMs - lastTick) / 1000` coerced into `[0, MAX_FRAME_DT_SECONDS]` (so a stale or
     * backwards clock can never move the simulation backwards or fling it forward).
     */
    fun tick(nowMs: Long): Float {
        val dt = if (lastMs == UNSET) 0f else (nowMs - lastMs) / MS_PER_SECOND
        lastMs = nowMs
        return dt.coerceIn(0f, MAX_FRAME_DT_SECONDS)
    }

    /** Forget the last tick so the next [tick] returns 0 (no catch-up across the gap). */
    fun reset() {
        lastMs = UNSET
    }

    companion object {
        private const val UNSET = -1L
        private const val MS_PER_SECOND = 1000f

        /**
         * Upper bound on a single frame's delta (seconds), ≈ 20 fps. Frames slower than this — a
         * hidden HUD, a minimized window, a lag spike — are capped so the bounce never teleports
         * across the gap; it resumes within one capped frame of where it left off.
         */
        const val MAX_FRAME_DT_SECONDS = 0.05f
    }
}
