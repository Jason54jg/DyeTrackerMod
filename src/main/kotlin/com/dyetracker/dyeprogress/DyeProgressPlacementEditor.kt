package com.dyetracker.dyeprogress

import com.dyetracker.config.ConfigManager
import com.dyetracker.ui.core.PlacementEditor

/**
 * Write side of the single-dye progress widgets' placement for the generalized edit screen.
 * Applies edits to the persisted [ConfigManager.dyeProgressPlacements] store.
 *
 * Unlike the singleton rotation widget (whose `remove` only hides), these widgets are
 * user-deletable, so [remove] actually drops the placement from config. No GPU textures are
 * owned per widget — dye sprites are shared and preloaded — so removal needs no texture
 * release.
 */
object DyeProgressPlacementEditor : PlacementEditor {

    override fun setPlacementTransient(id: String, x: Float, y: Float, scale: Float) {
        ConfigManager.dyeProgressPlacements.updateTransient(id) { it.copy(x = x, y = y, scale = scale) }
    }

    override fun flush() {
        ConfigManager.dyeProgressPlacements.flush()
    }

    override fun setVisible(id: String, visible: Boolean) {
        ConfigManager.dyeProgressPlacements.update(id) { it.copy(visible = visible) }
    }

    override fun remove(id: String) {
        ConfigManager.dyeProgressPlacements.remove(id)
    }
}
