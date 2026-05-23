package com.dyetracker.ui.edit

import net.minecraft.client.gui.DrawContext
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A per-feature affordance contributed to the [WidgetEditScreen] (e.g. the GIF
 * "+ Add overlay" panel). The screen shows one toolbar button per registered action; clicking
 * it activates the action, which then owns part of the screen — its own widgets, panel
 * rendering, and key handling — until it finishes or is dismissed. This keeps the generalized
 * edit screen free of any feature-specific UI.
 *
 * Implementations typically hold transient activation state (text field, in-flight job,
 * status); this is safe because the edit screen is modal and only one action is active at a
 * time. Reset that state in [onActivate]/[onDismiss].
 */
interface EditScreenAction {

    /** Toolbar button label shown while no action is active. */
    val label: String

    /** Build this action's widgets (text fields, buttons) into [screen]; sets initial focus. */
    fun onActivate(screen: WidgetEditScreen)

    /** Cancel in-flight work and reset state; called on ESC/cancel/screen close. */
    fun onDismiss()

    /** Paint behind the screen's widgets (panel background). */
    fun renderBackground(screen: WidgetEditScreen, context: DrawContext) {}

    /** Paint above the screen's widgets (status line). */
    fun renderForeground(screen: WidgetEditScreen, context: DrawContext) {}

    /** Handle [keyCode] while active; return true if consumed (e.g. Enter to submit). */
    fun keyPressed(screen: WidgetEditScreen, keyCode: Int): Boolean = false
}

/**
 * Registry of [EditScreenAction]s. Features register their edit-screen affordances here at
 * client init; [WidgetEditScreen] renders a toolbar button for each.
 */
object EditScreenActionRegistry {

    private val actions = CopyOnWriteArrayList<EditScreenAction>()

    fun register(action: EditScreenAction) {
        actions.add(action)
    }

    fun all(): List<EditScreenAction> = actions
}
