package page.editor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TreeSitterKotlinFolderTest {

    @Test
    fun emptyTextReturnsEmpty() {
        assertEquals(emptyList(), TreeSitterKotlinFolder.detect(""))
    }

    @Test
    fun singleLineFunctionDoesNotFold() {
        val text = "fun f() = 1\n"
        assertEquals(emptyList(), TreeSitterKotlinFolder.detect(text))
    }

    @Test
    fun functionBodyFolds() {
        val text = """
            fun f() {
                val x = 1
            }
        """.trimIndent() + "\n"
        val regions = TreeSitterKotlinFolder.detect(text)
        assertTrue(regions.any { it.startLine == 0 && it.endLine == 2 }, "function body region missing: $regions")
    }

    @Test
    fun classBodyFolds() {
        val text = """
            class A {
                val x = 1
            }
        """.trimIndent() + "\n"
        val regions = TreeSitterKotlinFolder.detect(text)
        assertTrue(regions.any { it.startLine == 0 && it.endLine == 2 }, "class body region missing: $regions")
    }

    @Test
    fun multilineCommentFolds() {
        val text = "/*\n line\n*/\nfun g() = 1\n"
        val regions = TreeSitterKotlinFolder.detect(text)
        assertTrue(regions.any { it.startLine == 0 && it.endLine == 2 }, "multiline comment region missing: $regions")
    }

    @Test
    fun importListFolds() {
        val text = """
            package p

            import a.A
            import b.B
            import c.C

            fun h() = 1
        """.trimIndent() + "\n"
        val regions = TreeSitterKotlinFolder.detect(text)
        assertTrue(
            regions.any { it.startLine == 2 && it.endLine == 4 },
            "import list region missing: $regions",
        )
    }

    @Test
    fun nestedBracesProduceMultipleRegions() {
        val text = """
            class A {
                fun f() {
                    val x = 1
                }
            }
        """.trimIndent() + "\n"
        val regions = TreeSitterKotlinFolder.detect(text)
        assertTrue(regions.any { it.startLine == 0 && it.endLine == 4 }, "outer class region missing: $regions")
        assertTrue(regions.any { it.startLine == 1 && it.endLine == 3 }, "inner function region missing: $regions")
    }

    @Test
    fun chainedLambdaDoesNotFold() {
        val text = """
            fun f(numbers: List<Int>): Int {
                return numbers.map { value ->
                    value * 2
                }.sum()
            }
        """.trimIndent() + "\n"
        val regions = TreeSitterKotlinFolder.detect(text)
        assertTrue(
            regions.none { it.startLine == 1 && it.endLine == 3 },
            "chained lambda should not produce a fold region (would hide .sum()): $regions",
        )
    }

    @Test
    fun lambdaBodyFolds() {
        val text = """
            val k = listOf(1, 2).map {
                it + 1
            }
        """.trimIndent() + "\n"
        val regions = TreeSitterKotlinFolder.detect(text)
        assertTrue(regions.any { it.startLine == 0 && it.endLine == 2 }, "lambda region missing: $regions")
    }

    @Test
    fun whenExpressionFolds() {
        val text = """
            fun w(x: Int) = when (x) {
                1 -> "a"
                else -> "b"
            }
        """.trimIndent() + "\n"
        val regions = TreeSitterKotlinFolder.detect(text)
        assertTrue(regions.any { it.startLine == 0 && it.endLine == 3 }, "when region missing: $regions")
    }

    @Test
    fun regionsAreSortedByStartLine() {
        val text = """
            class A {
                fun a() {
                    val x = 1
                }
                fun b() {
                    val y = 2
                }
            }
        """.trimIndent() + "\n"
        val regions = TreeSitterKotlinFolder.detect(text)
        val starts = regions.map { it.startLine }
        assertEquals(starts.sorted(), starts, "regions not sorted: $regions")
    }

    @Test
    fun importListEndsAtLastImportEvenWithTrailingComments() {
        val text = """
            package sample

            import kotlin.math.abs
            import kotlin.math.sqrt

            // doc comment ending with paren (foo)
            // another comment
            class C
        """.trimIndent() + "\n"
        val regions = TreeSitterKotlinFolder.detect(text)
        val importRegion = regions.firstOrNull { it.startLine == 2 }
        assertTrue(importRegion != null, "import region missing: $regions")
        assertEquals(
            3,
            importRegion!!.endLine,
            "import region must stop at last import line, not absorb trailing comments: $regions",
        )
    }

    @Test
    fun unicodeCharactersDoNotShiftLines() {
        val text = "// 한글 코멘트\nfun f() {\n    val x = 1\n}\n"
        val regions = TreeSitterKotlinFolder.detect(text)
        assertTrue(regions.any { it.startLine == 1 && it.endLine == 3 }, "post-unicode function region missing: $regions")
    }
}
