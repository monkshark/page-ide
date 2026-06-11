package page.atlas

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import page.atlas.analyzer.ImportGraphProvider
import page.atlas.graph.EdgeKind
import page.atlas.graph.GraphSlice
import page.atlas.graph.NodeKind

class ImportGraphProviderTest {

    @Test
    fun `slice has active center with workspace and external nodes`(@TempDir root: Path) {
        val helper = root.resolve("src/com/example/util/Helper.kt")
        Files.createDirectories(helper.parent)
        Files.writeString(helper, "package com.example.util")
        val active = root.resolve("src/com/example/Main.kt")
        val text = """
            package com.example

            import com.example.util.Helper
            import java.util.List
        """.trimIndent()
        Files.writeString(active, text)
        val slice = ImportGraphProvider(root).nodesForFile(active, text)
        assertEquals(3, slice.nodes.size)
        assertEquals(2, slice.edges.size)
        val activeNode = slice.nodes.single { it.kind == NodeKind.ACTIVE }
        assertEquals("Main.kt", activeNode.label)
        val workspaceNode = slice.nodes.single { it.kind == NodeKind.WORKSPACE_FILE }
        assertEquals(helper.toAbsolutePath().normalize(), workspaceNode.path)
        val external = slice.nodes.single { it.kind == NodeKind.EXTERNAL }
        assertEquals("java.util.List", external.label)
        assertEquals(null, external.path)
        assertTrue(slice.edges.all { it.from == activeNode.id })
    }

    @Test
    fun `dart slice resolves package imports and promotes snake case extends`(@TempDir root: Path) {
        val basePage = root.resolve("lib/pages/base_page.dart")
        Files.createDirectories(basePage.parent)
        Files.writeString(basePage, "class BasePage {}\n\nmixin Trackable {}")
        val homePage = root.resolve("lib/pages/home_page.dart")
        Files.writeString(
            homePage,
            """
            import 'base_page.dart';

            class HomePage extends BasePage with Trackable {}
            """.trimIndent(),
        )
        val active = root.resolve("lib/main.dart")
        val text = """
            import 'dart:async';
            import 'package:flutter/material.dart';
            import 'package:flutter_app/pages/home_page.dart';
        """.trimIndent()
        Files.writeString(active, text)
        val slice = ImportGraphProvider(root).nodesForFile(active, text)
        val ids = slice.nodes.associateBy { it.label }
        assertEquals(NodeKind.WORKSPACE_FILE, ids.getValue("home_page.dart").kind)
        assertEquals(NodeKind.WORKSPACE_FILE, ids.getValue("base_page.dart").kind)
        assertEquals(NodeKind.EXTERNAL, ids.getValue("dart:async").kind)
        assertEquals(NodeKind.EXTERNAL, ids.getValue("package:flutter/material.dart").kind)
        val extendsEdge = slice.edges.single {
            it.from == ids.getValue("home_page.dart").id && it.to == ids.getValue("base_page.dart").id
        }
        assertEquals(EdgeKind.EXTENDS, extendsEdge.kind)
    }

    @Test
    fun `duplicate imports are deduplicated`(@TempDir root: Path) {
        val active = root.resolve("main.py")
        val text = """
            import json
            import json
        """.trimIndent()
        Files.writeString(active, text)
        val slice = ImportGraphProvider(root).nodesForFile(active, text)
        assertEquals(2, slice.nodes.size)
        assertEquals(1, slice.edges.size)
    }

    @Test
    fun `same input yields equal slices`(@TempDir root: Path) {
        val active = root.resolve("main.py")
        val text = "import json"
        Files.writeString(active, text)
        val provider = ImportGraphProvider(root)
        assertEquals(provider.nodesForFile(active, text), provider.nodesForFile(active, text))
    }

    @Test
    fun `node count is capped at 100`(@TempDir root: Path) {
        val active = root.resolve("main.py")
        val text = (1..101).joinToString("\n") { "import module$it" }
        Files.writeString(active, text)
        val slice = ImportGraphProvider(root).nodesForFile(active, text)
        assertEquals(100, slice.nodes.size)
        assertEquals(99, slice.edges.size)
    }

