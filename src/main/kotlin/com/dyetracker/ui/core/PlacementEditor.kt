package com.dyetracker.ui.core

/**
 * Write side of a HUD widget's placement, supplied by the owning feature so the generic edit
 * screen can move/scale/hide/remove a widget without knowing its concrete config type. The
 * edit screen computes the new (already-clamped) absolute values and calls these; the feature
 * applies them to its own [WidgetPlacement] config and persists.
 */
interface PlacementEditor {
    /**
     * Apply a new center ([x], [y] as fractions 0..1) and [scale] WITHOUT persisting — used
     * during interactive drag/scroll so a burst hits disk once via [flush], not per frame.
     */
    fun setPlacementTransient(id: String, x: Float, y: Float, scale: Float)

    /** Persist buffered transient placement changes for this feature. */
    fun flush()

    /** Set visibility and persist. */
    fun setVisible(id: String, visible: Boolean)

    /** Remove the widget, persist, and release any resources it owns (e.g. GPU textures). */
    fun remove(id: String)
}
