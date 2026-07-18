package com.dyetracker.integration

import com.dyetracker.ui.edit.WidgetEditScreen
import com.terraformersmc.modmenu.api.ConfigScreenFactory
import com.terraformersmc.modmenu.api.ModMenuApi

/**
 * ModMenu entrypoint (optional; only loaded if ModMenu is installed — see the "modmenu"
 * entrypoint + "suggests" entry in fabric.mod.json). The mod's "config" is HUD edit mode, so
 * the config screen button just opens [WidgetEditScreen] directly.
 */
class ModMenuIntegration : ModMenuApi {
    override fun getModConfigScreenFactory(): ConfigScreenFactory<*> =
        ConfigScreenFactory { parent -> WidgetEditScreen(parent) }
}
