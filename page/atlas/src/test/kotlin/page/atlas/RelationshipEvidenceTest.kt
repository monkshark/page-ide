package page.atlas

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import page.atlas.analyzer.ImportExtractor
import page.atlas.analyzer.ImportGraphProvider
import page.atlas.graph.EdgeKind

class RelationshipEvidenceTest {
    @Test
    fun `multiline import preserves the exact statement and zero based source line`() {
        val text = "const title = '한글 🧭';\n\nimport {\n  session,\n  clear\n} from './store';\n"
        val analysis = ImportExtractor.analyze(Path.of("actions.ts"), text)
        val evidence = assertNotNull(analysis.importEvidence[analysis.imports.single()])
        assertEquals(2, evidence.line)
        assertEquals("import {\n  session,\n  clear\n} from './store';", evidence.text)
    }

    @Test
    fun `identical repeated imports point to the first syntax node`() {
        val text = "import json\n\nimport json\n"
        val analysis = ImportExtractor.analyze(Path.of("a.py"), text)
        assertEquals(0, analysis.importEvidence.values.single().line)
    }

    @Test
    fun `component script evidence keeps original component line numbers`() {
        val text = "<template>한글</template>\n<script setup lang=\"ts\">\nimport { session } from './store';\n</script>"
        val analysis = ImportExtractor.analyze(Path.of("View.vue"), text)
        val evidence = assertNotNull(analysis.importEvidence[analysis.imports.single()])
        assertEquals(2, evidence.line)
        assertTrue(evidence.text.contains("from './store'"))
    }

    @Test
    fun `resolved project and file edges retain the source import`(@TempDir root: Path) {
        val store = root.resolve("store.ts")
        val source = root.resolve("actions.ts")
        Files.writeString(store, "export const session = {};\n")
        val text = "const title = '한글';\nimport { session } from './store';\n"
        Files.writeString(source, text)
        val provider = ImportGraphProvider(root)
        for (slice in listOf(provider.nodesForFile(source, text), provider.nodesForProject(source, text))) {
            val edge = slice.edges.single { it.from == source.toString() && it.to == store.toString() }
            assertEquals(1, edge.evidence?.line)
            assertEquals("import { session } from './store';", edge.evidence?.text)
        }
    }

    @Test
    fun `inheritance evidence is the type clause rather than an import`(@TempDir root: Path) {
        Files.writeString(root.resolve("Base.java"), "class Base {}")
        val source = root.resolve("Child.java")
        val text = "\n\nclass Child extends Base {}"
        Files.writeString(source, text)
        val edge = ImportGraphProvider(root).nodesForFile(source, text).edges.single()
        assertEquals(EdgeKind.EXTENDS, edge.kind)
        assertEquals(2, edge.evidence?.line)
        assertEquals("extends Base", edge.evidence?.text)
    }

    @Test
    fun `unsaved active text provides its own evidence`(@TempDir root: Path) {
        Files.writeString(root.resolve("store.ts"), "export const store = {}")
        val source = root.resolve("actions.ts")
        Files.writeString(source, "export const empty = {}")
        val text = "\n\nimport { store } from './store';"
        val slice = ImportGraphProvider(root).nodesForProject(source, text)
        assertEquals(2, slice.edges.single().evidence?.line)
    }
}
