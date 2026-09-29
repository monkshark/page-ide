package page.atlas.graph

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProjectMapBuilderTest {

    private fun project(vararg entries: String): Path {
        val root = Files.createTempDirectory("map-")
        for (e in entries) {
            val p = root.resolve(e)
            Files.createDirectories(p.parent)
            Files.writeString(p, if (e.endsWith(".md")) "" else "x")
        }
        return root
    }

    private fun sources(root: Path): List<Path> = Files.walk(root).use { s ->
        s.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".kt") }.toList()
    }

    private fun edge(root: Path, from: String, to: String) = root.resolve(from) to root.resolve(to)

    @Test
    fun `build modules become the parts`() {
        val root = project(
            "app/build.gradle.kts", "app/src/main/kotlin/Main.kt", "app/src/main/kotlin/Panel.kt",
            "core/build.gradle.kts", "core/src/main/kotlin/Model.kt",
        )
        val map = ProjectMapBuilder.build(root, sources(root), emptyList())
        assertEquals(setOf("app", "core"), map.parts.map { it.name }.toSet())
        assertEquals(2, map.part("app")!!.fileCount)
    }

    @Test
    fun `a file belongs to the innermost module around it`() {
        val root = project(
            "page/app/build.gradle.kts", "page/app/A.kt",
            "page/core/build.gradle.kts", "page/core/B.kt",
            "tools/build.gradle.kts", "tools/C.kt",
        )
        val map = ProjectMapBuilder.build(root, sources(root), emptyList())
        assertEquals("app", map.partOf(root.resolve("page/app/A.kt"))!!.name)
        assertEquals("tools", map.partOf(root.resolve("tools/C.kt"))!!.name)
    }

    @Test
    fun `without modules the first folder that splits the code decides`() {
        val root = project(
            "src/main/kotlin/com/acme/web/Routes.kt",
            "src/main/kotlin/com/acme/web/Views.kt",
            "src/main/kotlin/com/acme/store/Db.kt",
            "src/main/kotlin/com/acme/Main.kt",
        )
        val map = ProjectMapBuilder.build(root, sources(root), emptyList())
        assertEquals(setOf("web", "store", "acme"), map.parts.map { it.name }.toSet())
    }

    @Test
    fun `imports between files become one weighted link between parts`() {
        val root = project(
            "app/build.gradle.kts", "app/A.kt", "app/B.kt",
            "core/build.gradle.kts", "core/C.kt",
        )
        val imports = listOf(edge(root, "app/A.kt", "core/C.kt"), edge(root, "app/B.kt", "core/C.kt"), edge(root, "app/A.kt", "app/B.kt"))
        val map = ProjectMapBuilder.build(root, sources(root), imports)
        assertEquals(listOf(MapLink("app", "core", 2)), map.links)
    }

    @Test
    fun `what nothing relies on sits on top and what everything relies on sits at the bottom`() {
        val root = project(
            "app/build.gradle.kts", "app/A.kt",
            "feature/build.gradle.kts", "feature/F.kt",
            "core/build.gradle.kts", "core/C.kt",
        )
        val imports = listOf(edge(root, "app/A.kt", "feature/F.kt"), edge(root, "feature/F.kt", "core/C.kt"), edge(root, "app/A.kt", "core/C.kt"))
        val map = ProjectMapBuilder.build(root, sources(root), imports)
        assertEquals(0, map.part("core")!!.layer)
        assertEquals(1, map.part("feature")!!.layer)
        assertEquals(2, map.part("app")!!.layer)
        assertEquals(3, map.layers)
    }

    @Test
    fun `a dependency cycle still gets layers instead of hanging`() {
        val root = project("a/build.gradle.kts", "a/A.kt", "b/build.gradle.kts", "b/B.kt")
        val imports = listOf(edge(root, "a/A.kt", "b/B.kt"), edge(root, "b/B.kt", "a/A.kt"))
        val map = ProjectMapBuilder.build(root, sources(root), imports)
        assertEquals(2, map.parts.size)
        assertEquals(map.part("a")!!.layer, map.part("b")!!.layer, "parts that need each other share a row")
    }

    @Test
    fun `too many parts fold the smallest into Other`() {
        val names = (1..15).map { "m$it" }
        val entries = names.flatMapIndexed { i, n ->
            listOf("$n/build.gradle.kts") + (0..i).map { "$n/F$it.kt" }
        }
        val root = project(*entries.toTypedArray())
        val map = ProjectMapBuilder.build(root, sources(root), emptyList())
        assertEquals(ProjectMapBuilder.MAX_PARTS, map.parts.size)
        val other = map.part(ProjectMapBuilder.OTHER_ID)!!
        assertEquals(1 + 2 + 3 + 4, other.fileCount)
        assertTrue(map.part("m15") != null, "the biggest parts stay on their own")
    }

    @Test
    fun `relations say what a part relies on and what relies on it`() {
        val root = project("app/build.gradle.kts", "app/A.kt", "core/build.gradle.kts", "core/C.kt", "ui/build.gradle.kts", "ui/U.kt")
        val imports = listOf(edge(root, "app/A.kt", "core/C.kt"), edge(root, "ui/U.kt", "core/C.kt"), edge(root, "app/A.kt", "ui/U.kt"))
        val map = ProjectMapBuilder.build(root, sources(root), imports)
        assertEquals(setOf("app", "ui"), map.relationsOf("core").usedBy)
        assertEquals(setOf("core", "ui"), map.relationsOf("app").uses)
    }

    @Test
    fun `going inside a part splits it by its own folders`() {
        val root = project(
            "lsp/build.gradle.kts",
            "lsp/src/main/kotlin/page/lsp/client/Client.kt",
            "lsp/src/main/kotlin/page/lsp/client/Transport.kt",
            "lsp/src/main/kotlin/page/lsp/features/Hover.kt",
            "core/build.gradle.kts", "core/C.kt",
        )
        val imports = listOf(edge(root, "lsp/src/main/kotlin/page/lsp/features/Hover.kt", "lsp/src/main/kotlin/page/lsp/client/Client.kt"))
        val map = ProjectMapBuilder.build(root, sources(root), imports)
        val inner = ProjectMapBuilder.inside(map, "lsp", root, imports)
        assertEquals("lsp", inner.title)
        assertEquals(setOf("client", "features"), inner.parts.map { it.name }.toSet())
        assertEquals(listOf(MapLink(inner.parts.first { it.name == "features" }.id, inner.parts.first { it.name == "client" }.id, 1)), inner.links)
    }

    @Test
    fun `files outside the project root are ignored`() {
        val root = project("a/build.gradle.kts", "a/A.kt")
        val stray = Files.createTempFile("stray", ".kt")
        val map = ProjectMapBuilder.build(root, sources(root) + stray, emptyList())
        assertNull(map.partOf(stray))
    }

    @Test
    fun `an empty project gives an empty map`() {
        val root = Files.createTempDirectory("empty-")
        assertTrue(ProjectMapBuilder.build(root, emptyList(), emptyList()).isEmpty())
    }

    @Test
    fun `stats say how big a part is and what it is written in`() {
        val files = listOf(Path.of("a.kt"), Path.of("b.kt"), Path.of("c.java"))
        assertEquals("3 files · Kotlin, Java", StatsDescriber.describe(null, "x", files))
        assertEquals("1 file", StatsDescriber.describe(null, "x", listOf(Path.of("notes.txt"))))
    }

    @Test
    fun `a readme's first sentence describes its folder`() {
        val root = project("svc/README.md", "svc/S.kt")
        Files.writeString(root.resolve("svc/README.md"), "# Service\n\nHandles `orders` and billing. More detail follows here.\n")
        val d = DocsDescriber(root).describe(root.resolve("svc"), "svc", listOf(root.resolve("svc/S.kt")))
        assertEquals("Handles orders and billing.", d)
    }

    @Test
    fun `the internals doc summary line describes a module`() {
        val root = project("lsp/L.kt", "docs/internals/lsp/main.md")
        Files.writeString(
            root.resolve("docs/internals/lsp/main.md"),
            "# LSP\n\n> `page:lsp` — Language Server Protocol client layer. Starts servers.\n\n> English: [main_en.md](x)\n",
        )
        val d = DocsDescriber(root).describe(root.resolve("lsp"), "lsp", listOf(root.resolve("lsp/L.kt")))
        assertEquals("Language Server Protocol client layer.", d)
    }

    @Test
    fun `with no docs the description falls back to stats`() {
        val root = project("x/X.kt")
        assertEquals("1 file · Kotlin", DocsDescriber(root).describe(root.resolve("x"), "x", listOf(root.resolve("x/X.kt"))))
    }

    @Test
    fun `test code stays off the map`() {
        val root = project("app/build.gradle.kts", "app/src/main/kotlin/A.kt", "app/src/test/kotlin/ATest.kt", "core/build.gradle.kts", "core/C.kt")
        val map = ProjectMapBuilder.build(root, sources(root), emptyList())
        assertEquals(1, map.part("app")!!.fileCount)
    }

    @Test
    fun `loose files in a modular project go to Other instead of getting their own boxes`() {
        val root = project("app/build.gradle.kts", "app/A.kt", "core/build.gradle.kts", "core/C.kt", "build.gradle.kts", "Root.kt", "scripts/Tool.kt")
        val map = ProjectMapBuilder.build(root, sources(root), emptyList())
        assertEquals(setOf("app", "core", "Other"), map.parts.map { it.name }.toSet())
        assertEquals(2, map.part(ProjectMapBuilder.OTHER_ID)!!.fileCount)
    }

    @Test
    fun `declared module dependencies draw links without reading any imports`() {
        val root = project("app/build.gradle.kts", "app/A.kt", "core/build.gradle.kts", "core/C.kt")
        val map = ProjectMapBuilder.build(root, sources(root), emptyList(), moduleLinks = listOf(root.resolve("app") to root.resolve("core")))
        assertEquals(listOf(MapLink("app", "core", 0)), map.links)
        assertEquals(1, map.part("app")!!.layer)
    }

    @Test
    fun `code samples and markup in a readme are not taken as the description`() {
        val root = project("svc/README.md", "svc/S.kt")
        Files.writeString(
            root.resolve("svc/README.md"),
            "# svc\n\n```kotlin\napplication {\n}\n```\n\n**Runs the order service.** Also does more.\n",
        )
        assertEquals("Runs the order service.", DocsDescriber(root).describe(root.resolve("svc"), "svc", listOf(root.resolve("svc/S.kt"))))
    }

    @Test
    fun `a small part everything relies on is kept over a bigger loner`() {
        val big = (1..14).map { "m$it" }
        val entries = big.flatMap { n -> listOf("$n/build.gradle.kts") + (1..5).map { "$n/F$it.kt" } } +
            listOf("core/build.gradle.kts", "core/C.kt")
        val root = project(*entries.toTypedArray())
        val links = big.take(4).map { root.resolve(it) to root.resolve("core") }
        val map = ProjectMapBuilder.build(root, sources(root), emptyList(), moduleLinks = links)
        assertTrue(map.part("core") != null, "core has one file but four parts rely on it")
    }

    @Test
    fun `build scripts are not code on the map`() {
        val root = project("lsp/build.gradle.kts", "lsp/src/main/kotlin/p/lsp/client/A.kt", "lsp/src/main/kotlin/p/lsp/features/B.kt", "core/build.gradle.kts", "core/C.kt")
        val files = Files.walk(root).use { s -> s.filter { Files.isRegularFile(it) }.toList() }
        val map = ProjectMapBuilder.build(root, files, emptyList())
        val inner = ProjectMapBuilder.inside(map, "lsp", root, emptyList())
        assertEquals(setOf("client", "features"), inner.parts.map { it.name }.toSet())
    }

    @Test
    fun `an ellipsis does not end the description`() {
        val root = project("app/A.kt", "docs/internals/app/main.md")
        Files.writeString(root.resolve("docs/internals/app/main.md"), "# App\n\n> `Main.kt` — `application { ... }` entry point and window state\n")
        assertEquals("application { ... } entry point and window state", DocsDescriber(root).describe(root.resolve("app"), "app", listOf(root.resolve("app/A.kt"))))
    }

    @Test
    fun `Other sits at the bottom and never decides anyone's row`() {
        val entries = (1..14).flatMap { n -> listOf("m$n/build.gradle.kts") + (1..(20 - n)).map { "m$n/F$it.kt" } }
        val root = project(*entries.toTypedArray())
        val folded = listOf("m14", "m13", "m12")
        val links = folded.map { root.resolve("m1") to root.resolve(it) } + listOf(root.resolve(folded.first()) to root.resolve("m2"))
        val map = ProjectMapBuilder.build(root, sources(root), emptyList(), moduleLinks = links)
        assertEquals(0, map.part(ProjectMapBuilder.OTHER_ID)!!.layer)
        assertEquals(0, map.part("m2")!!.layer)
    }
}
