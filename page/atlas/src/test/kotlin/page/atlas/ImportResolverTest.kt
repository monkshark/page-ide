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
    fun `rust super import resolves against parent module not crate root`(@TempDir root: Path) {
        val target = root.resolve("src/a/foo.rs")
        Files.createDirectories(target.parent)
        Files.writeString(target, "pub struct Bar;")
        Files.writeString(root.resolve("src/foo.rs"), "pub struct Bar;")
        val active = root.resolve("src/a/b.rs")
        Files.writeString(active, "")
        val resolved = ImportResolver.resolve(RawImport("super::foo::Bar", true), active, WorkspaceIndex(root))
        assertEquals(target, resolved)
    }

    @Test
    fun `rust self import resolves to submodule in current module dir`(@TempDir root: Path) {
        val target = root.resolve("src/a/b/helper.rs")
        Files.createDirectories(target.parent)
        Files.writeString(target, "pub fn h() {}")
        val active = root.resolve("src/a/b.rs")
        Files.writeString(active, "")
        val resolved = ImportResolver.resolve(RawImport("self::helper", true), active, WorkspaceIndex(root))
        assertEquals(target, resolved)
    }

    @Test
    fun `rust nested super walks up two modules`(@TempDir root: Path) {
        val target = root.resolve("src/a/x.rs")
        Files.createDirectories(target.parent)
        Files.writeString(target, "pub struct X;")
        val active = root.resolve("src/a/b/c.rs")
        Files.createDirectories(active.parent)
        Files.writeString(active, "")
        val resolved = ImportResolver.resolve(RawImport("super::super::x::X", true), active, WorkspaceIndex(root))
        assertEquals(target, resolved)
    }

    @Test
    fun `rust self import from mod file resolves sibling submodule`(@TempDir root: Path) {
        val target = root.resolve("src/a/widget.rs")
        Files.createDirectories(target.parent)
        Files.writeString(target, "pub struct W;")
        val active = root.resolve("src/a/mod.rs")
        Files.writeString(active, "")
        val resolved = ImportResolver.resolve(RawImport("self::widget", true), active, WorkspaceIndex(root))
        assertEquals(target, resolved)
    }

    @Test
    fun `dart package import resolves into lib directory`(@TempDir root: Path) {
        Files.writeString(root.resolve("pubspec.yaml"), "name: my_app\n")
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
    fun `c quoted include resolves relative to file`(@TempDir root: Path) {
        val target = root.resolve("src/util/helper.h")
        Files.createDirectories(target.parent)
        Files.writeString(target, "")
        val active = root.resolve("src/main.c")
        Files.writeString(active, "")
        val resolved = ImportResolver.resolve(RawImport("util/helper.h", true), active, WorkspaceIndex(root))
        assertEquals(target, resolved)
    }

    @Test
    fun `cpp angle include resolves to project header by path`(@TempDir root: Path) {
        val target = root.resolve("include/app/widget.h")
        Files.createDirectories(target.parent)
        Files.writeString(target, "")
        val active = root.resolve("src/button.cpp")
        Files.createDirectories(active.parent)
        Files.writeString(active, "")
        val resolved = ImportResolver.resolve(RawImport("app/widget.h", false), active, WorkspaceIndex(root))
        assertEquals(target, resolved)
    }

    @Test
    fun `c system include stays external`(@TempDir root: Path) {
        val active = root.resolve("main.c")
        Files.writeString(active, "")
        assertNull(ImportResolver.resolve(RawImport("stdio.h", false), active, WorkspaceIndex(root)))
    }

    @Test
    fun `scala import resolves via declaration index for multi-symbol file`(@TempDir root: Path) {
        val model = root.resolve("src/com/example/util/Helpers.scala")
        Files.createDirectories(model.parent)
        Files.writeString(model, "package com.example.util\n\nclass Helper\nclass Logger")
        val active = root.resolve("src/com/example/app/Service.scala")
        Files.createDirectories(active.parent)
        Files.writeString(active, "package com.example.app")
        val index = WorkspaceIndex(root)
        val decls = declIndex(root)
        assertEquals(
            model,
            ImportResolver.resolve(RawImport("com.example.util.Helper", false), active, index, decls),
        )
        assertEquals(
            model,
            ImportResolver.resolve(RawImport("com.example.util.Logger", false), active, index, decls),
        )
    }

    @Test
    fun `scala external import stays external`(@TempDir root: Path) {
        val active = root.resolve("Main.scala")
        Files.writeString(active, "package app")
        assertNull(
            ImportResolver.resolve(RawImport("scala.collection.mutable.ListBuffer", false), active, WorkspaceIndex(root)),
        )
    }

    @Test
    fun `ruby require_relative resolves against file directory`(@TempDir root: Path) {
        val target = root.resolve("lib/helper.rb")
        Files.createDirectories(target.parent)
        Files.writeString(target, "")
        val active = root.resolve("app/service.rb")
        Files.createDirectories(active.parent)
        Files.writeString(active, "")
        val resolved = ImportResolver.resolve(RawImport("../lib/helper", true), active, WorkspaceIndex(root))
        assertEquals(target, resolved)
    }

    @Test
    fun `ruby require resolves project file by load path suffix`(@TempDir root: Path) {
        val target = root.resolve("lib/models/user.rb")
        Files.createDirectories(target.parent)
        Files.writeString(target, "")
        val active = root.resolve("app/service.rb")
        Files.createDirectories(active.parent)
        Files.writeString(active, "")
        val resolved = ImportResolver.resolve(RawImport("models/user", false), active, WorkspaceIndex(root))
        assertEquals(target, resolved)
    }

    @Test
    fun `ruby gem require stays external`(@TempDir root: Path) {
        val active = root.resolve("service.rb")
        Files.writeString(active, "")
        assertNull(ImportResolver.resolve(RawImport("json", false), active, WorkspaceIndex(root)))
    }

    @Test
    fun `php use resolves via declaration index across namespace`(@TempDir root: Path) {
        val model = root.resolve("src/Models/User.php")
        Files.createDirectories(model.parent)
        Files.writeString(model, "<?php\nnamespace App\\Models;\n\nclass User {}\nclass Account {}")
        val active = root.resolve("src/Http/Controller.php")
        Files.createDirectories(active.parent)
        Files.writeString(active, "<?php\nnamespace App\\Http;")
        val index = WorkspaceIndex(root)
        val decls = declIndex(root)
        assertEquals(
            model,
            ImportResolver.resolve(RawImport("App.Models.User", false), active, index, decls),
        )
        assertEquals(
            model,
            ImportResolver.resolve(RawImport("App.Models.Account", false), active, index, decls),
        )
    }

    @Test
    fun `php require resolves relative to file directory`(@TempDir root: Path) {
        val target = root.resolve("app/config.php")
        Files.createDirectories(target.parent)
        Files.writeString(target, "<?php")
        val active = root.resolve("app/index.php")
        Files.writeString(active, "<?php")
        val resolved = ImportResolver.resolve(RawImport("config.php", true), active, WorkspaceIndex(root))
        assertEquals(target, resolved)
    }

    @Test
    fun `php vendor use stays external`(@TempDir root: Path) {
        val active = root.resolve("src/index.php")
        Files.createDirectories(active.parent)
        Files.writeString(active, "<?php")
        assertNull(
            ImportResolver.resolve(RawImport("Psr.Log.LoggerInterface", false), active, WorkspaceIndex(root), declIndex(root)),
        )
    }

    @Test
    fun `kotlin wildcard import resolves to every file in the package`(@TempDir root: Path) {
        val model = root.resolve("src/g/Model.kt")
        Files.createDirectories(model.parent)
        Files.writeString(model, "package g\n\nclass GraphSlice\nclass GraphNode")
        val util = root.resolve("src/g/Util.kt")
        Files.writeString(util, "package g\n\nobject Util")
        val other = root.resolve("src/h/Other.kt")
        Files.createDirectories(other.parent)
        Files.writeString(other, "package h\n\nclass Other")
        val active = root.resolve("src/app/Main.kt")
        Files.createDirectories(active.parent)
        Files.writeString(active, "package app")
        val resolved = ImportResolver.resolveAll(
            RawImport("g", false, wildcard = true), active, WorkspaceIndex(root), declIndex(root),
        )
        assertEquals(
            setOf(model.toAbsolutePath().normalize(), util.toAbsolutePath().normalize()),
            resolved.toSet(),
        )
    }

    @Test
    fun `wildcard import excludes the active file from its own package`(@TempDir root: Path) {
        val a = root.resolve("src/g/A.kt")
        Files.createDirectories(a.parent)
        Files.writeString(a, "package g\n\nclass A")
        val active = root.resolve("src/g/B.kt")
        Files.writeString(active, "package g\n\nclass B")
        val resolved = ImportResolver.resolveAll(
            RawImport("g", false, wildcard = true), active, WorkspaceIndex(root), declIndex(root),
        )
        assertEquals(listOf(a.toAbsolutePath().normalize()), resolved)
    }

    @Test
    fun `non-wildcard import resolves to a single file via resolveAll`(@TempDir root: Path) {
        val target = root.resolve("src/com/example/util/Helper.kt")
        Files.createDirectories(target.parent)
        Files.writeString(target, "package com.example.util")
        val active = root.resolve("src/com/example/Main.kt")
        Files.writeString(active.also { Files.createDirectories(it.parent) }, "package com.example")
        val resolved = ImportResolver.resolveAll(
            RawImport("com.example.util.Helper", false), active, WorkspaceIndex(root),
        )
        assertEquals(listOf(target), resolved)
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

    @Test
    fun `csharp namespace using resolves to every file in the namespace`(@TempDir root: Path) {
        val calc = root.resolve("src/App/Services/Calculator.cs")
        Files.createDirectories(calc.parent)
        Files.writeString(calc, "namespace App.Services;\n\npublic class Calculator { }")
        val order = root.resolve("src/App/Services/Order.cs")
        Files.writeString(order, "namespace App.Services;\n\npublic class Order { }")
        val other = root.resolve("src/App/Models/User.cs")
        Files.createDirectories(other.parent)
        Files.writeString(other, "namespace App.Models;\n\npublic class User { }")
        val active = root.resolve("src/App/Program.cs")
        Files.writeString(active, "namespace App;")
        val resolved = ImportResolver.resolveAll(
            RawImport("App.Services", false, wildcard = true), active, WorkspaceIndex(root), declIndex(root),
        )
        assertEquals(
            setOf(calc.toAbsolutePath().normalize(), order.toAbsolutePath().normalize()),
            resolved.toSet(),
        )
    }

    @Test
    fun `csharp static using resolves to the declaring type file`(@TempDir root: Path) {
        val model = root.resolve("src/App/Services/Calculator.cs")
        Files.createDirectories(model.parent)
        Files.writeString(model, "namespace App.Services;\n\npublic class Calculator { }\npublic class Rounder { }")
        val active = root.resolve("src/App/Program.cs")
        Files.writeString(active, "namespace App;")
        val resolved = ImportResolver.resolve(
            RawImport("App.Services.Calculator", false, listOf("Calculator")),
            active,
            WorkspaceIndex(root),
            declIndex(root),
        )
        assertEquals(model, resolved)
    }

    @Test
    fun `csharp framework using stays external`(@TempDir root: Path) {
        val active = root.resolve("Program.cs")
        Files.writeString(active, "namespace App;")
        assertNull(
            ImportResolver.resolve(RawImport("System", false, wildcard = true), active, WorkspaceIndex(root)),
        )
    }
}
