package com.dyetracker.data

import com.dyetracker.api.SyncRngDataRequest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * PBI 47: proves the mod's RNG data model + sync payload no longer carry slayer meters,
 * and that a legacy `data.json` still containing a `slayerMeters` object deserializes
 * without error (forward-compat via `ignoreUnknownKeys`, mirroring DataPersistence's Json).
 */
class RngMeterDataTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `SyncRngDataRequest serialized JSON has no slayerMeters key but keeps other meters`() {
        val request = SyncRngDataRequest(
            dungeonMeters = mapOf(DungeonFloor.M5 to DungeonRngMeter(DungeonFloor.M5, storedXp = 500)),
            nucleusMeter = NucleusRngMeter(storedXp = 1000),
            modTimestamp = 1_700_000_000_000L
        )

        val encoded = Json.encodeToString(request)

        assertFalse(encoded.contains("slayerMeters"), "sync payload must not carry slayerMeters")
        assertTrue(encoded.contains("dungeonMeters"), "sync payload should still carry dungeonMeters")
        assertTrue(encoded.contains("nucleusMeter"), "sync payload should still carry nucleusMeter")
    }

    @Test
    fun `PlayerRngData hasData is true with only a dungeon meter and false when empty`() {
        assertFalse(PlayerRngData().hasData())

        val withDungeon = PlayerRngData(
            dungeonMeters = mapOf(DungeonFloor.M7 to DungeonRngMeter(DungeonFloor.M7, storedXp = 10))
        )
        assertTrue(withDungeon.hasData())
    }

    @Test
    fun `legacy data json containing slayerMeters deserializes without slayer data and does not throw`() {
        // A data.json written by a pre-PBI-47 mod build still carries slayerMeters; the trimmed
        // PlayerRngData must ignore the unknown key rather than fail to load.
        val legacy = """
            {
              "slayerMeters": {
                "REVENANT": { "slayerType": "REVENANT", "storedXp": 12345, "goalXp": 50000 }
              },
              "dungeonMeters": {
                "M5": { "floor": "M5", "storedXp": 500 }
              }
            }
        """.trimIndent()

        val data = json.decodeFromString<PlayerRngData>(legacy)

        assertEquals(1, data.dungeonMeters.size)
        assertTrue(data.hasData())
    }
}
