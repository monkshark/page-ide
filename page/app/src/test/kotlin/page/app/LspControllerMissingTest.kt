package page.app

import page.runtime.*

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LspControllerMissingTest {
    private val supervisor = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Unconfined + supervisor)

    @AfterTest
    fun tearDown() {
        supervisor.cancel()
    }

    @Test
    fun markMissingPopulatesDefinitionAttemptedAndStatus() {
        val controller = LspController(workspaceRoot = null, scope = scope)
        val attempts = listOf(
            "override=C:\\nope\\kls.bat",
            "bundled=C:\\app\\build\\compose\\tmp\\prepareAppResources\\lsp\\server\\bin",
            "dev=C:\\IDE\\page\\app\\build\\composeResources\\common\\lsp\\server\\bin",
            "PATH=C:\\Windows\\System32\\kotlin-language-server.bat",
        )
        val detail = "kotlin-language-server not found. Tried:\n  ${attempts.joinToString("\n  ")}"

        controller.markMissing(backendId = "kotlin", attempted = attempts, detail = detail)

        assertEquals(LspController.Status.MISSING, controller.status.value)
        assertEquals(detail, controller.statusDetail.value)
        assertEquals(attempts, controller.missingAttempted.value)
        val def = controller.missingDefinition.value
        assertNotNull(def, "kotlin definition must resolve via LanguageRegistry")
        assertEquals("kotlin", def.id)
        assertEquals("Kotlin", def.displayName)
        assertTrue(def.lspWindowsBinaries.any { it.endsWith(".bat") }, "kotlin def must include .bat for Windows: ${def.lspWindowsBinaries}")
    }

    @Test
    fun markMissingWithUnknownBackendStillSetsStatusButLeavesDefinitionNull() {
        val controller = LspController(workspaceRoot = null, scope = scope)

        controller.markMissing(backendId = "totally-fake-language", attempted = emptyList(), detail = "no LanguageBackend registered for .xyz")

        assertEquals(LspController.Status.MISSING, controller.status.value)
        assertEquals("no LanguageBackend registered for .xyz", controller.statusDetail.value)
        assertEquals(emptyList(), controller.missingAttempted.value)
        assertNull(controller.missingDefinition.value, "unknown backend id should produce null definition")
    }

    @Test
    fun markMissingOverwritesPriorMissingState() {
        val controller = LspController(workspaceRoot = null, scope = scope)
        controller.markMissing(backendId = "unknown", attempted = listOf("one"), detail = "first")

        controller.markMissing(
            backendId = "kotlin",
            attempted = listOf("PATH=/usr/bin/kotlin-language-server"),
            detail = "kotlin-language-server not found",
        )

        assertEquals(LspController.Status.MISSING, controller.status.value)
        assertEquals(listOf("PATH=/usr/bin/kotlin-language-server"), controller.missingAttempted.value)
        assertEquals("kotlin", controller.missingDefinition.value?.id)
    }
}
