package page.app

import kotlin.test.Test
import kotlin.test.assertEquals

class InlayHintMappingTest {

    @Test
    fun `identity mapping with no hints round-trips`() {
        val m = ComposedFoldInlayMapping(IdentityFoldMapping, emptyList())
        for (o in 0..10) {
            assertEquals(o, m.originalToTransformed(o))
            assertEquals(o, m.transformedToOriginal(o))
        }
    }

    @Test
    fun `single hint inserted at offset 5 shifts later offsets`() {
        val hints = listOf(HintInsertion(foldOffset = 5, originalAnchor = 5, label = "X:"))
        val m = ComposedFoldInlayMapping(IdentityFoldMapping, hints)
        assertEquals(0, m.originalToTransformed(0))
        assertEquals(4, m.originalToTransformed(4))
        assertEquals(5, m.originalToTransformed(5))
        assertEquals(8, m.originalToTransformed(6))
        assertEquals(9, m.originalToTransformed(7))
    }

    @Test
    fun `transformedToOriginal snaps positions inside hint to anchor`() {
        val hints = listOf(HintInsertion(foldOffset = 5, originalAnchor = 5, label = "X:"))
        val m = ComposedFoldInlayMapping(IdentityFoldMapping, hints)
        assertEquals(0, m.transformedToOriginal(0))
        assertEquals(5, m.transformedToOriginal(5))
        assertEquals(5, m.transformedToOriginal(6))
        assertEquals(5, m.transformedToOriginal(7))
        assertEquals(6, m.transformedToOriginal(8))
        assertEquals(7, m.transformedToOriginal(9))
    }

    @Test
    fun `multiple hints accumulate shift in order`() {
        val hints = listOf(
            HintInsertion(foldOffset = 3, originalAnchor = 3, label = "a"),
            HintInsertion(foldOffset = 7, originalAnchor = 7, label = "bb"),
            HintInsertion(foldOffset = 10, originalAnchor = 10, label = "ccc"),
        )
        val m = ComposedFoldInlayMapping(IdentityFoldMapping, hints)
        assertEquals(2, m.originalToTransformed(2))
        assertEquals(3, m.originalToTransformed(3))
        assertEquals(5, m.originalToTransformed(4))
        assertEquals(8, m.originalToTransformed(7))
        assertEquals(11, m.originalToTransformed(8))
        assertEquals(13, m.originalToTransformed(10))
        assertEquals(17, m.originalToTransformed(11))
    }

    @Test
    fun `transformedToOriginal walks through accumulated shifts`() {
        val hints = listOf(
            HintInsertion(foldOffset = 3, originalAnchor = 3, label = "a"),
            HintInsertion(foldOffset = 7, originalAnchor = 7, label = "bb"),
        )
        val m = ComposedFoldInlayMapping(IdentityFoldMapping, hints)
        assertEquals(2, m.transformedToOriginal(2))
        assertEquals(3, m.transformedToOriginal(3))
        assertEquals(3, m.transformedToOriginal(4))
        assertEquals(4, m.transformedToOriginal(5))
        assertEquals(6, m.transformedToOriginal(7))
        assertEquals(7, m.transformedToOriginal(8))
        assertEquals(7, m.transformedToOriginal(9))
        assertEquals(7, m.transformedToOriginal(10))
        assertEquals(8, m.transformedToOriginal(11))
    }

    @Test
    fun `two hints at same fold offset shift together`() {
        val hints = listOf(
            HintInsertion(foldOffset = 4, originalAnchor = 4, label = "a"),
            HintInsertion(foldOffset = 4, originalAnchor = 4, label = "b"),
        )
        val m = ComposedFoldInlayMapping(IdentityFoldMapping, hints)
        assertEquals(4, m.originalToTransformed(4))
        assertEquals(7, m.originalToTransformed(5))
        assertEquals(4, m.transformedToOriginal(4))
        assertEquals(4, m.transformedToOriginal(5))
        assertEquals(4, m.transformedToOriginal(6))
        assertEquals(5, m.transformedToOriginal(7))
    }
}
