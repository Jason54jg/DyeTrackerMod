package com.dyetracker.overlay

import com.dyetracker.config.ConfigManager
import com.dyetracker.ui.core.PlacementEditor
import com.dyetracker.ui.texture.ImageTextureManager

/**
 * Write side of the GIF overlays' placement for the generalized edit screen. Applies edits to
 * the persisted [com.dyetracker.config.ConfigManager.gifPlacements] store and releases GPU
 * textures when an overlay is removed.
 */
object GifPlacementEditor : PlacementEditor {

    override fun setPlacementTransient(id: String, x: Float, y: Float, scale: Float) {
        ConfigManager.gifPlacements.updateTransient(id) { it.copy(x = x, y = y, scale = scale) }
    }

    override fun flush() {
        ConfigManager.gifPlacements.flush()
    }

    override fun setVisible(id: String, visible: Boolean) {
        ConfigManager.gifPlacements.update(id) { it.copy(visible = visible) }
    }

    override fun remove(id: String) {
        if (ConfigManager.gifPlacements.remove(id)) {
            ImageTextureManager.release(id)
        }
    }
}
