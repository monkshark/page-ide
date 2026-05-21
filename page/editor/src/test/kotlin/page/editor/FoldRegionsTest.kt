package page.editor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FoldRegionsTest {
    @Test
    fun detectSimpleBlock() {
        val text = "class A {\n    val x = 1\n}\n"
        val regions = FoldRegions.detect(text)
        assertEquals(listOf(FoldRegions.Region(0, 2)), regions)
    }

    @Test
    fun detectNestedBlocks() {
        val text = "fun f() {\n    if (x) {\n        y\n    }\n}\n"
        val regions = FoldRegions.detect(text)
        assertEquals(
            listOf(FoldRegions.Region(0, 4), FoldRegions.Region(1, 3)),
            regions.sortedBy { it.startLine }.sortedBy { it.startLine }
        )
    }

    @Test
    fun ignoreSingleLineBlocks() {
        val text = "fun f() { return 1 }\n"
        val regions = FoldRegions.detect(text)
        assertTrue(regions.isEmpty())
    }

    @Test
    fun ignoreBracesInsideStrings() {
        val text = "val s = \"{ not a block }\"\nclass A {\n}\n"
        val regions = FoldRegions.detect(text)
        assertEquals(listOf(FoldRegions.Region(1, 2)), regions)
    }

    @Test
    fun ignoreBracesInsideLineComments() {
        val text = "// { fake }\nclass A {\n    body\n}\n"
        val regions = FoldRegions.detect(text)
        assertEquals(listOf(FoldRegions.Region(1, 3)), regions)
    }

    @Test
    fun ignoreBracesInsideBlockComments() {
        val text = "/* {\nfake\n} */\nclass A {\n}\n"
        val regions = FoldRegions.detect(text)
        assertEquals(listOf(FoldRegions.Region(3, 4)), regions)
    }

    @Test
    fun unbalancedBracesIgnored() {
        val text = "}{}{\n"
        val regions = FoldRegions.detect(text)
        assertTrue(regions.isEmpty())
    }

    @Test
    fun segmentForBlock() {
        val text = "function foo() {\n  body\n}\nrest"
        val region = FoldRegions.Region(0, 2)
        val segs = FoldRegions.segmentsFor(text, listOf(region))
        assertEquals(1, segs.size)
        val seg = segs[0]
        assertEquals(16, seg.origStart)
        assertEquals(26, seg.origEnd)
        assertEquals(" ... }\n", seg.replacement)
    }

    @Test
    fun segmentForLastLineBlock() {
        val text = "function foo() {\n  body\n}"
        val region = FoldRegions.Region(0, 2)
        val segs = FoldRegions.segmentsFor(text, listOf(region))
        assertEquals(1, segs.size)
        val seg = segs[0]
        assertEquals(16, seg.origStart)
        assertEquals(text.length, seg.origEnd)
        assertEquals(" ... }", seg.replacement)
    }

    @Test
    fun segmentsAreSortedAndNonOverlapping() {
        val text = "fun f() {\n    if (x) {\n        y\n    }\n}\n"
        val outer = FoldRegions.Region(0, 4)
        val inner = FoldRegions.Region(1, 3)
        val segs = FoldRegions.segmentsFor(text, listOf(outer, inner))
        assertEquals(1, segs.size, "outer fold should swallow inner")
    }

    @Test
    fun originalToTransformedBeforeFold() {
        val text = "function foo() {\n  body\n}\nrest"
        val segs = FoldRegions.segmentsFor(text, listOf(FoldRegions.Region(0, 2)))
        assertEquals(5, FoldRegions.originalToTransformed(segs, 5))
    }

    @Test
    fun originalToTransformedInsideFoldClampsToStart() {
        val text = "function foo() {\n  body\n}\nrest"
        val segs = FoldRegions.segmentsFor(text, listOf(FoldRegions.Region(0, 2)))
        assertEquals(16, FoldRegions.originalToTransformed(segs, 20))
    }

    @Test
    fun originalToTransformedAfterFoldShifts() {
        val text = "function foo() {\n  body\n}\nrest"
        val segs = FoldRegions.segmentsFor(text, listOf(FoldRegions.Region(0, 2)))
        val origRest = text.indexOf("rest")
        val transformedRest = FoldRegions.originalToTransformed(segs, origRest)
        assertEquals(16 + " ... }\n".length, transformedRest)
    }

    @Test
    fun transformedToOriginalLeftHalfMapsToFoldStart() {
        val text = "function foo() {\n  body\n}\nrest"
        val segs = FoldRegions.segmentsFor(text, listOf(FoldRegions.Region(0, 2)))
        val transStart = segs[0].origStart
        val mid = transStart + segs[0].replacement.length / 2
        for (off in transStart until mid) {
            assertEquals(segs[0].origStart, FoldRegions.transformedToOriginal(segs, off),
                "left-half offset $off should map to origStart")
        }
    }

    @Test
    fun transformedToOriginalCloserMapsToCloserChars() {
        val text = "function foo() {\n  body\n}\nrest"
        val segs = FoldRegions.segmentsFor(text, listOf(FoldRegions.Region(0, 2)))
        val seg = segs[0]
        val closerStart = seg.origStart + seg.closerInRepStart
        val closerEnd = closerStart + seg.closerLength
        assertEquals(seg.closerOrigStart, FoldRegions.transformedToOriginal(segs, closerStart),
            "click before closer should map to closer start in original")
        assertEquals(seg.closerOrigStart + seg.closerLength, FoldRegions.transformedToOriginal(segs, closerEnd),
            "click after closer should map to position after closer in original")
    }

    @Test
    fun originalToTransformedCloserCharsLandOnVisibleCloser() {
        val text = "function foo() {\n  body\n}\nrest"
        val segs = FoldRegions.segmentsFor(text, listOf(FoldRegions.Region(0, 2)))
        val seg = segs[0]
        val closerStart = seg.origStart + seg.closerInRepStart
        val closerEnd = closerStart + seg.closerLength
        assertEquals(closerStart, FoldRegions.originalToTransformed(segs, seg.closerOrigStart),
            "original closer-start should land before visible closer")
        assertEquals(closerEnd, FoldRegions.originalToTransformed(segs, seg.closerOrigStart + seg.closerLength),
            "original closer-end should land after visible closer")
    }

    @Test
    fun closerCaretRoundTrip() {
        val text = "function foo() {\n  body\n}\nrest"
        val segs = FoldRegions.segmentsFor(text, listOf(FoldRegions.Region(0, 2)))
        val seg = segs[0]
        val closerEnd = seg.origStart + seg.closerInRepStart + seg.closerLength
        val orig = FoldRegions.transformedToOriginal(segs, closerEnd)
        assertEquals(closerEnd, FoldRegions.originalToTransformed(segs, orig),
            "clicking on closer must round-trip back to the same visible offset")
    }

    @Test
    fun transformedToOriginalRoundTrip() {
        val text = "function foo() {\n  body\n}\nrest"
        val segs = FoldRegions.segmentsFor(text, listOf(FoldRegions.Region(0, 2)))
        val origRest = text.indexOf("rest")
        val t = FoldRegions.originalToTransformed(segs, origRest)
        assertEquals(origRest, FoldRegions.transformedToOriginal(segs, t))
    }

    @Test
    fun foldedRegionAtDotsHits() {
        val text = "function foo() {\n  body\n}\nrest"
        val region = FoldRegions.Region(0, 2)
        val segs = FoldRegions.segmentsFor(text, listOf(region))
        val dotsStart = segs[0].origStart + segs[0].replacement.indexOf("...")
        for (off in dotsStart until dotsStart + 3) {
            val hit = FoldRegions.foldedRegionAt(text, listOf(region), off)
            assertEquals(region, hit, "expected hit at offset $off")
        }
    }

    @Test
    fun foldedRegionAtBraceAndSpacesMiss() {
        val text = "function foo() {\n  body\n}\nrest"
        val region = FoldRegions.Region(0, 2)
        val segs = FoldRegions.segmentsFor(text, listOf(region))
        val transStart = segs[0].origStart
        val rep = segs[0].replacement
        val leadingSpace = transStart + rep.indexOf(' ')
        val dotsStart = transStart + rep.indexOf("...")
        val trailingSpace = dotsStart + 3
        val brace = transStart + rep.indexOf('}')
        for (off in listOf(leadingSpace, trailingSpace, brace)) {
            val hit = FoldRegions.foldedRegionAt(text, listOf(region), off)
            assertEquals(null, hit, "expected miss at offset $off")
        }
    }

    @Test
    fun chainedBraceDoesNotFold() {
        val text = "fun f() {\n  list.map {\n    it + 1\n  }.sum()\n}\n"
        val regions = FoldRegions.detect(text)
        assertTrue(regions.any { it.startLine == 0 && it.endLine == 4 }, "outer block should fold: $regions")
        assertTrue(regions.none { it.startLine == 1 && it.endLine == 3 }, "chained lambda block should not fold: $regions")
    }

    @Test
    fun segmentForMultilineCommentUsesStarSlashCloser() {
        val text = "/*\n line\n*/\nrest"
        val region = FoldRegions.Region(0, 2)
        val segs = FoldRegions.segmentsFor(text, listOf(region))
        assertEquals(1, segs.size)
        assertEquals(" ... */\n", segs[0].replacement)
    }

    @Test
    fun segmentForImportListUsesNoCloser() {
        val text = "import a.A\nimport b.B\nimport c.C\nrest"
        val region = FoldRegions.Region(0, 2)
        val segs = FoldRegions.segmentsFor(text, listOf(region))
        assertEquals(1, segs.size)
        assertEquals(" ...\n", segs[0].replacement)
    }

    @Test
    fun segmentForParenListUsesParenCloser() {
        val text = "foo(\n  a,\n  b,\n)\nrest"
        val region = FoldRegions.Region(0, 3)
        val segs = FoldRegions.segmentsFor(text, listOf(region))
        assertEquals(1, segs.size)
        assertEquals(" ... )\n", segs[0].replacement)
    }

    @Test
    fun segmentForImportPrefixReplacesWholeLine() {
        val text = "import a.A\nimport b.B\nrest"
        val region = FoldRegions.Region(0, 1, placeholderPrefix = "import")
        val segs = FoldRegions.segmentsFor(text, listOf(region))
        assertEquals(1, segs.size)
        assertEquals("import ...\n", segs[0].replacement)
        assertEquals(0, segs[0].origStart)
        assertEquals(0, segs[0].closerLength)
    }

    @Test
    fun originalToTransformedAfterImportPrefixShifts() {
        val text = "import a.A\nimport b.B\nrest"
        val region = FoldRegions.Region(0, 1, placeholderPrefix = "import")
        val segs = FoldRegions.segmentsFor(text, listOf(region))
        val origRest = text.indexOf("rest")
        val t = FoldRegions.originalToTransformed(segs, origRest)
        assertEquals("import ...\n".length, t)
    }

    @Test
    fun foldedRegionAtImportPrefixHitsOverPrefixAndDots() {
        val text = "import a.A\nimport b.B\nrest"
        val region = FoldRegions.Region(0, 1, placeholderPrefix = "import")
        val segs = FoldRegions.segmentsFor(text, listOf(region))
        val rep = segs[0].replacement
        val dotsStart = segs[0].origStart + rep.indexOf("...")
        for (off in dotsStart until dotsStart + 3) {
            val hit = FoldRegions.foldedRegionAt(text, listOf(region), off)
            assertEquals(region, hit, "expected hit at offset $off")
        }
    }

    @Test
    fun foldedRegionAtOutsidePlaceholderMisses() {
        val text = "function foo() {\n  body\n}\nrest"
        val region = FoldRegions.Region(0, 2)
        val before = FoldRegions.foldedRegionAt(text, listOf(region), 0)
        assertEquals(null, before)
        val segs = FoldRegions.segmentsFor(text, listOf(region))
        val past = segs[0].origStart + segs[0].replacement.length + 1
        val after = FoldRegions.foldedRegionAt(text, listOf(region), past)
        assertEquals(null, after)
    }
}
