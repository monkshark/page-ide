package page.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GlobalStatusBarTest {

    @Test
    fun `java ext maps to jdk with matching version`() {
        val info = runtimeInfoFor(
            activeExt = "java",
            runtimeVersions = mapOf("java" to "21.0.2"),
            runtimeSources = mapOf("java" to "managed"),
            runtimeBuildFileVersions = mapOf("java" to "21"),
        )
        assertEquals("JDK 21.0.2", info?.first)
        assertEquals("jdk", info?.second)
        assertEquals("from managed", info?.third)
    }

    @Test
    fun `build-file version mismatch adds warning label and explanatory tooltip`() {
        val info = runtimeInfoFor(
            activeExt = "java",
            runtimeVersions = mapOf("java" to "17.0.1"),
            runtimeSources = mapOf("java" to "system"),
            runtimeBuildFileVersions = mapOf("java" to "21"),
        )
        assertTrue(info!!.first.contains("⚠"), "mismatch should flag the label")
        assertEquals("Project requires 21 (system), using 17.0.1", info.third)
    }

    @Test
    fun `unknown version falls back to question mark and no tooltip`() {
        val info = runtimeInfoFor(
            activeExt = "rs",
            runtimeVersions = emptyMap(),
            runtimeSources = emptyMap(),
            runtimeBuildFileVersions = emptyMap(),
        )
        assertEquals("Rust ?", info?.first)
        assertEquals("rust-runtime", info?.second)
        assertNull(info?.third)
    }

    @Test
    fun `unrecognized extension yields no runtime info`() {
        val info = runtimeInfoFor(
            activeExt = "txt",
            runtimeVersions = mapOf("java" to "21"),
            runtimeSources = emptyMap(),
            runtimeBuildFileVersions = emptyMap(),
        )
        assertNull(info)
    }
}
