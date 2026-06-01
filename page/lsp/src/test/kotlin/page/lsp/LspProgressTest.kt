package page.lsp

import org.eclipse.lsp4j.ProgressParams
import org.eclipse.lsp4j.WorkDoneProgressBegin
import org.eclipse.lsp4j.WorkDoneProgressEnd
import org.eclipse.lsp4j.WorkDoneProgressNotification
import org.eclipse.lsp4j.WorkDoneProgressReport
import org.eclipse.lsp4j.jsonrpc.messages.Either
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LspProgressTest {

    private fun params(token: String, notif: WorkDoneProgressNotification): ProgressParams =
        ProgressParams(Either.forLeft(token), Either.forLeft<WorkDoneProgressNotification, Any>(notif))

    @Test
    fun `null params returns null`() {
        assertNull(parseLspProgress(null))
    }

    @Test
    fun `begin maps to Begin with title message and percentage`() {
        val begin = WorkDoneProgressBegin().apply {
            title = "Indexing"
            message = "1/10"
            percentage = 10
        }
        val r = parseLspProgress(params("rustAnalyzer/Indexing", begin))
        assertEquals(LspProgress.Begin("rustAnalyzer/Indexing", "Indexing", "1/10", 10), r)
    }

    @Test
    fun `report maps to Report and missing percentage is null`() {
        val report = WorkDoneProgressReport().apply { message = "5/10" }
        val r = parseLspProgress(params("rustAnalyzer/Indexing", report))
        assertEquals(LspProgress.Report("rustAnalyzer/Indexing", "5/10", null), r)
    }

    @Test
    fun `end maps to End`() {
        val end = WorkDoneProgressEnd().apply { message = "done" }
        val r = parseLspProgress(params("rustAnalyzer/Indexing", end))
        assertEquals(LspProgress.End("rustAnalyzer/Indexing", "done"), r)
    }

    @Test
    fun `numeric token is stringified`() {
        val begin = WorkDoneProgressBegin().apply { title = "Loading" }
        val p = ProgressParams(Either.forRight(42), Either.forLeft<WorkDoneProgressNotification, Any>(begin))
        val r = parseLspProgress(p)
        assertEquals("42", r?.token)
    }
}