    @Test
    fun `unsupported extension yields empty slice`(@TempDir root: Path) {
        val active = root.resolve("notes.txt")
        Files.writeString(active, "import x")
        assertEquals(GraphSlice.EMPTY, ImportGraphProvider(root).nodesForFile(active, "import x"))
    }

    @Test
    fun `transitive dependencies are followed`(@TempDir root: Path) {
        val util = root.resolve("pkg/util.py")
        Files.createDirectories(util.parent)
        Files.writeString(util, "import json")
        val service = root.resolve("pkg/service.py")
        Files.writeString(service, "from .util import helper")
        val active = root.resolve("pkg/main.py")
        val text = "from .service import run"
        Files.writeString(active, text)
        val slice = ImportGraphProvider(root).nodesForFile(active, text)
        assertEquals(4, slice.nodes.size)
        assertEquals(3, slice.edges.size)
        val serviceId = service.toAbsolutePath().normalize().toString()
        val utilId = util.toAbsolutePath().normalize().toString()
        assertTrue(slice.edges.any { it.from == serviceId && it.to == utilId })
        assertTrue(slice.edges.any { it.from == utilId && it.to == "json" })
    }

    @Test
    fun `import cycle terminates`(@TempDir root: Path) {
        val a = root.resolve("pkg/a.py")
        Files.createDirectories(a.parent)
        val b = root.resolve("pkg/b.py")
        Files.writeString(a, "from .b import x")
        Files.writeString(b, "from .a import y")
        val slice = ImportGraphProvider(root).nodesForFile(a, "from .b import x")
        assertEquals(2, slice.nodes.size)
        assertEquals(2, slice.edges.size)
    }

    @Test
    fun `project slice connects all workspace files without externals`(@TempDir root: Path) {
        val util = root.resolve("pkg/util.py")
        Files.createDirectories(util.parent)
        Files.writeString(util, "import json")
        val service = root.resolve("pkg/service.py")
        Files.writeString(service, "from .util import helper")
        val main = root.resolve("pkg/main.py")
        Files.writeString(main, "from .service import run")
        val orphan = root.resolve("pkg/orphan.py")
        Files.writeString(orphan, "x = 1")
        val slice = ImportGraphProvider(root).nodesForProject(main, null)
        assertEquals(4, slice.nodes.size)
        assertEquals(2, slice.edges.size)
        assertEquals(NodeKind.ACTIVE, slice.nodes.single { it.label == "main.py" }.kind)
        assertTrue(slice.nodes.none { it.kind == NodeKind.EXTERNAL })
    }

    @Test
    fun `project slice reports per-file progress`(@TempDir root: Path) {
        val pkg = Files.createDirectories(root.resolve("pkg"))
        for (i in 1..3) Files.writeString(pkg.resolve("m$i.py"), "import json")
        val updates = ArrayList<Pair<Int, Int>>()
        ImportGraphProvider(root).nodesForProject(null, null) { done, total -> updates += done to total }
        assertEquals(3, updates.size)
        assertEquals(listOf(0, 1, 2), updates.map { it.first })
        assertTrue(updates.all { it.second == 3 })
    }

    @Test
    fun `project slice promotes inheritance edges`(@TempDir root: Path) {
        val base = root.resolve("src/base.ts")
        Files.createDirectories(base.parent)
        Files.writeString(base, "export class BaseComponent {}")
        val widget = root.resolve("src/widget.ts")
        Files.writeString(
            widget,
            "import { BaseComponent } from './base';\n\nexport class Widget extends BaseComponent {}",
        )
        val slice = ImportGraphProvider(root).nodesForProject(null, null)
        assertEquals(2, slice.nodes.size)
        assertEquals(EdgeKind.EXTENDS, slice.edges.single().kind)
    }

