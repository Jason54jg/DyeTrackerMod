package com.dyetracker.overlay

import com.dyetracker.config.ConfigManager
import com.dyetracker.ui.components.SpriteWidget
import com.dyetracker.ui.edit.EditScreenActionRegistry
import com.dyetracker.ui.hud.HudWidgetEntry
import com.dyetracker.ui.hud.HudWidgetRegistry

/**
 * Wires the GIF/image overlays into the UI toolkit. Registers a HUD provider that maps the live
 * [ConfigManager] gif list to one [HudWidgetEntry] per overlay each frame (a [SpriteWidget]
 * whose frames come from the shared texture manager, plus [GifPlacementEditor] so the edit
 * screen can move/scale/hide/remove it), and contributes the GIF "+ Add overlay"
 * [GifAddAction] to the edit screen. Each [GifOverlayConfig] is itself a `WidgetPlacement`, so
 * it supplies its own center/scale/visibility directly.
 */
object GifHudFeature {

    /** Register the GIF overlay HUD provider + edit-screen add action. Call once at client init. */
    fun register() {
        HudWidgetRegistry.register {
            ConfigManager.gifPlacements.all().map { cfg ->
                HudWidgetEntry(cfg, SpriteWidget(cfg.id), GifPlacementEditor)
            }
        }
        EditScreenActionRegistry.register(GifAddAction)
    }
}
