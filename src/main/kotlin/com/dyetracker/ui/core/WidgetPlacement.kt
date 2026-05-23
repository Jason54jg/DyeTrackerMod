package com.dyetracker.ui.core

/**
 * Read contract for where a HUD widget sits and how it is shown. Coordinates are fractions
 * of the scaled-window size (0..1) addressing the widget's CENTER, so placements survive
 * resolution changes; [scale] multiplies the widget's intrinsic size; [visible] gates
 * drawing. Any feature's per-widget config (e.g. the GIF overlay config) implements this so
 * the HUD host and edit screen can position/scale/hide it generically.
 */
interface WidgetPlacement {
    /** Stable id identifying this placement (matches its HUD widget entry / texture id). */
    val id: String

    /** Fractional center X in [0, 1] of the scaled window. */
    val x: Float

    /** Fractional center Y in [0, 1] of the scaled window. */
    val y: Float

    /** Scale multiplier applied to the widget's intrinsic size. */
    val scale: Float

    /** Whether the widget is currently shown. */
    val visible: Boolean
}
