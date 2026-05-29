package com.dyetracker.data

import com.dyetracker.api.SyncRngDataRequest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.AfterTest
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

    // The mergeVisitorsSeen tests mutate the RngDataStore singleton; reset it after each
    // test so no residual copperDye / visitorLogbook state can leak into other tests in this JVM.
    @AfterTest
    fun tearDown() {
        RngDataStore.clear()
    }

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
    fun `copperDye serializes under the visitorsSeen key and not visitorAccepts (PBI 42)`() {
        val request = SyncRngDataRequest(
            copperDye = CopperDyeData(
                visitorsSeen = mapOf(VisitorRarity.RARE to 3, VisitorRarity.SPECIAL to 7)
            ),
            modTimestamp = 1_700_000_000_000L
        )

        val encoded = Json.encodeToString(request)

        assertTrue(encoded.contains("visitorsSeen"), "copper payload must use the visitorsSeen key")
        assertFalse(encoded.contains("visitorAccepts"), "the old visitorAccepts key must be gone")
    }

    @Test
    fun `CopperDyeData round-trips the visitorsSeen map`() {
        val original = CopperDyeData(
            visitorsSeen = mapOf(VisitorRarity.UNCOMMON to 12, VisitorRarity.MYTHIC to 4)
        )

        val decoded = json.decodeFromString<CopperDyeData>(Json.encodeToString(original))

        assertEquals(original.visitorsSeen, decoded.visitorsSeen)
    }

    @Test
    fun `mergeVisitorsSeen unions visitors across pages and derives per-tier totals (PBI 42)`() {
        // Each Logbook page-turn finalizes a separate screen, so pages arrive as separate merges.
        RngDataStore.mergeVisitorsSeen(mapOf("Alice" to VisitorEntry("Alice", VisitorRarity.RARE, 3)))
        RngDataStore.mergeVisitorsSeen(mapOf("Bob" to VisitorEntry("Bob", VisitorRarity.SPECIAL, 7)))

        // Both pages must contribute — NOT just the latest one (the bug this fixes).
        assertEquals(
            mapOf(VisitorRarity.RARE to 3, VisitorRarity.SPECIAL to 7),
            RngDataStore.getData().copperDye?.visitorsSeen
        )
        assertEquals(setOf("Alice", "Bob"), RngDataStore.getVisitorLogbook()?.visitors?.keys)
    }

    @Test
    fun `mergeVisitorsSeen is idempotent for a re-seen visitor (no double-count) (PBI 42)`() {
        RngDataStore.mergeVisitorsSeen(mapOf("Alice" to VisitorEntry("Alice", VisitorRarity.RARE, 3)))
        // Re-scanning the same visitor (e.g. paging back) must overwrite, not add — absolute count.
        RngDataStore.mergeVisitorsSeen(mapOf("Alice" to VisitorEntry("Alice", VisitorRarity.RARE, 5)))

        assertEquals(
            mapOf(VisitorRarity.RARE to 5),
            RngDataStore.getData().copperDye?.visitorsSeen
        )
        assertEquals(1, RngDataStore.getVisitorLogbook()?.visitors?.size)
    }

    @Test
    fun `mergeVisitorsSeen with a partial view never shrinks the prior union (PBI 42)`() {
        // A full capture across pages...
        RngDataStore.mergeVisitorsSeen(
            mapOf(
                "Alice" to VisitorEntry("Alice", VisitorRarity.RARE, 3),
                "Bob" to VisitorEntry("Bob", VisitorRarity.SPECIAL, 7)
            )
        )
        // ...then a later session that only views one page (e.g. after a restart) must NOT drop Bob;
        // the per-tier snapshot is re-derived from the FULL union, so SPECIAL stays at 7.
        RngDataStore.mergeVisitorsSeen(mapOf("Alice" to VisitorEntry("Alice", VisitorRarity.RARE, 4)))

        assertEquals(
            mapOf(VisitorRarity.RARE to 4, VisitorRarity.SPECIAL to 7),
            RngDataStore.getData().copperDye?.visitorsSeen
        )
    }

    @Test
    fun `mergeVisitorsSeen with an empty session does not clobber the prior union (PBI 42)`() {
        RngDataStore.mergeVisitorsSeen(mapOf("Alice" to VisitorEntry("Alice", VisitorRarity.RARE, 3)))
        RngDataStore.mergeVisitorsSeen(emptyMap())

        assertEquals(
            mapOf(VisitorRarity.RARE to 3),
            RngDataStore.getData().copperDye?.visitorsSeen
        )
    }

    @Test
    fun `VisitorLogbookData round-trips through JSON`() {
        val original = VisitorLogbookData(
            visitors = mapOf(
                "Alice" to VisitorEntry("Alice", VisitorRarity.RARE, 3),
                "Bob" to VisitorEntry("Bob", VisitorRarity.SPECIAL, 7)
            )
        )

        val decoded = json.decodeFromString<VisitorLogbookData>(Json.encodeToString(original))

        assertEquals(original, decoded)
    }

    @Test
    fun `visitorLogbook union is persisted in PlayerRngData but never reaches the sync payload (PBI 42)`() {
        RngDataStore.mergeVisitorsSeen(mapOf("Alice" to VisitorEntry("Alice", VisitorRarity.RARE, 3)))

        // The store snapshot (persisted to disk) carries the by-name union...
        assertTrue(RngDataStore.getData().visitorLogbook != null)

        // ...but the sync request only carries the derived per-tier copperDye, never the union.
        val syncEncoded = Json.encodeToString(
            SyncRngDataRequest(
                copperDye = RngDataStore.getData().copperDye,
                modTimestamp = 1_700_000_000_000L
            )
        )
        assertFalse(syncEncoded.contains("visitorLogbook"), "the by-name union must never be synced")
        assertTrue(syncEncoded.contains("visitorsSeen"), "the synced copper data stays per-tier")
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
