package page.atlas.render

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import page.atlas.graph.GraphSlice
import page.ui.GlassTheme
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ProjectMapViewTest {

    private fun project(): Path {
        val root = Files.createTempDirectory("mapview-")
        fun w(rel: String, text: String = "x") {
            val p = root.resolve(rel)
            Files.createDirectories(p.parent)
            Files.writeString(p, text)
        }
        w("settings.gradle.kts", "include(\":app\")\ninclude(\":core\")\n")
        w("app/build.gradle.kts", "dependencies { implementation(project(\":core\")) }")
        w("app/src/main/kotlin/web/Routes.kt")
        w("app/src/main/kotlin/web/Views.kt")
        w("app/src/main/kotlin/store/Db.kt")
        w("core/build.gradle.kts", "")
        w("core/src/main/kotlin/Model.kt")
        return root
    }

    @Test
    fun `the map appears without waiting for import analysis`() = runComposeUiTest {
        val root = project()
        setContent {
            GlassTheme { ProjectMapView(root, GraphSlice.EMPTY, null, {}, tourStore = TourStore.memory(initiallyDone = true)) }
        }
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithTag("part-app").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("part-app").assertIsDisplayed()
        onNodeWithTag("part-core").assertIsDisplayed()
    }

    @Test
    fun `clicking a part looks inside it`() = runComposeUiTest {
        val root = project()
        setContent {
            GlassTheme { ProjectMapView(root, GraphSlice.EMPTY, null, {}, tourStore = TourStore.memory(initiallyDone = true)) }
        }
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithTag("part-app").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("part-app").performClick()
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithText("web").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("store").assertIsDisplayed()
    }

    @Test
    fun `the part holding the open file says so and its neighbours are named`() = runComposeUiTest {
        val root = project()
        val open = root.resolve("core/src/main/kotlin/Model.kt")
        setContent {
            GlassTheme { ProjectMapView(root, GraphSlice.EMPTY, open, {}, tourStore = TourStore.memory(initiallyDone = true)) }
        }
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithText("Model.kt is here").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("breaks if it breaks").assertIsDisplayed()
    }

    @Test
    fun `the tour shows once and then stays away`() = runComposeUiTest {
        val root = project()
        val store = TourStore.memory()
        setContent { GlassTheme { ProjectMapView(root, GraphSlice.EMPTY, null, {}, tourStore = store) } }
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithTag("map-tour").fetchSemanticsNodes().isNotEmpty() }
        repeat(3) { onNodeWithTag("map-tour-next").performClick() }
        waitForIdle()
        assertTrue(onAllNodesWithTag("map-tour").fetchSemanticsNodes().isEmpty())
        assertTrue(store.done())
    }

    @Test
    fun `the question mark brings the tour back`() = runComposeUiTest {
        val root = project()
        setContent { GlassTheme { ProjectMapView(root, GraphSlice.EMPTY, null, {}, tourStore = TourStore.memory(initiallyDone = true)) } }
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithTag("part-app").fetchSemanticsNodes().isNotEmpty() }
        assertTrue(onAllNodesWithTag("map-tour").fetchSemanticsNodes().isEmpty())
        onNodeWithTag("map-help").performClick()
        onNodeWithTag("map-tour").assertIsDisplayed()
    }
}
