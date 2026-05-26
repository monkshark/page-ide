package page.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TerminalShellTest {

    @Test
    fun `detected shells include at least one option on this platform`() {
        val shells = TerminalSession.detectShells()
        assertTrue(shells.isNotEmpty(), "expected at least one shell available")
    }

    @Test
    fun `default shell is picked from detected list`() {
        val first = TerminalSession.detectShells().firstOrNull()
        val default = TerminalSession.defaultShell()
        if (first != null) {
            assertEquals(first.kind, default.kind)
        }
    }

    @Test
    fun `buildCommand without elevation returns base executable + args`() {
        val shell = ShellOption(ShellKind.POWERSHELL, "pwsh.exe", listOf("-NoLogo", "-NoProfile"))
        val cmd = TerminalSession.buildCommand(shell, elevated = false)
        assertEquals(listOf("pwsh.exe", "-NoLogo", "-NoProfile"), cmd.toList())
    }

    @Test
    fun `buildCommand with elevation throws when gsudo is absent`() {
        val shell = ShellOption(ShellKind.CMD, "cmd.exe", listOf("/K"))
        val hasGsudo = System.getenv("PATH")?.split(java.io.File.pathSeparator)
            ?.any { dir -> java.io.File(dir, "gsudo.exe").exists() || java.io.File(dir, "gsudo").exists() } == true
        if (hasGsudo) return
        assertFailsWith<IllegalStateException> {
            TerminalSession.buildCommand(shell, elevated = true)
        }
    }

    @Test
    fun `ShellKind display names are not blank`() {
        for (kind in ShellKind.values()) {
            assertTrue(kind.display.isNotBlank(), "${kind.name} display should not be blank")
        }
    }
}
