package page.atlas

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import org.junit.jupiter.api.io.TempDir
import page.atlas.analyzer.DeclarationIndex
import page.atlas.analyzer.ImportExtractor
import page.atlas.analyzer.ImportGraphProvider
import page.atlas.analyzer.ProjectAnalysisProgress
import page.atlas.analyzer.ProjectAnalysisStage
import page.atlas.analyzer.WorkspaceIndex

class ProjectAnalysisProgressTest {
    @Test
    fun `declaration-only analysis preserves symbols and source locations`() {
        val samples = mapOf(
            "Demo.kt" to "package demo\n\nclass 한글 { fun run() { println(\"hello\") } }\nfun entry() = println(\"world\")",
            "Demo.java" to "package demo;\npublic class Demo { void run() { System.out.println(1); } }",
            "Demo.scala" to "package demo\nclass Demo { def run() = println(1) }",
            "Demo.cs" to "namespace Demo;\npublic class Example { void Run() { System.Console.WriteLine(1); } }",
            "Demo.php" to "<?php\nclass Demo { function run() { echo 1; } }",
            "Demo.swift" to "class Demo { func run() { print(1) } }",
        )
        for ((name, source) in samples) {
            val path = Path.of(name)
            val full = ImportExtractor.analyze(path, source)
            assertEquals(full.declarations, ImportExtractor.analyzeDeclarations(path, source), name)
            assertTrue(ImportExtractor.supportsDeclarations(path), name)
        }
        assertFalse(ImportExtractor.supportsDeclarations(Path.of("main.py")))
        assertFalse(ImportExtractor.supportsDeclarations(Path.of("component.vue")))
    }

    @Test
    fun `declaration index excludes files with no supported declarations and reports every candidate`(@TempDir root: Path) {
        Files.writeString(root.resolve("A.kt"), "package demo\nclass A")
        Files.writeString(root.resolve("B.java"), "package demo; class B {}")
        Files.writeString(root.resolve("Empty.kt"), "")
        Files.writeString(root.resolve("main.py"), "print(1)")
        Files.writeString(root.resolve("main.ts"), "export const value = 1")
        val analyzed = mutableListOf<String>()
        val updates = mutableListOf<Pair<Int, Int>>()
        val index = DeclarationIndex(WorkspaceIndex(root)) {
            analyzed += it.fileName.toString()
            ImportExtractor.analyze(it, Files.readString(it))
        }
        index.refreshIfStale { done, total -> updates += done to total }
        assertEquals(listOf("A.kt", "B.java", "Empty.kt"), analyzed)
        assertEquals(listOf(0 to 3, 1 to 3, 2 to 3, 3 to 3), updates)
        index.refreshIfStale { done, total -> updates += done to total }
        assertEquals(3, analyzed.size)
        assertEquals(4, updates.size)
    }

    @Test
    fun `project reports discovery then declarations before linking files`(@TempDir root: Path) {
        val source = Files.writeString(root.resolve("A.kt"), "package demo\nimport demo.B\nclass A : B()")
        Files.writeString(root.resolve("B.kt"), "package demo\nopen class B")
        val updates = mutableListOf<ProjectAnalysisProgress>()
        val provider = ImportGraphProvider(root)
        val result = provider.analyzeProject(source, null, updates::add)
        assertEquals(ProjectAnalysisStage.DISCOVERING, updates.first().stage)
        val declarations = updates.filter { it.stage == ProjectAnalysisStage.DECLARATIONS }
        assertEquals(listOf(0, 1, 2), declarations.map { it.completed })
        assertTrue(declarations.all { it.total == 2 })
        assertTrue(updates.indexOf(declarations.last()) < updates.indexOfFirst { it.stage == ProjectAnalysisStage.RELATIONSHIPS })
        assertEquals(ProjectAnalysisProgress(ProjectAnalysisStage.RELATIONSHIPS, 2, 2), updates.last())
        assertEquals(1, result.edges.size)
        updates.clear()
        assertEquals(result, provider.analyzeProject(source, null, updates::add))
        assertTrue(updates.none { it.stage == ProjectAnalysisStage.DECLARATIONS })
    }

    @Test
    fun `relative-import project does not build the unrelated declaration index`(@TempDir root: Path) {
        Files.writeString(root.resolve("a.py"), "from . import b")
        Files.writeString(root.resolve("b.py"), "print(1)")
        val updates = mutableListOf<ProjectAnalysisProgress>()
        ImportGraphProvider(root).analyzeProject(null, null, updates::add)
        assertTrue(updates.none { it.stage == ProjectAnalysisStage.DECLARATIONS })
        assertEquals(2, updates.last().completed)
    }

    @Test
    fun `workspace snapshot prevents rescanning during one analysis and releases afterwards`(@TempDir root: Path) {
        Files.writeString(root.resolve("A.kt"), "class A")
        val workspace = WorkspaceIndex(root)
        workspace.withSnapshot {
            val revision = workspace.revision()
            Files.writeString(root.resolve("B.kt"), "class B")
            workspace.refreshIfStale(0)
            assertEquals(revision, workspace.revision())
            assertEquals(1, workspace.files().size)
        }
        workspace.refreshIfStale(0)
        assertEquals(2, workspace.files().size)
    }

    @Test
    fun `cancelled declaration indexing can be restarted without publishing a partial index`(@TempDir root: Path) {
        Files.writeString(root.resolve("A.kt"), "package demo\nimport demo.B\nclass A : B()")
        Files.writeString(root.resolve("B.kt"), "package demo\nopen class B")
        val provider = ImportGraphProvider(root)
        assertFailsWith<CancellationException> {
            provider.analyzeProject(null, null) {
                if (it.stage == ProjectAnalysisStage.DECLARATIONS && it.completed == 1) throw CancellationException("cancelled")
            }
        }
        assertEquals(1, provider.analyzeProject(null, null) {}.edges.size)
    }
}
