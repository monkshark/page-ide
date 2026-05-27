package page.app

import page.runtime.*

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RunConfigDialogParseTest {

    @Test
    fun `parseArgs splits on whitespace`() {
        assertEquals(listOf("run", "main.py"), parseArgs("run main.py"))
        assertEquals(listOf("a", "b", "c"), parseArgs("  a   b\tc "))
    }

    @Test
    fun `parseArgs preserves double-quoted segments with spaces`() {
        assertEquals(listOf("hello world", "next"), parseArgs("\"hello world\" next"))
    }

    @Test
    fun `parseArgs preserves single-quoted segments with spaces`() {
        assertEquals(listOf("a b", "c"), parseArgs("'a b' c"))
    }

    @Test
    fun `parseArgs honours backslash escape`() {
        assertEquals(listOf("a b"), parseArgs("a\\ b"))
        assertEquals(listOf("\"quoted\""), parseArgs("\\\"quoted\\\""))
    }

    @Test
    fun `parseArgs returns empty list for blank input`() {
        assertTrue(parseArgs("").isEmpty())
        assertTrue(parseArgs("   ").isEmpty())
    }

    @Test
    fun `parseEnv splits KEY=VALUE lines and preserves insertion order`() {
        val env = parseEnv(
            """
            PORT=8080
            NODE_ENV=development
            # comment line
            EMPTY=
            """.trimIndent(),
        )
        assertEquals(listOf("PORT", "NODE_ENV", "EMPTY"), env.keys.toList())
        assertEquals("8080", env["PORT"])
        assertEquals("development", env["NODE_ENV"])
        assertEquals("", env["EMPTY"])
    }

    @Test
    fun `parseEnv ignores invalid lines`() {
        val env = parseEnv("no-equals\n=missing-key\nGOOD=ok")
        assertEquals(mapOf("GOOD" to "ok"), env)
    }

    @Test
    fun `envToText emits one KEY=VALUE per line`() {
        val text = envToText(linkedMapOf("A" to "1", "B" to "two"))
        assertEquals("A=1\nB=two", text)
    }
}
