package page.language

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import page.lsp.LspProgress
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LspControllerActivityTest {
    private val supervisor = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Unconfined + supervisor)

    @AfterTest
    fun tearDown() {
        supervisor.cancel()
    }

    private fun controller() = LspController(workspaceRoot = null, scope = scope)

    @Test
    fun progressBeginCreatesActivityWithTitleAndFraction() {
        val controller = controller()
        controller.applyProgressEvent(LspProgress.Begin("ANALYZING", "Analyzing…", null, 30))

        val activity = controller.activities["progress:ANALYZING"]
        assertEquals("Analyzing…", activity?.label)
        assertEquals(0.3f, activity?.progress)
    }

    @Test
    fun progressBeginSupersedesSyntheticAnalysisActivity() {
        val controller = controller()
        controller.startActivity(LspController.ANALYSIS_KIND, "Analyzing project…")
        assertTrue(controller.activities.containsKey(LspController.ANALYSIS_KIND))

        controller.applyProgressEvent(LspProgress.Begin("ANALYZING", "Analyzing…", null, null))

        assertFalse(controller.activities.containsKey(LspController.ANALYSIS_KIND))
        assertTrue(controller.activities.containsKey("progress:ANALYZING"))
    }

    @Test
    fun progressReportUpdatesLabelAndFraction() {
        val controller = controller()
        controller.applyProgressEvent(LspProgress.Begin("T", "Indexing", null, 0))
        controller.applyProgressEvent(LspProgress.Report("T", "half done", 50))

        val activity = controller.activities["progress:T"]
        assertEquals("Indexing — half done", activity?.label)
        assertEquals(0.5f, activity?.progress)
    }

    @Test
    fun progressEndRemovesActivity() {
        val controller = controller()
        controller.applyProgressEvent(LspProgress.Begin("T", "Indexing", null, null))
        controller.applyProgressEvent(LspProgress.End("T", null))

        assertNull(controller.activities["progress:T"])
    }

    @Test
    fun longRunningAnalysisOutlivesDefaultActivityTimeout() {
        val controller = controller()
        assertEquals(600_000L, controller.activityTimeoutMs(LspController.ANALYSIS_KIND))
        assertEquals(600_000L, controller.activityTimeoutMs("progress:ANALYZING"))
        assertEquals(120_000L, controller.activityTimeoutMs(LspController.STARTUP_KIND))
        assertEquals(60_000L, controller.activityTimeoutMs("unknown"))
    }
}
