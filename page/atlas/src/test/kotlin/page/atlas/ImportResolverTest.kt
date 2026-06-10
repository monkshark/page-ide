package page.atlas

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.io.TempDir
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
    fun `unresolved import returns null`(@TempDir root: Path) {
        val active = root.resolve("Main.kt")
        Files.writeString(active, "package main")
        assertNull(ImportResolver.resolve(RawImport("java.util.List", false), active, WorkspaceIndex(root)))
        assertNull(ImportResolver.resolve(RawImport("express", false), root.resolve("app.ts"), WorkspaceIndex(root)))
    }
}
