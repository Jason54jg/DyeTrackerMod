package com.dyetracker.dyeprogress

import com.dyetracker.config.ConfigManager
import com.dyetracker.ui.edit.EditScreenActionRegistry
import com.dyetracker.ui.hud.HudWidgetEntry
import com.dyetracker.ui.hud.HudWidgetRegistry

/**
 * Wires the single-dye progress widgets into the UI toolkit (PBI 34). Registers a HUD provider
 * that maps the live [ConfigManager.dyeProgressPlacements] list to one [HudWidgetEntry] per
 * widget each frame: the composed tree from [DyeProgressWidgetView] (reading live data from
 * [DyeProgressStore]) plus [DyeProgressPlacementEditor] so the shared edit screen can
 * move/scale/hide/remove it. Evaluated per frame, so adds/removes/edits reflect immediately.
 *
 * The in-screen "+ Add dye" panel ([DyeProgressAddAction]) is contributed to the edit screen
 * here as well.
 */
object DyeProgressHudFeature {

    /** Register the dye-progress HUD provider + edit-screen add action. Call once at client init. */
    fun register() {
        HudWidgetRegistry.register {
            ConfigManager.dyeProgressPlacements.all().map { cfg ->
                val status = DyeProgressStore.status(cfg.profileId)
                val entry = DyeProgressStore.entry(cfg.profileId, cfg.dyeId)
                HudWidgetEntry(cfg, DyeProgressWidgetView.build(cfg, status, entry), DyeProgressPlacementEditor)
            }
        }
        EditScreenActionRegistry.register(DyeProgressAddAction)
    }
}
