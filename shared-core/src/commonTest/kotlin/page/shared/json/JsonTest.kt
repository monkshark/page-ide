package page.shared.json

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class JsonTest {

    @Test
    fun parsesSlimGraphShape() {
        val json = """
            {
              "nodes": [
                {"id": "a/b.kt", "label": "b.kt", "kind": "ACTIVE"},
                {"id": "c.kt", "label": "c.kt", "kind": "WORKSPACE_FILE"}
              ],
              "edges": [
                {"from": "a/b.kt", "to": "c.kt", "kind": "IMPORT"}
              ]
            }
        """.trimIndent()
        val root = Json.parse(json).asObject()!!
        val nodes = root["nodes"].asArray()
        assertEquals(2, nodes.size)
        assertEquals("b.kt", nodes[0].asObject()!!["label"].asString())
        assertEquals("ACTIVE", nodes[0].asObject()!!["kind"].asString())
        val edges = root["edges"].asArray()
        assertEquals("a/b.kt", edges[0].asObject()!!["from"].asString())
    }

    @Test
    fun handlesEscapesAndUnicode() {
        val root = Json.parse("""{"s": "a\"b\\c\nA"}""").asObject()!!
        assertEquals("a\"b\\c\nA", root["s"].asString())
    }

    @Test
    fun parsesNumbersBoolsNull() {
        val arr = Json.parse("[1, -2.5, 3e2, true, false, null]").asArray()
        assertEquals(6, arr.size)
        assertEquals(1.0, (arr[0] as JsonNumber).value)
        assertEquals(-2.5, (arr[1] as JsonNumber).value)
        assertEquals(300.0, (arr[2] as JsonNumber).value)
        assertTrue((arr[3] as JsonBool).value)
        assertEquals(JsonNull, arr[5])
    }

    @Test
    fun emptyContainers() {
        assertTrue(Json.parse("[]").asArray().isEmpty())
        assertTrue(Json.parse("{}").asObject()!!.entries.isEmpty())
    }

    @Test
    fun rejectsTrailingContent() {
        assertFailsWith<IllegalArgumentException> { Json.parse("{} junk") }
    }

    @Test
    fun rejectsUnterminatedString() {
        assertFailsWith<IllegalArgumentException> { Json.parse("\"abc") }
    }
}
