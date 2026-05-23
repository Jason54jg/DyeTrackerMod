package com.dyetracker.rotation

import com.dyetracker.events.InventoryUtils
import kotlin.test.Test
import kotlin.test.fail

/**
 * Guards the three mod dye lists that must stay in lock-step (resolves follow-up #62):
 *  1. `InventoryUtils.DYE_DISPLAY_NAME_TO_ID` values (every recognizable compendium/rotation dye)
 *  2. `DyeSprites.DYE_IDS` (every dye ID that claims a bundled sprite)
 *  3. the bundled PNG files under `assets/dyetracker/dyes/` on the classpath
 *
 * A future dye addition that updates only one or two of these will fail here with a message
 * naming the offending dye ID(s), instead of silently shipping an unrenderable dye or an orphan
 * sprite. Pure JVM — reads the PNG names from the test classpath, no Minecraft runtime.
 */
class DyeSpritesDriftTest {

    private val nameMapIds: Set<String> = InventoryUtils.dyeDisplayNameToIdValues().toSet()
    private val spriteIds: Set<String> = DyeSprites.DYE_IDS
    private val bundledPngIds: Set<String> = readBundledPngIds()

    @Test
    fun `every recognizable dye id has a registered sprite`() {
        val missing = nameMapIds - spriteIds
        if (missing.isNotEmpty()) {
            fail(
                "DYE_DISPLAY_NAME_TO_ID has ${missing.size} id(s) absent from DyeSprites.DYE_IDS " +
                    "(no art for a recognizable dye): ${missing.sorted()}",
            )
        }
    }

    @Test
    fun `every registered sprite id has a bundled PNG`() {
        val missing = spriteIds - bundledPngIds
        if (missing.isNotEmpty()) {
            fail(
                "DyeSprites.DYE_IDS has ${missing.size} id(s) with no bundled PNG under " +
                    "${DyeSprites.RESOURCE_DIR}/: ${missing.sorted()}",
            )
        }
    }

    @Test
    fun `there are no orphan PNGs without a registered sprite id`() {
        val orphans = bundledPngIds - spriteIds
        if (orphans.isNotEmpty()) {
            fail(
                "${orphans.size} bundled PNG(s) under ${DyeSprites.RESOURCE_DIR}/ have no matching " +
                    "DyeSprites.DYE_IDS entry: ${orphans.sorted()}",
            )
        }
    }

    /**
     * Each sprite id must be loadable via the documented `DyeSprites.resourcePath` accessor, so the
     * runtime loader (`getResourceAsStream("/" + resourcePath(id))`) cannot 404 at render time.
     */
    @Test
    fun `every sprite id resolves a loadable classpath resource`() {
        val unloadable = spriteIds.filter { id ->
            javaClass.getResourceAsStream("/" + DyeSprites.resourcePath(id)) == null
        }
        if (unloadable.isNotEmpty()) {
            fail(
                "${unloadable.size} DyeSprites.DYE_IDS entries are not loadable from the classpath " +
                    "via resourcePath(): ${unloadable.sorted()}",
            )
        }
    }

    private fun readBundledPngIds(): Set<String> {
        val dirUrl = javaClass.getResource("/" + DyeSprites.RESOURCE_DIR)
            ?: fail("Bundled dye sprite dir not found on classpath: ${DyeSprites.RESOURCE_DIR}")
        val dir = java.io.File(dirUrl.toURI())
        val pngs = dir.listFiles { _, name -> name.endsWith(PNG_SUFFIX) }
            ?: fail("Could not list bundled dye sprite dir: ${dir.absolutePath}")
        return pngs.map { it.name.removeSuffix(PNG_SUFFIX) }.toSet()
    }

    private companion object {
        const val PNG_SUFFIX = ".png"
    }
}
