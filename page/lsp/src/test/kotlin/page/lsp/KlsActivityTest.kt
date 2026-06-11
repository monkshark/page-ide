package page.lsp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class KlsActivityTest {

    @Test
    fun `null and blank input return null`() {
        assertNull(parseKlsActivity(null))
        assertNull(parseKlsActivity(""))
        assertNull(parseKlsActivity("   "))
    }

    @Test
    fun `unrecognized message returns null`() {
        assertNull(parseKlsActivity("Some random log line that should not match"))
    }

    @Test
    fun `kotlinLSPProjectDeps task starts gradle-deps activity`() {
        val r = parseKlsActivity("[Thread-1] Run: kotlinLSPProjectDeps")
        assertEquals(KlsActivity.Start(KLS_GRADLE_DEPS_KIND, "Gradle: resolving project dependencies…"), r)
    }

    @Test
    fun `kotlinLSPKotlinDSLDeps task starts gradle-script-deps activity`() {
        val r = parseKlsActivity("[Thread-1] Run: kotlinLSPKotlinDSLDeps")
        assertEquals(KlsActivity.Start(KLS_GRADLE_SCRIPT_DEPS_KIND, "Gradle: resolving build script dependencies…"), r)
    }

    @Test
    fun `Successfully resolved build script ends gradle-script-deps`() {
        val r = parseKlsActivity("[Thread-1] Successfully resolved build script dependencies")
        assertEquals(KlsActivity.End(KLS_GRADLE_SCRIPT_DEPS_KIND), r)
    }

    @Test
    fun `Successfully resolved (without build script) ends gradle-deps`() {
        val r = parseKlsActivity("[Thread-1] Successfully resolved dependencies for project")
        assertEquals(KlsActivity.End(KLS_GRADLE_DEPS_KIND), r)
    }

    @Test
    fun `Linting starts linting activity`() {
        val r = parseKlsActivity("[Thread-1] Linting file:///foo/Bar.kt")
        assertEquals(KlsActivity.Start(KLS_LINTING_KIND, "Analyzing…"), r)
    }

    @Test
    fun `Reported N diagnostics ends linting activity`() {
        val r = parseKlsActivity("[Thread-1] Reported 3 diagnostics in file:///foo/Bar.kt")
        assertEquals(KlsActivity.End(KLS_LINTING_KIND), r)
    }

    @Test
    fun `No diagnostics ends linting activity (zero-diagnostic case)`() {
        val r = parseKlsActivity("[Thread-1] No diagnostics in file:///foo/Bar.kt")
        assertEquals(KlsActivity.End(KLS_LINTING_KIND), r)
    }

    @Test
    fun `Reported requires diagnostic word in message`() {
        assertNull(parseKlsActivity("[Thread-1] Reported nothing of interest"))
    }

    @Test
    fun `Updating symbol index starts symbol-index activity`() {
        val r = parseKlsActivity("[Thread-1] Updating symbol index")
        assertEquals(KlsActivity.Start(KLS_SYMBOL_INDEX_KIND, "Indexing symbols…"), r)
    }

    @Test
    fun `Updating full symbol index also starts symbol-index`() {
        val r = parseKlsActivity("[Thread-1] Updating full symbol index for module foo")
        assertEquals(KlsActivity.Start(KLS_SYMBOL_INDEX_KIND, "Indexing symbols…"), r)
    }

    @Test
    fun `Updated symbol index ends symbol-index activity`() {
        val r = parseKlsActivity("[Thread-1] Updated symbol index")
        assertEquals(KlsActivity.End(KLS_SYMBOL_INDEX_KIND), r)
    }

    @Test
    fun `Updated full symbol index also ends symbol-index`() {
        val r = parseKlsActivity("[Thread-1] Updated full symbol index for module foo")
        assertEquals(KlsActivity.End(KLS_SYMBOL_INDEX_KIND), r)
    }

    @Test
    fun `short message without thread prefix is parsed as-is`() {
        val r = parseKlsActivity("Linting")
        assertEquals(KlsActivity.Start(KLS_LINTING_KIND, "Analyzing…"), r)
    }

    @Test
    fun `case-insensitive matching covers lowercase variants`() {
        assertEquals(
            KlsActivity.Start(KLS_LINTING_KIND, "Analyzing…"),
            parseKlsActivity("[Thread-1] linting file:///x.kt"),
        )
        assertEquals(
            KlsActivity.End(KLS_LINTING_KIND),
            parseKlsActivity("[Thread-1] no diagnostics in x.kt"),
        )
    }
}
