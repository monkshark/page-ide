package page.shared.path

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FilePathTest {

    @Test
    fun normalizesBackslashesAndTrailingSlash() {
        assertEquals("page/atlas/render", FilePath.of("page\\atlas\\render\\").toString())
        assertEquals("/page/atlas", FilePath.of("/page//atlas").toString())
    }

    @Test
    fun fileNameIsLastSegment() {
        assertEquals("Overview.kt", FilePath.of("/page/atlas/Overview.kt").fileName)
        assertEquals("solo", FilePath.of("solo").fileName)
    }

    @Test
    fun parentWalksUpToRootThenNull() {
        val p = FilePath.of("/page/atlas")
        assertEquals("/page", p.parent?.toString())
        assertEquals("/", p.parent?.parent?.toString())
        assertNull(p.parent?.parent?.parent)
        assertNull(FilePath.of("solo").parent)
    }

    @Test
    fun startsWithRespectsSegmentBoundaries() {
        val root = FilePath.of("/page/atlas")
        assertTrue(FilePath.of("/page/atlas/render/X.kt").startsWith(root))
        assertTrue(root.startsWith(root))
        assertFalse(FilePath.of("/page/atlasx/X.kt").startsWith(root))
        assertTrue(FilePath.of("/page/atlas").startsWith(FilePath.of("/")))
        assertTrue(FilePath.of("anything").startsWith(FilePath.of("")))
    }

    @Test
    fun relativizeAndResolveRoundTrip() {
        val root = FilePath.of("/page/atlas")
        val child = FilePath.of("/page/atlas/render/X.kt")
        assertEquals("render/X.kt", root.relativize(child).toString())
        assertEquals(child, root.resolve("render").resolve("X.kt"))
        assertEquals("page/atlas/X.kt", FilePath.of("/").relativize(FilePath.of("/page/atlas/X.kt")).toString())
    }

    @Test
    fun segmentsSplitOnSlash() {
        assertEquals(listOf("page", "atlas", "X.kt"), FilePath.of("/page/atlas/X.kt").segments)
    }

    @Test
    fun equalityByNormalizedValue() {
        assertEquals(FilePath.of("page\\atlas"), FilePath.of("page/atlas/"))
    }
}
