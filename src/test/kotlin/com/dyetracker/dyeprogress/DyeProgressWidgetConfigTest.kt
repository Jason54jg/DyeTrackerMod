package com.dyetracker.dyeprogress

import com.dyetracker.config.ModConfig
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Serialization coverage for the single-dye widget config (task 34-3): a full round-trip and
 * the backward-compatible default (an existing config file without `dyeProgressWidgets` must
 * still load). Mirrors the ConfigManager Json config.
 */
class DyeProgressWidgetConfigTest {

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    @Test
    fun `round-trips a widget config preserving every field`() {
        val original = DyeProgressWidgetConfig(
            id = DyeProgressWidgetConfig.newId(),
            dyeId = "matcha",
            profileName = "Mango",
            profileId = "profile-uuid-1",
            x = 0.25f,
            y = 0.8f,
            scale = 1.5f,
            visible = false,
        )

        val decoded = json.decodeFromString<DyeProgressWidgetConfig>(json.encodeToString(original))

        assertEquals(original, decoded)
    }

    @Test
    fun `applies default placement when only required fields are given`() {
        val cfg = DyeProgressWidgetConfig(
            id = "abc123",
            dyeId = "celeste",
            profileName = "Mango",
            profileId = "p1",
        )

        assertEquals(DyeProgressWidgetConfig.DEFAULT_X, cfg.x)
        assertEquals(DyeProgressWidgetConfig.DEFAULT_Y, cfg.y)
        assertEquals(DyeProgressWidgetConfig.DEFAULT_SCALE, cfg.scale)
        assertEquals(DyeProgressWidgetConfig.DEFAULT_VISIBLE, cfg.visible)
    }

    @Test
    fun `newId produces short distinct ids`() {
        val a = DyeProgressWidgetConfig.newId()
        val b = DyeProgressWidgetConfig.newId()
        assertEquals(8, a.length)
        assertTrue(a != b)
    }

    @Test
    fun `a ModConfig JSON without dyeProgressWidgets decodes to an empty list`() {
        // A pre-PBI-34 config file: no `dyeProgressWidgets` key at all.
        val legacy = """
            {
              "apiUrl": "https://example.test",
              "authToken": "",
              "linkedUuid": "",
              "linkedUsername": "",
              "gifs": []
            }
        """.trimIndent()

        val config = json.decodeFromString<ModConfig>(legacy)

        assertTrue(config.dyeProgressWidgets.isEmpty())
    }

    @Test
    fun `a ModConfig round-trips its dyeProgressWidgets slice`() {
        val config = ModConfig(
            dyeProgressWidgets = listOf(
                DyeProgressWidgetConfig(id = "w1", dyeId = "matcha", profileName = "Mango", profileId = "p1"),
                DyeProgressWidgetConfig(id = "w2", dyeId = "celeste", profileName = "Banana", profileId = "p2", visible = false),
            ),
        )

        val decoded = json.decodeFromString<ModConfig>(json.encodeToString(config))

        assertEquals(config.dyeProgressWidgets, decoded.dyeProgressWidgets)
    }
}
