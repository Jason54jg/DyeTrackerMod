package com.dyetracker.rotation

import com.dyetracker.config.ConfigManager
import com.dyetracker.ui.core.PlacementEditor

/**
 * Write side of the dye-rotation widget's placement for the generalized edit screen. Delegates to
 * [ConfigManager.rotationPlacements]. The rotation feature seeds a placement on startup, so the
 * element always exists for transient/visibility updates.
 *
 * Unlike a GIF overlay, the rotation widget is a singleton the user can't delete — [remove] hides
 * it instead, so "Delete" in the edit screen reads as "hide".
 */
object RotationPlacementEditor : PlacementEditor {

    override fun setPlacementTransient(id: String, x: Float, y: Float, scale: Float) {
        ConfigManager.rotationPlacements.updateTransient(id) { it.copy(x = x, y = y, scale = scale) }
    }

    override fun flush() {
        ConfigManager.rotationPlacements.flush()
    }

    override fun setVisible(id: String, visible: Boolean) {
        ConfigManager.rotationPlacements.update(id) { it.copy(visible = visible) }
    }

    override fun remove(id: String) {
        // Singleton widget is not user-deletable; hide it instead of removing the placement.
        setVisible(id, false)
    }
}
