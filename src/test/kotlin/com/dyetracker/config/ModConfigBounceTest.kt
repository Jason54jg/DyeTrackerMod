package com.dyetracker.config

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pure-JVM serialization tests for the PBI 38 `bounceEnabled` config flag: it defaults to false for
 * existing config files that predate it (back-compat), and it round-trips through JSON so the on/off
 * state survives a restart. Mirrors [ConfigManager]'s JSON settings; no Minecraft runtime.
 */
class ModConfigBounceTest {

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    @Test
    fun `config files predating the flag load with bounce disabled`() {
        // A minimal pre-PBI-38 config: no bounceEnabled key present.
        val legacy = """{ "apiUrl": "https://example.test", "authToken": "" }"""
        val config = json.decodeFromString<ModConfig>(legacy)
        assertFalse(config.bounceEnabled, "missing flag defaults to off, leaving existing configs unchanged")
    }

    @Test
    fun `bounce flag round-trips through JSON so it survives a restart`() {
        val saved = json.encodeToString(ModConfig(bounceEnabled = true))
        assertTrue(saved.contains("bounceEnabled"), "flag is written to disk")

        val reloaded = json.decodeFromString<ModConfig>(saved)
        assertTrue(reloaded.bounceEnabled, "enabled state persists across a save/load cycle")
    }

    @Test
    fun `default config has bounce disabled`() {
        assertFalse(ModConfig().bounceEnabled, "bounce is off by default")
    }
}
