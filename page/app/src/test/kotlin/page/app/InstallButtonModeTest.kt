package page.app

import page.runtime.*

import kotlin.test.Test
import kotlin.test.assertEquals

class InstallButtonModeTest {

    @Test
    fun installModeWhenNothingSelected() {
        val mode = installButtonMode(
            progress = null,
            selectedVersion = null,
            activeVersion = "1.3.13-page-1 (fork)",
            installedVersions = listOf("1.3.13-page-1 (fork)"),
        )
        assertEquals(InstallButtonMode.Install, mode)
    }

    @Test
    fun alreadyActiveWhenSelectedMatchesActive() {
        val mode = installButtonMode(
            progress = null,
            selectedVersion = "1.3.13-page-1 (fork)",
            activeVersion = "1.3.13-page-1 (fork)",
            installedVersions = listOf("1.3.13-page-1 (fork)"),
        )
        assertEquals(InstallButtonMode.AlreadyActive, mode)
    }

    @Test
    fun applyWhenSelectedInstalledButNotActive() {
        val mode = installButtonMode(
            progress = null,
            selectedVersion = "1.3.12 (upstream)",
            activeVersion = "1.3.13-page-1 (fork)",
            installedVersions = listOf("1.3.13-page-1 (fork)", "1.3.12 (upstream)"),
        )
        assertEquals(InstallButtonMode.Apply, mode)
    }

    @Test
    fun installWhenSelectedNotInstalled() {
        val mode = installButtonMode(
            progress = null,
            selectedVersion = "1.3.11 (upstream)",
            activeVersion = "1.3.13-page-1 (fork)",
            installedVersions = listOf("1.3.13-page-1 (fork)"),
        )
        assertEquals(InstallButtonMode.Install, mode)
    }

    @Test
    fun progressOverridesEverythingElse() {
        val mode = installButtonMode(
            progress = LspInstaller.Progress.Downloading(0, 100),
            selectedVersion = "1.3.13-page-1 (fork)",
            activeVersion = "1.3.13-page-1 (fork)",
            installedVersions = listOf("1.3.13-page-1 (fork)"),
        )
        assertEquals(InstallButtonMode.Install, mode)
    }
}
