package com.dyetracker.overlay

import com.dyetracker.DyeTrackerMod
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.minecraft.client.MinecraftClient
import net.minecraft.client.option.KeyBinding
import net.minecraft.client.util.InputUtil
import net.minecraft.util.Identifier
import org.lwjgl.glfw.GLFW

/**
 * Registers the `G` keybind that opens [EditOverlaysScreen]. The binding lives in a
 * custom `dyetracker:main` category so vanilla's key-binding screen organizes our
 * shortcuts together. Players may rebind freely; the `G` default is only the cold-start
 * value and is honored only when the binding hasn't been overridden in `options.txt`.
 */
object OverlayKeybinds {

    private const val EDIT_OVERLAYS_TRANSLATION_KEY = "key.dyetracker.edit_overlays"
    private const val CATEGORY_PATH = "main"
    private const val DEFAULT_KEY = GLFW.GLFW_KEY_G

    private lateinit var editOverlaysKey: KeyBinding

    /** Register the keybind + the tick listener that opens edit mode on a press. */
    fun register() {
        val category = KeyBinding.Category.create(Identifier.of(DyeTrackerMod.MOD_ID, CATEGORY_PATH))
        editOverlaysKey = KeyBinding(
            EDIT_OVERLAYS_TRANSLATION_KEY,
            InputUtil.Type.KEYSYM,
            DEFAULT_KEY,
            category,
        )
        KeyBindingHelper.registerKeyBinding(editOverlaysKey)

        // Drain the press-counter every tick so a single press only opens the screen once
        // even if multiple presses landed within one tick.
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            while (editOverlaysKey.wasPressed()) {
                openEditOverlays(client)
            }
        }
        DyeTrackerMod.info("Overlay keybind registered (default G)")
    }

    /** Open edit mode if no other screen is active. Safe to call from any thread. */
    fun openEditOverlays(client: MinecraftClient) {
        if (client.currentScreen != null) return
        client.setScreen(EditOverlaysScreen())
    }
}
