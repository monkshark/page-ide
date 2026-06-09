package page.workspace

import page.runtime.*
import page.workspace.*

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class FolderPackageRenameTest {

    @TempDir lateinit var temp: Path

    private fun reader(): (Path) -> String? = { p -> runCatching { Files.readString(p) }.getOrNull() }

    @Test
    fun `computePackageMap returns single mirror mapping`() {
        val folder = temp.resolve("alpha").also { Files.createDirectories(it) }
        Files.writeString(folder.resolve("A.kt"), "package alpha\n\nclass A\n")
        Files.writeString(folder.resolve("B.kt"), "package alpha\n\nclass B\n")

        val map = FolderPackageRename.computePackageMap(folder, "beta", reader())

        assertEquals(mapOf("alpha" to "beta"), map)
    }

    @Test
    fun `computePackageMap handles nested mirror`() {
        val folder = temp.resolve("alpha").also { Files.createDirectories(it) }
        val sub = folder.resolve("sub").also { Files.createDirectories(it) }
        Files.writeString(folder.resolve("A.kt"), "package alpha\n")
        Files.writeString(sub.resolve("X.kt"), "package alpha.sub\n")

        val map = FolderPackageRename.computePackageMap(folder, "beta", reader())

        assertEquals(mapOf("alpha" to "beta", "alpha.sub" to "beta.sub"), map)
    }

    @Test
    fun `computePackageMap preserves prefix package segments`() {
        val folder = temp.resolve("alpha").also { Files.createDirectories(it) }
        Files.writeString(folder.resolve("A.kt"), "package com.example.alpha\n")

        val map = FolderPackageRename.computePackageMap(folder, "beta", reader())

        assertEquals(mapOf("com.example.alpha" to "com.example.beta"), map)
    }

    @Test
    fun `computePackageMap skips files whose package does not mirror folder`() {
        val folder = temp.resolve("alpha").also { Files.createDirectories(it) }
        Files.writeString(folder.resolve("A.kt"), "package my.weird.path\n")

        val map = FolderPackageRename.computePackageMap(folder, "beta", reader())

        assertEquals(emptyMap<String, String>(), map)
    }

    @Test
    fun `computePackageMap skips files with no package declaration`() {
        val folder = temp.resolve("alpha").also { Files.createDirectories(it) }
        Files.writeString(folder.resolve("A.kt"), "class A\n")

        val map = FolderPackageRename.computePackageMap(folder, "beta", reader())

        assertEquals(emptyMap<String, String>(), map)
    }

    @Test
    fun `computePackageMap returns empty when old equals new`() {
        val folder = temp.resolve("alpha").also { Files.createDirectories(it) }
        Files.writeString(folder.resolve("A.kt"), "package alpha\n")

        val map = FolderPackageRename.computePackageMap(folder, "alpha", reader())

        assertEquals(emptyMap<String, String>(), map)
    }

    @Test
    fun `rewritePackageLine replaces package and preserves leading whitespace`() {
        val before = "  package alpha\n\nclass A\n"
        val after = FolderPackageRename.rewritePackageLine(before, "beta")
        assertEquals("  package beta\n\nclass A\n", after)
    }

    @Test
    fun `rewritePackageLine handles nested fqn`() {
        val before = "package com.example.alpha\n\nclass A\n"
        val after = FolderPackageRename.rewritePackageLine(before, "com.example.beta")
        assertEquals("package com.example.beta\n\nclass A\n", after)
    }

    @Test
    fun `rewritePackageLine returns null when no package exists`() {
        assertNull(FolderPackageRename.rewritePackageLine("class A\n", "beta"))
    }

    @Test
    fun `rewriteImports replaces single segment imports`() {
        val before = """
            package consumer

            import alpha.A
            import alpha.B

            class UseAlpha {
                val a = A()
            }
        """.trimIndent()
        val after = FolderPackageRename.rewriteImports(before, mapOf("alpha" to "beta"))
        val expected = """
            package consumer

            import beta.A
            import beta.B

            class UseAlpha {
                val a = A()
            }
        """.trimIndent()
        assertEquals(expected, after)
    }

    @Test
    fun `rewriteImports preserves wildcard and alias`() {
        val before = """
            import alpha.*
            import alpha.A as Aliased
        """.trimIndent()
        val after = FolderPackageRename.rewriteImports(before, mapOf("alpha" to "beta"))
        val expected = """
            import beta.*
            import beta.A as Aliased
        """.trimIndent()
        assertEquals(expected, after)
    }

    @Test
    fun `rewriteImports prefers longer prefix match`() {
        val before = """
            import alpha.X
            import alpha.sub.Y
        """.trimIndent()
        val map = mapOf("alpha" to "beta", "alpha.sub" to "beta.sub")
        val after = FolderPackageRename.rewriteImports(before, map)
        val expected = """
            import beta.X
            import beta.sub.Y
        """.trimIndent()
        assertEquals(expected, after)
    }

    @Test
    fun `rewriteImports respects word boundary`() {
        val before = """
            import alphabeta.X
            import alpha.Y
        """.trimIndent()
        val after = FolderPackageRename.rewriteImports(before, mapOf("alpha" to "beta"))
        val expected = """
            import alphabeta.X
            import beta.Y
        """.trimIndent()
        assertEquals(expected, after)
    }

    @Test
    fun `rewriteImports returns null when no import matches`() {
        val before = "import other.X\n"
        assertNull(FolderPackageRename.rewriteImports(before, mapOf("alpha" to "beta")))
    }

    @Test
    fun `rewriteImports replaces bare fqn import`() {
        val before = "import alpha\n"
        val after = FolderPackageRename.rewriteImports(before, mapOf("alpha" to "beta"))
        assertEquals("import beta\n", after)
    }

    @Test
    fun `rewriteFileInRenamedFolder updates package and sibling-package imports together`() {
        val before = """
            package complex.mid

            import complex.core.Base
            import complex.core.shout

            class Engine : Base("e") {
                fun loud() = shout("e")
            }
        """.trimIndent()
        val map = mapOf(
            "complex.core" to "lattice.core",
            "complex.mid" to "lattice.mid",
            "complex.app" to "lattice.app",
        )
        val after = FolderPackageRename.rewriteFileInRenamedFolder(before, map)
        val expected = """
            package lattice.mid

            import lattice.core.Base
            import lattice.core.shout

            class Engine : Base("e") {
                fun loud() = shout("e")
            }
        """.trimIndent()
        assertEquals(expected, after)
    }

    @Test
    fun `rewriteFileInRenamedFolder preserves wildcard and alias imports`() {
        val before = """
            package complex.app

            import complex.core.*
            import complex.core.Base as CoreBase
            import complex.mid.Engine as Power
            import complex.core.shout as loud
        """.trimIndent()
        val map = mapOf(
            "complex.core" to "lattice.core",
            "complex.mid" to "lattice.mid",
            "complex.app" to "lattice.app",
        )
        val after = FolderPackageRename.rewriteFileInRenamedFolder(before, map)
        val expected = """
            package lattice.app

            import lattice.core.*
            import lattice.core.Base as CoreBase
            import lattice.mid.Engine as Power
            import lattice.core.shout as loud
        """.trimIndent()
        assertEquals(expected, after)
    }

    @Test
    fun `rewriteFileInRenamedFolder returns null when nothing changes`() {
        val text = """
            package outside

            class A
        """.trimIndent()
        assertNull(FolderPackageRename.rewriteFileInRenamedFolder(text, mapOf("complex.core" to "lattice.core")))
    }

    @Test
    fun `rewriteFileInRenamedFolder still rewrites package when no imports exist`() {
        val before = "package complex.kernel\n\nclass A\n"
        val after = FolderPackageRename.rewriteFileInRenamedFolder(before, mapOf("complex.kernel" to "lattice.kernel"))
        assertEquals("package lattice.kernel\n\nclass A\n", after)
    }

    @Test
    fun `computePackageMap captures 4-layer mirror chain ignoring sibling free package`() {
        val root = temp.resolve("deep_graph").also { Files.createDirectories(it) }
        val base = root.resolve("base").also { Files.createDirectories(it) }
        val core = root.resolve("core").also { Files.createDirectories(it) }
        val mid = root.resolve("mid").also { Files.createDirectories(it) }
        val app = root.resolve("app").also { Files.createDirectories(it) }
        Files.writeString(base.resolve("Marker.kt"), "package deep_graph.base\n")
        Files.writeString(base.resolve("Result.kt"), "package deep_graph.base\n")
        Files.writeString(core.resolve("Service.kt"), "package deep_graph.core\n")
        Files.writeString(mid.resolve("Adapter.kt"), "package deep_graph.mid\n")
        Files.writeString(mid.resolve("Free.kt"), "package freebag.float\n")
        Files.writeString(app.resolve("Runner.kt"), "package deep_graph.app\n")

        val map = FolderPackageRename.computePackageMap(root, "lattice_deep", reader())

        val expected = mapOf(
            "deep_graph.base" to "lattice_deep.base",
            "deep_graph.core" to "lattice_deep.core",
            "deep_graph.mid" to "lattice_deep.mid",
            "deep_graph.app" to "lattice_deep.app",
        )
        assertEquals(expected, map)
    }

    @Test
    fun `rewriteFileInRenamedFolder preserves free package declaration while rewriting imports`() {
        val before = """
            package freebag.float

            import deep_graph.base.Result

            object FreeBucket {
                fun wrap(r: Result): String = "free:${'$'}{r.describe()}"
            }
        """.trimIndent()
        val map = mapOf(
            "deep_graph" to "lattice_deep",
            "deep_graph.base" to "lattice_deep.base",
            "deep_graph.core" to "lattice_deep.core",
            "deep_graph.mid" to "lattice_deep.mid",
            "deep_graph.app" to "lattice_deep.app",
        )
        val after = FolderPackageRename.rewriteFileInRenamedFolder(before, map)
        val expected = """
            package freebag.float

            import lattice_deep.base.Result

            object FreeBucket {
                fun wrap(r: Result): String = "free:${'$'}{r.describe()}"
            }
        """.trimIndent()
        assertEquals(expected, after)
    }

    @Test
    fun `computePackageMapForMove rebases nested mirror to new parent`() {
        val src = temp.resolve("src").also { Files.createDirectories(it) }
        val oldParent = src.resolve("com/example/old").also { Files.createDirectories(it) }
        val folder = oldParent.resolve("alpha").also { Files.createDirectories(it) }
        val sub = folder.resolve("sub").also { Files.createDirectories(it) }
        Files.writeString(folder.resolve("A.kt"), "package com.example.old.alpha\n")
        Files.writeString(sub.resolve("X.kt"), "package com.example.old.alpha.sub\n")

        val newParent = src.resolve("com/example/new").also { Files.createDirectories(it) }
        Files.writeString(newParent.resolve("Anchor.kt"), "package com.example.new\n")

        val map = FolderPackageRename.computePackageMapForMove(
            oldFolder = folder,
            newFolder = newParent.resolve("alpha"),
            workspaceRoot = src,
            readText = reader(),
        )

        val expected = mapOf(
            "com.example.old.alpha" to "com.example.new.alpha",
            "com.example.old.alpha.sub" to "com.example.new.alpha.sub",
        )
        assertEquals(expected, map)
    }

    @Test
    fun `computePackageMapForMove returns empty when same parent`() {
        val src = temp.resolve("src").also { Files.createDirectories(it) }
        val folder = src.resolve("alpha").also { Files.createDirectories(it) }
        Files.writeString(folder.resolve("A.kt"), "package alpha\n")

        val map = FolderPackageRename.computePackageMapForMove(
            oldFolder = folder,
            newFolder = folder,
            workspaceRoot = src,
            readText = reader(),
        )
        assertEquals(emptyMap<String, String>(), map)
    }

    @Test
    fun `planSingleFileMove targets only the moved file's import`() {
        val src = temp.resolve("src").also { Files.createDirectories(it) }
        val oldDir = src.resolve("com/example/util").also { Files.createDirectories(it) }
        val sibling = oldDir.resolve("Other.kt").also { Files.writeString(it, "package com.example.util\n") }
        val moved = oldDir.resolve("Moved.kt").also { Files.writeString(it, "package com.example.util\n\nclass Moved\n") }

        val newDir = src.resolve("com/example/core").also { Files.createDirectories(it) }
        Files.writeString(newDir.resolve("Anchor.kt"), "package com.example.core\n")

        val plan = FolderPackageRename.planSingleFileMove(
            oldFile = moved,
            newParent = newDir,
            workspaceRoot = src,
            readText = reader(),
        )
        assertEquals("com.example.util", plan?.oldPackage)
        assertEquals("com.example.core", plan?.newPackage)
        assertEquals("Moved", plan?.stem)
        assertEquals(
            mapOf("com.example.util.Moved" to "com.example.core.Moved"),
            plan?.importRewriteMap,
        )
        assertEquals("package com.example.util\n", Files.readString(sibling))
    }

    @Test
    fun `planSingleFileMove returns null when packages match`() {
        val src = temp.resolve("src").also { Files.createDirectories(it) }
        val dir = src.resolve("com/example").also { Files.createDirectories(it) }
        val file = dir.resolve("Self.kt").also { Files.writeString(it, "package com.example\n") }
        Files.writeString(dir.resolve("Sibling.kt"), "package com.example\n")

        val plan = FolderPackageRename.planSingleFileMove(
            oldFile = file,
            newParent = dir,
            workspaceRoot = src,
            readText = reader(),
        )
        assertNull(plan)
    }

    @Test
    fun `rewriteImports preserves free package alias unchanged`() {
        val before = """
            import deep_graph.base.Marker as Sig
            import deep_graph.core.Service as Engine
            import deep_graph.mid.Adapter as Hub
            import freebag.float.FreeBucket as Bag
        """.trimIndent()
        val map = mapOf(
            "deep_graph.base" to "lattice_deep.base",
            "deep_graph.core" to "lattice_deep.core",
            "deep_graph.mid" to "lattice_deep.mid",
        )
        val after = FolderPackageRename.rewriteImports(before, map)
        val expected = """
            import lattice_deep.base.Marker as Sig
            import lattice_deep.core.Service as Engine
            import lattice_deep.mid.Adapter as Hub
            import freebag.float.FreeBucket as Bag
        """.trimIndent()
        assertEquals(expected, after)
    }
}
