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

class GoModResolverTest {

    private fun write(path: Path, content: String): Path {
        Files.createDirectories(path.parent)
        Files.writeString(path, content)
        return path
    }

    private fun resolve(root: Path, active: Path, target: String): Path? =
        ImportResolver.resolve(RawImport(target, false), active, WorkspaceIndex(root))

    @Test
    fun `module-prefixed import resolves to exact package dir`(@TempDir root: Path) {
        write(root.resolve("go.mod"), "module example.com/app\n\ngo 1.22\n")
        val target = write(root.resolve("internal/db/conn.go"), "package db")
        val active = write(root.resolve("main.go"), "package main")
        assertEquals(target, resolve(root, active, "example.com/app/internal/db"))
    }

    @Test
    fun `module path disambiguates duplicate last-segment dirs`(@TempDir root: Path) {
        write(root.resolve("go.mod"), "module example.com/app\n")
        write(root.resolve("internal/db/x.go"), "package db")
        val target = write(root.resolve("service/db/y.go"), "package db")
        val active = write(root.resolve("main.go"), "package main")
        assertEquals(target, resolve(root, active, "example.com/app/service/db"))
    }

    @Test
    fun `import equal to module path resolves to module root`(@TempDir root: Path) {
        write(root.resolve("go.mod"), "module example.com/app\n")
        val target = write(root.resolve("app.go"), "package app")
        val active = write(root.resolve("sub/main.go"), "package main")
        assertEquals(target, resolve(root, active, "example.com/app"))
    }

    @Test
    fun `trailing comment on module directive is tolerated`(@TempDir root: Path) {
        write(root.resolve("go.mod"), "module example.com/app // primary module\n")
        val target = write(root.resolve("util/u.go"), "package util")
        val active = write(root.resolve("main.go"), "package main")
        assertEquals(target, resolve(root, active, "example.com/app/util"))
    }

    @Test
    fun `missing go_mod falls back to last-segment match`(@TempDir root: Path) {
        val target = write(root.resolve("store/s.go"), "package store")
        val active = write(root.resolve("main.go"), "package main")
        assertEquals(target, resolve(root, active, "anything/store"))
    }

    @Test
    fun `stdlib import stays external`(@TempDir root: Path) {
        write(root.resolve("go.mod"), "module example.com/app\n")
        write(root.resolve("internal/db/conn.go"), "package db")
        val active = write(root.resolve("main.go"), "package main")
        assertNull(resolve(root, active, "fmt"))
    }

    @Test
    fun `third-party import outside module stays external`(@TempDir root: Path) {
        write(root.resolve("go.mod"), "module example.com/app\n")
        write(root.resolve("internal/db/conn.go"), "package db")
        val active = write(root.resolve("main.go"), "package main")
        assertNull(resolve(root, active, "github.com/gin-gonic/gin"))
    }
}
