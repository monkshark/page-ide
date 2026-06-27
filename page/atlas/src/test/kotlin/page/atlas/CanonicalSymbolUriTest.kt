package page.atlas

import page.atlas.graph.SymbolSpec
import page.atlas.graph.canonicalSymbolUri
import page.atlas.graph.symbolNodeId
import kotlin.test.Test
import kotlin.test.assertEquals

class CanonicalSymbolUriTest {

    private fun spec(uri: String) = SymbolSpec(name = "fn", detail = null, uri = uri, line = 7)

    @Test
    fun `windows drive casing collapses to one node id`() {
        val upper = symbolNodeId(spec("file:///C:/ws/A.kt"))
        val lower = symbolNodeId(spec("file:///c:/ws/A.kt"))
        assertEquals(upper, lower)
    }

    @Test
    fun `dot segments are normalized away`() {
        assertEquals(
            canonicalSymbolUri("file:///C:/ws/A.kt"),
            canonicalSymbolUri("file:///C:/ws/sub/../A.kt"),
        )
    }

    @Test
    fun `canonical file uri uses a lowercase drive letter`() {
        assertEquals("file:///c:/ws/a.kt", canonicalSymbolUri("file:///C:/ws/a.kt"))
    }

    @Test
    fun `malformed uri falls back to the original string`() {
        assertEquals("not a uri", canonicalSymbolUri("not a uri"))
    }
}
