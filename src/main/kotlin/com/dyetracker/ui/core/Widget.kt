package com.dyetracker.ui.core

import net.minecraft.client.gui.DrawContext

/**
 * Per-frame render context handed to every [Widget.draw]. Bundles the [DrawContext] with
 * the shared animation clock so animated widgets (e.g. sprites) select frames consistently
 * and freeze together while the game is paused.
 *
 * [wallClockMs] is a monotonic millisecond timer advanced by the HUD host; it stops
 * accumulating while [paused] is true so animations hold their current frame on the pause
 * screen.
 */
data class RenderContext(
    val drawContext: DrawContext,
    val wallClockMs: Long,
    val paused: Boolean,
)

/**
 * A composable in-game UI element. A widget reports its intrinsic [measure] size (in GUI
 * pixels, pre-scale) and draws itself at an absolute top-left position via [draw].
 *
 * Contract:
 * - [measure] must be side-effect free and cheap; containers may call it repeatedly while
 *   laying out children.
 * - [draw] renders at the intrinsic size; any placement scale is applied by the host via a
 *   matrix transform, so widgets never multiply coordinates by a scale factor themselves.
 */
interface Widget {
    /** Intrinsic size in GUI pixels, before any HUD placement scale is applied. */
    fun measure(): Size

    /** Draw the widget with its top-left corner at ([x], [y]) in GUI pixels. */
    fun draw(ctx: RenderContext, x: Int, y: Int)
}
