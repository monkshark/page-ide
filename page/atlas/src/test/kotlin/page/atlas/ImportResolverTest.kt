package page.atlas

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.io.TempDir
import page.atlas.analyzer.DeclarationIndex
import page.atlas.analyzer.ImportExtractor
import page.atlas.analyzer.ImportResolver
import page.atlas.analyzer.RawImport
import page.atlas.analyzer.WorkspaceIndex

class ImportResolverTest {

    @Test
    fun `kotlin dotted import resolves to workspace file`(@TempDir root: Path) {
        val target = root.resolve("src/main/kotlin/com/example/util/Helper.kt")
        Files.createDirectories(target.parent)
        Files.writeString(target, "package com.example.util")
        val active = root.resolve("src/main/kotlin/com/example/Main.kt")
        Files.writeString(active.also { Files.createDirectories(it.parent) }, "package com.example")
        val resolved = ImportResolver.resolve(
            RawImport("com.example.util.Helper", false), active, WorkspaceIndex(root),
        )
        assertEquals(target, resolved)
    }

    @Test
    fun `kotlin member import falls back to enclosing file`(@TempDir root: Path) {
        val target = root.resolve("src/com/example/util/Helper.kt")
        Files.createDirectories(target.parent)
        Files.writeString(target, "package com.example.util")
        val active = root.resolve("src/com/example/Main.kt")
        Files.writeString(active, "package com.example")
        val resolved = ImportResolver.resolve(
            RawImport("com.example.util.Helper.helperFun", false), active, WorkspaceIndex(root),
        )
        assertEquals(target, resolved)
    }

    @Test
    fun `js relative import probes extensions`(@TempDir root: Path) {
        val target = root.resolve("src/util.ts")
        Files.createDirectories(target.parent)
        Files.writeString(target, "export const x = 1")
        val active = root.resolve("src/app.ts")
        Files.writeString(active, "")
        val resolved = ImportResolver.resolve(RawImport("./util", true), active, WorkspaceIndex(root))
        assertEquals(target, resolved)
    }

    @Test
    fun `python relative import walks up directories`(@TempDir root: Path) {
        val target = root.resolve("pkg/mod.py")
        Files.createDirectories(target.parent)
        Files.writeString(target, "")
        val active = root.resolve("pkg/sub/main.py")
        Files.createDirectories(active.parent)
        Files.writeString(active, "")
        val resolved = ImportResolver.resolve(RawImport("..mod", true), active, WorkspaceIndex(root))
        assertEquals(target, resolved)
    }

    @Test
    fun `python absolute module import resolves to module file`(@TempDir root: Path) {
        val target = root.resolve("app/services/auth.py")
        Files.createDirectories(target.parent)
        Files.writeString(target, "")
        val active = root.resolve("app/main.py")
        Files.writeString(active, "")
        val resolved = ImportResolver.resolve(RawImport("app.services.auth", false), active, WorkspaceIndex(root))
        assertEquals(target, resolved)
    }

    @Test
    fun `python package import resolves to init file`(@TempDir root: Path) {
        val target = root.resolve("app/services/__init__.py")
        Files.createDirectories(target.parent)
        Files.writeString(target, "")
        val active = root.resolve("app/main.py")
        Files.writeString(active, "")
        val resolved = ImportResolver.resolve(RawImport("app.services", false), active, WorkspaceIndex(root))
        assertEquals(target, resolved)
    }

    @Test
    fun `python from-package import resolves to init file`(@TempDir root: Path) {
        val target = root.resolve("app/services/__init__.py")
        Files.createDirectories(target.parent)
        Files.writeString(target, "")
        Files.writeString(root.resolve("app/services/auth.py"), "")
        val active = root.resolve("app/main.py")
        Files.writeString(active, "")
        val resolved = ImportResolver.resolve(
            RawImport("app.services", false, listOf("auth")), active, WorkspaceIndex(root),
        )
        assertEquals(target, resolved)
    }

    @Test
    fun `python stdlib import stays external`(@TempDir root: Path) {
        val active = root.resolve("app/main.py")
        Files.createDirectories(active.parent)
        Files.writeString(active, "")
        assertNull(ImportResolver.resolve(RawImport("os.path", false), active, WorkspaceIndex(root)))
    }

    @Test
    fun `go import resolves to first file in package directory`(@TempDir root: Path) {
        val target = root.resolve("pkg/util/a.go")
        Files.createDirectories(target.parent)
        Files.writeString(target, "package util")
        Files.writeString(root.resolve("pkg/util/b.go"), "package util")
        val active = root.resolve("main.go")
        Files.writeString(active, "package main")
        val resolved = ImportResolver.resolve(
            RawImport("example.com/pkg/util", false), active, WorkspaceIndex(root),
        )
        assertEquals(target, resolved)
    }

