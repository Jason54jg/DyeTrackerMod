package com.dyetracker

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Proves the JVM unit-test source set compiles and runs under the Stonecutter/fabric-loom build.
 * See docs/delivery/33/33-2-junit-guide.md.
 */
class SanityTest {

    @Test
    fun `test source set is wired and runs`() {
        assertEquals(4, 2 + 2)
    }
}
