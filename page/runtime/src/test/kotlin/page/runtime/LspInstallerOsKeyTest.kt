package page.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LspInstallerOsKeyTest {

    @Test
    fun osKeyMapsMacWindowsLinux() {
        assertEquals("macos", LspInstaller.osKey("Mac OS X"))
        assertEquals("macos", LspInstaller.osKey("Darwin"))
        assertEquals("windows", LspInstaller.osKey("Windows 11"))
        assertEquals("linux", LspInstaller.osKey("Linux"))
        assertEquals("linux", LspInstaller.osKey(""))
    }

    @Test
    fun isWindowsRecognizesWindowsNames() {
        assertTrue(LspInstaller.isWindows("Windows 10"))
        assertTrue(LspInstaller.isWindows("WINDOWS"))
        assertFalse(LspInstaller.isWindows("Linux"))
        assertFalse(LspInstaller.isWindows("Mac OS X"))
    }

    @Test
    fun lspHomeIsUnderPageIde() {
        val home = LspInstaller.lspHome().toString()
        assertTrue(home.contains(".page-ide"), "lspHome should live under .page-ide but was $home")
        assertTrue(home.endsWith("lsp"), "lspHome should end with lsp segment")
    }
}
