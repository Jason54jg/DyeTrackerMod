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
 * PBI 63: the same for the dungeon, nucleus and experimentation meters.
 */
class RngMeterDataTest {

    private val json = Json { ignoreUnknownKeys = true }

    // Mirrors DataPersistence's private Json flags, so a decode here behaves like loading data.json.
    private val persistenceJson = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    // The mergeVisitorsSeen tests mutate the RngDataStore singleton; reset it after each
    // test so no residual copperDye / visitorLogbook state can leak into other tests in this JVM.
    @AfterTest
    fun tearDown() {
        RngDataStore.clear()
    }

    @Test
    fun `PlayerRngData hasData is false when empty`() {
        assertFalse(PlayerRngData().hasData())
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
    fun `TreasureDyeData round-trips through JSON with the per-tier catch keys (PBI 58)`() {
        val original = TreasureDyeData(goodCatches = 50_000, greatCatches = 5_000, outstandingCatches = 500)

        val encoded = Json.encodeToString(original)
        assertTrue(encoded.contains("goodCatches"), "must serialize the goodCatches key")
        assertTrue(encoded.contains("greatCatches"), "must serialize the greatCatches key")
        assertTrue(encoded.contains("outstandingCatches"), "must serialize the outstandingCatches key")

        val decoded = json.decodeFromString<TreasureDyeData>(encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `incrementTreasureCatch bumps only the matching tier and accumulates (PBI 58)`() {
        RngDataStore.incrementTreasureCatch(TreasureQuality.GOOD)
        RngDataStore.incrementTreasureCatch(TreasureQuality.GOOD)
        RngDataStore.incrementTreasureCatch(TreasureQuality.GREAT)
        RngDataStore.incrementTreasureCatch(TreasureQuality.OUTSTANDING)

        val treasure = RngDataStore.getData().treasureDye
        assertEquals(2, treasure?.goodCatches)
        assertEquals(1, treasure?.greatCatches)
        assertEquals(1, treasure?.outstandingCatches)
    }

    @Test
    fun `PlayerRngData hasData is true when a treasureDye block is present (PBI 58)`() {
        val withTreasure = PlayerRngData(treasureDye = TreasureDyeData(outstandingCatches = 1))
        assertTrue(withTreasure.hasData())
    }

    @Test
    fun `SyncRngDataRequest carries treasureDye with the per-tier catch keys (PBI 58)`() {
        val request = SyncRngDataRequest(
            treasureDye = TreasureDyeData(goodCatches = 3, greatCatches = 2, outstandingCatches = 1),
            modTimestamp = 1_700_000_000_000L
        )

        val encoded = Json.encodeToString(request)
        assertTrue(encoded.contains("treasureDye"), "sync payload should carry treasureDye when present")
        assertTrue(encoded.contains("goodCatches"), "treasureDye must use the goodCatches key")
        assertTrue(encoded.contains("greatCatches"), "treasureDye must use the greatCatches key")
        assertTrue(encoded.contains("outstandingCatches"), "treasureDye must use the outstandingCatches key")
    }

    @Test
    fun `legacy data json without a treasureDye key deserializes to null (PBI 58 forward-compat)`() {
        // A data.json written before PBI 58 has no treasureDye key; the field must default to null.
        val legacy = """
            {
              "archfiendDye": { "highClassDiceRolls": 3, "archfiendDiceRolls": 1 }
            }
        """.trimIndent()

        val data = json.decodeFromString<PlayerRngData>(legacy)

        assertEquals(null, data.treasureDye)
        assertTrue(data.hasData())
    }

    @Test
    fun `FossilDyeData round-trips through JSON with the excavations key (PBI 66)`() {
        val original = FossilDyeData(excavations = 1_234)

        val encoded = Json.encodeToString(original)
        assertTrue(encoded.contains("excavations"), "must serialize the excavations key")

        val decoded = json.decodeFromString<FossilDyeData>(encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `DarkPurpleDyeData round-trips through JSON with the auctionsAttended key (PBI 66)`() {
        val original = DarkPurpleDyeData(auctionsAttended = 42)

        val encoded = Json.encodeToString(original)
        assertTrue(encoded.contains("auctionsAttended"), "must serialize the auctionsAttended key")

        val decoded = json.decodeFromString<DarkPurpleDyeData>(encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `incrementExcavation accumulates the Fossil Dye excavation count (PBI 66)`() {
        RngDataStore.incrementExcavation()
        RngDataStore.incrementExcavation()
        RngDataStore.incrementExcavation()

        assertEquals(3, RngDataStore.getData().fossilDye?.excavations)
    }

    @Test
    fun `incrementDarkAuction accumulates the Dark Purple Dye auction count (PBI 66)`() {
        RngDataStore.incrementDarkAuction()
        RngDataStore.incrementDarkAuction()

        assertEquals(2, RngDataStore.getData().darkPurpleDye?.auctionsAttended)
    }

    @Test
    fun `clear resets the Fossil and Dark Purple Dye counts (PBI 66)`() {
        RngDataStore.incrementExcavation()
        RngDataStore.incrementDarkAuction()

        RngDataStore.clear()

        assertEquals(null, RngDataStore.getData().fossilDye)
        assertEquals(null, RngDataStore.getData().darkPurpleDye)
    }

    @Test
    fun `PlayerRngData hasData is true when only a fossilDye block is present (PBI 66)`() {
        assertTrue(PlayerRngData(fossilDye = FossilDyeData(excavations = 1)).hasData())
    }

    @Test
    fun `PlayerRngData hasData is true when only a darkPurpleDye block is present (PBI 66)`() {
        assertTrue(PlayerRngData(darkPurpleDye = DarkPurpleDyeData(auctionsAttended = 1)).hasData())
    }

    @Test
    fun `SyncRngDataRequest carries fossilDye and darkPurpleDye with their count keys (PBI 66)`() {
        val request = SyncRngDataRequest(
            fossilDye = FossilDyeData(excavations = 7),
            darkPurpleDye = DarkPurpleDyeData(auctionsAttended = 3),
            modTimestamp = 1_700_000_000_000L
        )

        val encoded = Json.encodeToString(request)
        assertTrue(encoded.contains("\"fossilDye\":{\"excavations\":7}"), "sync payload should carry fossilDye.excavations")
        assertTrue(
            encoded.contains("\"darkPurpleDye\":{\"auctionsAttended\":3}"),
            "sync payload should carry darkPurpleDye.auctionsAttended"
        )
    }

    @Test
    fun `legacy data json without fossilDye or darkPurpleDye keys deserializes both to null (PBI 66 forward-compat)`() {
        // A data.json written before PBI 66 has neither key; both fields must default to null.
        val legacy = """
            {
              "treasureDye": { "goodCatches": 4, "greatCatches": 2, "outstandingCatches": 1 }
            }
        """.trimIndent()

        val data = persistenceJson.decodeFromString<PlayerRngData>(legacy)

        assertEquals(null, data.fossilDye)
        assertEquals(null, data.darkPurpleDye)
        assertTrue(data.hasData())
    }

    @Test
    fun `HollyDyeData round-trips through JSON with the giftRewards key (PBI 66)`() {
        val original = HollyDyeData(giftRewards = mapOf("RARE|+20,000 Coins" to 2, "SWEET|Winter Sack" to 1))

        val encoded = Json.encodeToString(original)
        assertTrue(encoded.contains("giftRewards"), "must serialize the giftRewards key")

        val decoded = json.decodeFromString<HollyDyeData>(encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `incrementGiftReward counts each raw rarity and reward key (PBI 66)`() {
        RngDataStore.incrementGiftReward("RARE", "+20,000 Coins")
        RngDataStore.incrementGiftReward("RARE", "+20,000 Coins")
        RngDataStore.incrementGiftReward("SWEET", "Winter Sack")

        assertEquals(
            mapOf("RARE|+20,000 Coins" to 2, "SWEET|Winter Sack" to 1),
            RngDataStore.getData().hollyDye?.giftRewards
        )
    }

    @Test
    fun `incrementGiftReward truncates a long reward to 100 chars in the key (PBI 66)`() {
        val longReward = "x".repeat(150)

        RngDataStore.incrementGiftReward("RARE", longReward)

        assertEquals(
            mapOf("RARE|${"x".repeat(100)}" to 1),
            RngDataStore.getData().hollyDye?.giftRewards
        )
    }

    @Test
    fun `incrementGiftReward sends a new reward to the overflow key once 499 real keys are stored (PBI 66)`() {
        repeat(499) { RngDataStore.incrementGiftReward("COMMON", "reward $it") }

        RngDataStore.incrementGiftReward("RARE", "brand new reward")
        RngDataStore.incrementGiftReward("COMMON", "reward 0")

        val rewards = RngDataStore.getData().hollyDye?.giftRewards.orEmpty()
        assertEquals(1, rewards["OTHER|other"], "a new reward past the cap must count under OTHER|other")
        assertEquals(null, rewards["RARE|brand new reward"])
        assertEquals(2, rewards["COMMON|reward 0"], "an existing key must still increment itself")
        assertEquals(500, rewards.size, "the map must never exceed the backend's 500-key limit")
    }

    @Test
    fun `incrementGiftReward still adds a new reward as its own key with 498 real keys stored (PBI 66)`() {
        repeat(498) { RngDataStore.incrementGiftReward("COMMON", "reward $it") }

        RngDataStore.incrementGiftReward("RARE", "brand new reward")

        val rewards = RngDataStore.getData().hollyDye?.giftRewards.orEmpty()
        assertEquals(1, rewards["RARE|brand new reward"])
        assertEquals(null, rewards["OTHER|other"])
        assertEquals(499, rewards.size)
    }

    @Test
    fun `incrementGiftReward keeps sending new rewards to the overflow key at 500 keys (PBI 66)`() {
        repeat(499) { RngDataStore.incrementGiftReward("COMMON", "reward $it") }
        RngDataStore.incrementGiftReward("RARE", "first overflow")

        RngDataStore.incrementGiftReward("RARE", "second overflow")
        RngDataStore.incrementGiftReward("SWEET", "third overflow")

        val rewards = RngDataStore.getData().hollyDye?.giftRewards.orEmpty()
        assertEquals(3, rewards["OTHER|other"])
        assertEquals(500, rewards.size)
    }

    @Test
    fun `clear resets the Holly Dye gift counts (PBI 66)`() {
        RngDataStore.incrementGiftReward("RARE", "+20,000 Coins")

        RngDataStore.clear()

        assertEquals(null, RngDataStore.getData().hollyDye)
    }

    @Test
    fun `PlayerRngData hasData is true when only a hollyDye block is present (PBI 66)`() {
        assertTrue(PlayerRngData(hollyDye = HollyDyeData(giftRewards = mapOf("RARE|+20,000 Coins" to 1))).hasData())
    }

    @Test
    fun `SyncRngDataRequest carries hollyDye giftRewards (PBI 66)`() {
        val request = SyncRngDataRequest(
            hollyDye = HollyDyeData(giftRewards = mapOf("SWEET|Winter Sack" to 4)),
            modTimestamp = 1_700_000_000_000L
        )

        val encoded = Json.encodeToString(request)
        assertTrue(
            encoded.contains("\"hollyDye\":{\"giftRewards\":{\"SWEET|Winter Sack\":4}}"),
            "sync payload should carry hollyDye.giftRewards"
        )
    }

    @Test
    fun `legacy data json without a hollyDye key deserializes to null (PBI 66 forward-compat)`() {
        val legacy = """
            {
              "fossilDye": { "excavations": 3 }
            }
        """.trimIndent()

        val data = persistenceJson.decodeFromString<PlayerRngData>(legacy)

        assertEquals(null, data.hollyDye)
        assertTrue(data.hasData())
    }

    @Test
    fun `MochaDyeData round-trips through JSON with tier names as potionsByTier keys (PBI 66)`() {
        val original = MochaDyeData(potionsByTier = mapOf(PotionTier.VII to 2))

        val encoded = Json.encodeToString(original)
        assertEquals("{\"potionsByTier\":{\"VII\":2}}", encoded)

        val decoded = json.decodeFromString<MochaDyeData>(encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `incrementBrewedPotion counts potions per tier (PBI 66)`() {
        RngDataStore.incrementBrewedPotion(PotionTier.VII)
        RngDataStore.incrementBrewedPotion(PotionTier.VII)
        RngDataStore.incrementBrewedPotion(PotionTier.I)

        assertEquals(
            mapOf(PotionTier.VII to 2, PotionTier.I to 1),
            RngDataStore.getData().mochaDye?.potionsByTier
        )
    }

    @Test
    fun `clear resets the Mocha Dye potion counts (PBI 66)`() {
        RngDataStore.incrementBrewedPotion(PotionTier.VIII)

        RngDataStore.clear()

        assertEquals(null, RngDataStore.getData().mochaDye)
    }

    @Test
    fun `PlayerRngData hasData is true when only a mochaDye block is present (PBI 66)`() {
        assertTrue(PlayerRngData(mochaDye = MochaDyeData(potionsByTier = mapOf(PotionTier.III to 1))).hasData())
    }

    @Test
    fun `SyncRngDataRequest carries mochaDye potionsByTier (PBI 66)`() {
        val request = SyncRngDataRequest(
            mochaDye = MochaDyeData(potionsByTier = mapOf(PotionTier.VII to 2)),
            modTimestamp = 1_700_000_000_000L
        )

        val encoded = Json.encodeToString(request)
        assertTrue(
            encoded.contains("\"mochaDye\":{\"potionsByTier\":{\"VII\":2}}"),
            "sync payload should carry mochaDye.potionsByTier"
        )
    }

    @Test
    fun `legacy data json without a mochaDye key deserializes to null (PBI 66 forward-compat)`() {
        val legacy = """
            {
              "hollyDye": { "giftRewards": { "RARE|+20,000 Coins": 1 } }
            }
        """.trimIndent()

        val data = persistenceJson.decodeFromString<PlayerRngData>(legacy)

        assertEquals(null, data.mochaDye)
        assertTrue(data.hasData())
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
              "nyanzaDye": { "commissionsCompleted": 208 }
            }
        """.trimIndent()

        val data = json.decodeFromString<PlayerRngData>(legacy)

        assertEquals(208, data.nyanzaDye?.commissionsCompleted)
        assertTrue(data.hasData())
    }

    @Test
    fun `legacy data json with dungeon, nucleus and experimentation meters decodes with copper intact (PBI 63)`() {
        // A data.json written by a pre-PBI-63 mod build still carries the meter keys; the trimmed
        // PlayerRngData must ignore them and keep every surviving block.
        val legacy = """
            {
              "dungeonMeters":{"M7":{"floor":"M7","storedXp":100}},
              "nucleusMeter":{"storedXp":5},
              "experimentationMeter":null,
              "copperDye":{"visitorsSeen":{"RARE":3,"SPECIAL":7}}
            }
        """.trimIndent()

        val data = persistenceJson.decodeFromString<PlayerRngData>(legacy)

        assertEquals(
            mapOf(VisitorRarity.RARE to 3, VisitorRarity.SPECIAL to 7),
            data.copperDye?.visitorsSeen
        )
        assertTrue(data.hasData())
    }
}
