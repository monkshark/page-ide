package page.atlas

import page.shared.path.FilePath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import page.atlas.graph.GraphNode
import page.atlas.graph.NodeKind
import page.atlas.render.atlasSearchMatches

class AtlasSearchTest {

    private fun node(label: String, external: Boolean = false) = GraphNode(
        id = label,
        label = label,
        path = if (external) null else FilePath.of("ws/$label"),
        kind = if (external) NodeKind.EXTERNAL else NodeKind.WORKSPACE_FILE,
    )

    @Test
    fun ranksPrefixOverSubstringOverSubsequence() {
        val nodes = listOf(node("AppMain.kt"), node("Main.kt"), node("ModelTrain.kt"))
        val labels = atlasSearchMatches(nodes, "main").map { it.label }
        assertEquals(listOf("Main.kt", "AppMain.kt", "ModelTrain.kt"), labels)
    }

    @Test
    fun matchesAreCaseInsensitive() {
        val nodes = listOf(node("UTIL.KT"))
        assertEquals(1, atlasSearchMatches(nodes, "util").size)
    }

    @Test
    fun nonMatchesAreExcluded() {
        val nodes = listOf(node("Service.kt"))
        assertTrue(atlasSearchMatches(nodes, "xyz").isEmpty())
    }

    @Test
    fun externalNodesAreExcluded() {
        val nodes = listOf(node("kotlinx.coroutines", external = true), node("Coroutines.kt"))
        val labels = atlasSearchMatches(nodes, "co").map { it.label }
        assertEquals(listOf("Coroutines.kt"), labels)
    }

    @Test
    fun blankQueryYieldsNoMatches() {
        val nodes = listOf(node("Main.kt"))
        assertTrue(atlasSearchMatches(nodes, "  ").isEmpty())
    }
}