    @Test
    fun `rust crate import resolves to module file`(@TempDir root: Path) {
        val target = root.resolve("src/x.rs")
        Files.createDirectories(target.parent)
        Files.writeString(target, "pub struct Item;")
        val active = root.resolve("src/main.rs")
        Files.writeString(active, "")
        val resolved = ImportResolver.resolve(RawImport("crate::x::Item", true), active, WorkspaceIndex(root))
        assertEquals(target, resolved)
    }

    @Test
    fun `dart package import resolves into lib directory`(@TempDir root: Path) {
        val target = root.resolve("lib/widgets/button.dart")
        Files.createDirectories(target.parent)
        Files.writeString(target, "class Button {}")
        val active = root.resolve("lib/main.dart")
        Files.writeString(active, "")
        val resolved = ImportResolver.resolve(
            RawImport("package:my_app/widgets/button.dart", false), active, WorkspaceIndex(root),
        )
        assertEquals(target, resolved)
    }

    @Test
    fun `dart relative import resolves against file directory`(@TempDir root: Path) {
        val target = root.resolve("lib/models/user.dart")
        Files.createDirectories(target.parent)
        Files.writeString(target, "class User {}")
        val active = root.resolve("lib/pages/home.dart")
        Files.createDirectories(active.parent)
        Files.writeString(active, "")
        val resolved = ImportResolver.resolve(
            RawImport("../models/user.dart", true), active, WorkspaceIndex(root),
        )
        assertEquals(target, resolved)
    }

    @Test
    fun `dart sdk import stays external`(@TempDir root: Path) {
        val active = root.resolve("lib/main.dart")
        Files.createDirectories(active.parent)
        Files.writeString(active, "")
        assertNull(ImportResolver.resolve(RawImport("dart:async", false), active, WorkspaceIndex(root)))
    }

    @Test
    fun `unresolved import returns null`(@TempDir root: Path) {
        val active = root.resolve("Main.kt")
        Files.writeString(active, "package main")
        assertNull(ImportResolver.resolve(RawImport("java.util.List", false), active, WorkspaceIndex(root)))
        assertNull(ImportResolver.resolve(RawImport("express", false), root.resolve("app.ts"), WorkspaceIndex(root)))
    }

    private fun declIndex(root: Path): DeclarationIndex =
        DeclarationIndex(WorkspaceIndex(root)) { file -> ImportExtractor.analyze(file, Files.readString(file)) }
            .also { it.refreshIfStale() }

    @Test
    fun `kotlin import resolves via declaration index when filename differs`(@TempDir root: Path) {
        val model = root.resolve("src/page/atlas/graph/GraphModel.kt")
        Files.createDirectories(model.parent)
        Files.writeString(
            model,
            "package page.atlas.graph\n\ndata class GraphSlice(val a: Int)\nclass GraphNode(val b: Int)",
        )
        val active = root.resolve("src/page/atlas/render/Canvas.kt")
        Files.createDirectories(active.parent)
        Files.writeString(active, "package page.atlas.render")
        val index = WorkspaceIndex(root)
        val decls = declIndex(root)
        assertEquals(model, ImportResolver.resolve(RawImport("page.atlas.graph.GraphSlice", false), active, index, decls))
        assertEquals(model, ImportResolver.resolve(RawImport("page.atlas.graph.GraphNode", false), active, index, decls))
    }

    @Test
    fun `kotlin member import resolves to declaring file via index`(@TempDir root: Path) {
        val model = root.resolve("util/Helpers.kt")
        Files.createDirectories(model.parent)
        Files.writeString(model, "package app.util\n\nobject Strings")
        val active = root.resolve("Main.kt")
        Files.writeString(active, "package app")
        val decls = declIndex(root)
        assertEquals(
            model,
            ImportResolver.resolve(RawImport("app.util.Strings.join", false), active, WorkspaceIndex(root), decls),
        )
    }

    @Test
    fun `index collision falls back to filename heuristic`(@TempDir root: Path) {
        val a = root.resolve("a/Config.kt")
        Files.createDirectories(a.parent)
        Files.writeString(a, "package app\nclass Config")
        val b = root.resolve("b/Settings.kt")
        Files.createDirectories(b.parent)
        Files.writeString(b, "package app\nclass Config")
        val active = root.resolve("Main.kt")
        Files.writeString(active, "package app")
        val resolved =
            ImportResolver.resolve(RawImport("app.Config", false), active, WorkspaceIndex(root), declIndex(root))
        assertNull(resolved)
    }
}