    @Test
    fun `imported supertype promotes edge to extends`(@TempDir root: Path) {
        val figure = root.resolve("src/sample/base/Figure.kt")
        Files.createDirectories(figure.parent)
        Files.writeString(figure, "package sample.base\n\nopen class Figure")
        val active = root.resolve("src/sample/Circle.kt")
        val text = """
            package sample

            import sample.base.Figure

            class Circle : Figure()
        """.trimIndent()
        Files.writeString(active, text)
        val slice = ImportGraphProvider(root).nodesForFile(active, text)
        assertEquals(1, slice.edges.size)
        assertEquals(EdgeKind.EXTENDS, slice.edges.single().kind)
    }

    @Test
    fun `same directory supertype without import adds implements edge`(@TempDir root: Path) {
        val shape = root.resolve("src/sample/Shape.kt")
        Files.createDirectories(shape.parent)
        Files.writeString(shape, "package sample\n\ninterface Shape")
        val active = root.resolve("src/sample/Circle.kt")
        val text = "package sample\n\nclass Circle : Shape"
        Files.writeString(active, text)
        val slice = ImportGraphProvider(root).nodesForFile(active, text)
        assertEquals(2, slice.nodes.size)
        assertEquals(1, slice.edges.size)
        assertEquals(EdgeKind.IMPLEMENTS, slice.edges.single().kind)
        val shapeNode = slice.nodes.single { it.kind == NodeKind.WORKSPACE_FILE }
        assertEquals("Shape.kt", shapeNode.label)
    }

    @Test
    fun `external supertype promotes external edge`(@TempDir root: Path) {
        val active = root.resolve("src/App.java")
        Files.createDirectories(active.parent)
        val text = """
            import javax.swing.JFrame;

            public class App extends JFrame {
            }
        """.trimIndent()
        Files.writeString(active, text)
        val slice = ImportGraphProvider(root).nodesForFile(active, text)
        assertEquals(EdgeKind.EXTENDS, slice.edges.single().kind)
        assertEquals(NodeKind.EXTERNAL, slice.nodes.single { it.id == "javax.swing.JFrame" }.kind)
    }

    @Test
    fun `imported symbol promotes edge when file name differs from type name`(@TempDir root: Path) {
        val base = root.resolve("src/components/base.ts")
        Files.createDirectories(base.parent)
        Files.writeString(base, "export class BaseComponent {}")
        val active = root.resolve("src/components/widget.ts")
        val text = """
            import { BaseComponent } from './base';

            export class Widget extends BaseComponent {
            }
        """.trimIndent()
        Files.writeString(active, text)
        val slice = ImportGraphProvider(root).nodesForFile(active, text)
        assertEquals(1, slice.edges.size)
        assertEquals(EdgeKind.EXTENDS, slice.edges.single().kind)
    }

    @Test
    fun `python from import symbol promotes edge despite module name mismatch`(@TempDir root: Path) {
        val shapes = root.resolve("pkg/shapes.py")
        Files.createDirectories(shapes.parent)
        Files.writeString(shapes, "class Shape:\n    pass")
        val active = root.resolve("pkg/circle.py")
        val text = "from .shapes import Shape\n\n\nclass Circle(Shape):\n    pass"
        Files.writeString(active, text)
        val slice = ImportGraphProvider(root).nodesForFile(active, text)
        assertEquals(1, slice.edges.size)
        assertEquals(EdgeKind.EXTENDS, slice.edges.single().kind)
    }

    @Test
    fun `extends outranks implements and import on the same edge`(@TempDir root: Path) {
        val base = root.resolve("src/sample/Base.kt")
        Files.createDirectories(base.parent)
        Files.writeString(base, "package sample\n\nopen class Base")
        val active = root.resolve("src/sample/Impl.kt")
        val text = """
            package sample

            import sample.Base

            class Impl : Base()
        """.trimIndent()
        Files.writeString(active, text)
        val slice = ImportGraphProvider(root).nodesForFile(active, text)
        assertEquals(1, slice.edges.size)
        assertEquals(EdgeKind.EXTENDS, slice.edges.single().kind)
    }
}
