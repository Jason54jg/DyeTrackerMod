package com.dyetracker.rotation

/**
 * Authoritative dye-ID → bundled-sprite mapping for the dye rotation widget.
 *
 * The sprite PNGs live under [RESOURCE_DIR] on the classpath, named `<dyeId>.png` using our
 * snake_case dye IDs (matching the values of `InventoryUtils.DYE_DISPLAY_NAME_TO_ID` and
 * `packages/shared/src/constants/dyes.ts`). They were converted offline from the external
 * dyes browser-source repo — see `docs/delivery/31/sprites.md` for provenance and the exact
 * conversion command.
 *
 * Runtime texture upload is handled separately (task 31-3); this object only owns the mapping
 * from a dye ID to its resource path and its stable texture id, so no sprite path strings are
 * duplicated across the codebase.
 *
 * [DYE_IDS] must cover every value in `InventoryUtils.DYE_DISPLAY_NAME_TO_ID` so any rotation
 * dye the parser can recognize has art; both tables are maintained in lock-step.
 */
object DyeSprites {

    /** Classpath directory (under `resources/`) holding the bundled dye sprite PNGs. */
    const val RESOURCE_DIR = "assets/dyetracker/dyes"

    private const val FILE_EXTENSION = ".png"

    /** Prefix for the stable texture id used with `ImageTextureManager` / `SpriteWidget`. */
    private const val SPRITE_ID_PREFIX = "dyesprite/"

    /**
     * Every dye ID that has a bundled sprite. Listed in the same order as
     * `InventoryUtils.DYE_DISPLAY_NAME_TO_ID` for easy cross-checking.
     */
    val DYE_IDS: Set<String> = linkedSetOf(
        "matcha",
        "brick_red",
        "celeste",
        "byzantium",
        "flame",
        "sangria",
        "livid",
        "necron",
        "jade",
        "nadeshiko",
        "tentacle",
        "aquamarine",
        "archfiend",
        "bone",
        "carmine",
        "celadon",
        "copper",
        "cyclamen",
        "dark_purple",
        "dung",
        "emerald",
        "fossil",
        "frostbitten",
        "holly",
        "iceberg",
        "mango",
        "midnight",
        "mocha",
        "mythological",
        "nyanza",
        "pearlescent",
        "pelt",
        "periwinkle",
        "secret",
        "wild_strawberry",
        "bingo_blue",
        "chocolate",
        "pure_black",
        "pure_white",
        "pure_blue",
        "pure_yellow",
        // Animated Fire-Sale dyes (PBI 33). Kept in lock-step with DYE_DISPLAY_NAME_TO_ID.
        "aurora",
        "black_ice",
        "lava",
        "lucky",
        "ocean",
        "pastel_sky",
        "portal",
        "rose",
        "snowflake",
        "treasure",
        "warden",
    )

    /** True if a bundled sprite exists for [dyeId]. */
    fun has(dyeId: String): Boolean = dyeId in DYE_IDS

    /**
     * Classpath resource path for a dye's bundled PNG, e.g. `assets/dyetracker/dyes/dung.png`.
     * Load with `getResourceAsStream("/" + resourcePath(dyeId))`.
     */
    fun resourcePath(dyeId: String): String = "$RESOURCE_DIR/$dyeId$FILE_EXTENSION"

    /**
     * Stable texture id for a dye, consumed by `ImageTextureManager.upload(...)` (task 31-3)
     * and `SpriteWidget(spriteId(dyeId))` (task 31-5). Deterministic per dye ID so the
     * uploader and the widget always agree on the key.
     */
    fun spriteId(dyeId: String): String = "$SPRITE_ID_PREFIX$dyeId"
}
