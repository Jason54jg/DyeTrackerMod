package com.dyetracker.ui.edit

import com.dyetracker.ui.hud.HudWidgetEntry
import net.minecraft.client.gui.DrawContext

/**
 * A per-widget configuration panel a feature contributes for its HUD entries. It mirrors
 * [EditScreenAction]'s modal lifecycle, but instead of a global toolbar button it is bound to a
 * specific focused widget: while a widget is focused in the [WidgetEditScreen], pressing the
 * configure key opens that widget's panel — but only if its [HudWidgetEntry] supplies one
 * (entries without a panel ignore the key).
 *
 * The edit screen stays feature-agnostic: it discovers the panel from the focused entry and
 * drives it through the same render/key/ESC paths an [EditScreenAction] uses, with no knowledge
 * of any concrete feature. Implementations hold transient per-activation state (control refs);
 * reset it in [onActivate]/[onDismiss]. Activation is modal, so only one panel is live at a time.
 */
interface WidgetConfigPanel {

    /** Build this panel's controls into [screen], bound to [widgetId]; set initial focus. */
    fun onActivate(screen: WidgetEditScreen, widgetId: String)

    /** Reset transient state; called on ESC/cancel/screen close. */
    fun onDismiss()

    /** Paint behind the screen's widgets (panel background). */
    fun renderBackground(screen: WidgetEditScreen, context: DrawContext) {}

    /** Paint above the screen's widgets (header/status). */
    fun renderForeground(screen: WidgetEditScreen, context: DrawContext) {}

    /** Handle [keyCode] while active; return true if consumed. */
    fun keyPressed(screen: WidgetEditScreen, keyCode: Int): Boolean = false
}

/** True when [entry] exposes a per-widget config panel (so the configure trigger is eligible). */
fun hasConfigPanel(entry: HudWidgetEntry?): Boolean = entry?.configPanel != null
