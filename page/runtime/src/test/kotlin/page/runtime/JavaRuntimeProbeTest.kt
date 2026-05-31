package page.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class JavaRuntimeProbeTest {

    private val modern = """
        Property settings:
            java.home = C:\Program Files\Java\jdk-25.0.2
            java.specification.version = 25
            java.version = 25.0.2
    """.trimIndent()

    private val legacy = """
        Property settings:
            java.home = /opt/jdk8
            java.specification.version = 1.8
            java.version = 1.8.0_432
    """.trimIndent()

    @Test
    fun parseSpecMajorReadsModernVersion() {
        assertEquals(25, JavaRuntimeProbe.parseSpecMajor(modern))
    }

    @Test
    fun parseSpecMajorHandlesLegacyOnePrefix() {
        assertEquals(8, JavaRuntimeProbe.parseSpecMajor(legacy))
    }

    @Test
    fun parseSpecMajorNullWhenAbsent() {
        assertNull(JavaRuntimeProbe.parseSpecMajor("no version here"))
    }

    @Test
    fun parseJavaHomeReadsPathWithSpaces() {
        assertEquals("C:\\Program Files\\Java\\jdk-25.0.2", JavaRuntimeProbe.parseJavaHome(modern))
    }

    @Test
    fun parseJavaHomeNullWhenAbsent() {
        assertNull(JavaRuntimeProbe.parseJavaHome("java.version = 25"))
    }

    @Test
    fun majorFromVersionStringHandlesBuildSuffixes() {
        assertEquals(21, JavaRuntimeProbe.majorFromVersionString("21.0.5+11"))
        assertEquals(17, JavaRuntimeProbe.majorFromVersionString("17.0.19-10"))
    }
}
