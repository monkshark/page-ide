package page.shared.docs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DocRoutingTest {
    @Test
    fun parsesEmptyHashAsLanding() {
        assertEquals(DocRef(null, null), parseDocHash(""))
        assertEquals(DocRef(null, null), parseDocHash("#"))
    }

    @Test
    fun parsesDocPathOnly() {
        assertEquals(DocRef("guides/overview.md", null), parseDocHash("#guides/overview.md"))
        assertEquals(DocRef("guides/overview.md", null), parseDocHash("guides/overview.md"))
    }

    @Test
    fun parsesDocPathWithHeading() {
        val ref = parseDocHash("#guides/overview.md#핵심-가치")
        assertEquals("guides/overview.md", ref.path)
        assertEquals("핵심-가치", ref.heading)
    }

    @Test
    fun buildsHashRoundTrip() {
        assertEquals("guides/overview.md", buildDocHash("guides/overview.md", null))
        assertEquals("guides/overview.md#s", buildDocHash("guides/overview.md", "s"))
        assertEquals("", buildDocHash(null, null))
    }

    @Test
    fun enKoVariantsSwap() {
        assertEquals("guides/overview_en.md", enVariant("guides/overview.md"))
        assertEquals("guides/overview.md", koVariant("guides/overview_en.md"))
        assertTrue(isEnVariant("guides/overview_en.md"))
        assertEquals("guides/overview_en.md", enVariant("guides/overview_en.md"))
    }

    @Test
    fun variantForFallsBackWhenMissing() {
        val available = setOf("guides/overview.md", "guides/overview_en.md", "modules/app/main.md")
        assertEquals("guides/overview_en.md", variantFor("guides/overview.md", english = true, available))
        assertEquals("modules/app/main.md", variantFor("modules/app/main.md", english = true, available))
        assertEquals("guides/overview.md", variantFor("guides/overview_en.md", english = false, available))
    }

    @Test
    fun buildsTreeGroupedByFolderExcludingEn() {
        val paths = listOf(
            "guides/overview.md",
            "guides/overview_en.md",
            "guides/architecture.md",
            "modules/app/main.md",
            "modules/app/tab_bar.md",
            "sample.md",
        )
        val tree = buildDocTree(paths)
        assertEquals(listOf("guides", "modules", "sample"), tree.map { it.name })
        val guides = tree.first { it.name == "guides" }
        assertNull(guides.path)
        assertEquals(listOf("architecture", "overview"), guides.children.map { it.name })
        assertTrue(guides.children.none { isEnVariant(it.path ?: "") })
        val modules = tree.first { it.name == "modules" }
        val app = modules.children.single()
        assertEquals("app", app.name)
        assertEquals(listOf("main", "tab bar"), app.children.map { it.name })
    }

    @Test
    fun parsesDocIndexJson() {
        val json = """["guides/overview.md","modules/app/main.md"]"""
        assertEquals(listOf("guides/overview.md", "modules/app/main.md"), parseDocIndex(json))
    }
}
