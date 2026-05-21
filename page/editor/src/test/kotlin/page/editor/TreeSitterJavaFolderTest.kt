package page.editor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TreeSitterJavaFolderTest {

    @Test
    fun emptyTextReturnsEmpty() {
        assertEquals(emptyList(), TreeSitterJavaFolder.detect(""))
    }

    @Test
    fun methodBodyFolds() {
        val text = """
            class A {
                void f() {
                    int x = 1;
                }
            }
        """.trimIndent() + "\n"
        val regions = TreeSitterJavaFolder.detect(text)
        assertTrue(regions.any { it.startLine == 0 && it.endLine == 4 }, "class body region missing: $regions")
        assertTrue(regions.any { it.startLine == 1 && it.endLine == 3 }, "method body region missing: $regions")
    }

    @Test
    fun interfaceBodyFolds() {
        val text = """
            interface I {
                void f();
                void g();
            }
        """.trimIndent() + "\n"
        val regions = TreeSitterJavaFolder.detect(text)
        assertTrue(regions.any { it.startLine == 0 && it.endLine == 3 }, "interface body region missing: $regions")
    }

    @Test
    fun enumBodyFolds() {
        val text = """
            enum E {
                A,
                B;
            }
        """.trimIndent() + "\n"
        val regions = TreeSitterJavaFolder.detect(text)
        assertTrue(regions.any { it.startLine == 0 && it.endLine == 3 }, "enum body region missing: $regions")
    }

    @Test
    fun blockCommentFolds() {
        val text = "/*\n line\n*/\nclass C {}\n"
        val regions = TreeSitterJavaFolder.detect(text)
        assertTrue(regions.any { it.startLine == 0 && it.endLine == 2 }, "block comment region missing: $regions")
    }

    @Test
    fun consecutiveImportsFoldAsImportGroup() {
        val text = """
            package p;

            import java.util.List;
            import java.util.Map;
            import java.util.Set;

            class C {}
        """.trimIndent() + "\n"
        val regions = TreeSitterJavaFolder.detect(text)
        val importRegion = regions.firstOrNull { it.placeholderPrefix == "import" }
        assertTrue(importRegion != null, "import region missing: $regions")
        assertEquals(2, importRegion!!.startLine, "import region should start at first import: $regions")
        assertEquals(4, importRegion.endLine, "import region should end at last import: $regions")
    }

    @Test
    fun singleImportDoesNotFold() {
        val text = """
            package p;

            import java.util.List;

            class C {}
        """.trimIndent() + "\n"
        val regions = TreeSitterJavaFolder.detect(text)
        assertTrue(
            regions.none { it.placeholderPrefix == "import" },
            "single import should not produce fold region: $regions",
        )
    }

    @Test
    fun chainedLambdaDoesNotFoldOverTrailingChain() {
        val text = """
            class A {
                int f(java.util.List<Integer> xs) {
                    return xs.stream().map(v -> {
                        return v * 2;
                    }).reduce(0, Integer::sum);
                }
            }
        """.trimIndent() + "\n"
        val regions = TreeSitterJavaFolder.detect(text)
        assertTrue(
            regions.none { it.startLine == 2 && it.endLine == 4 },
            "chained lambda block should not fold (would hide .reduce(...)): $regions",
        )
    }
}
