package page.app.ui

import androidx.compose.material3.Text
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import page.app.mvi.SideView
import page.ui.GlassTheme
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class SidebarFrameTest {

    @Test
    fun `the sidebar is headed by the project it shows`() = runComposeUiTest {
        setContent {
            GlassTheme { SidebarFrame(projectName = "IDE", active = SideView.FILES, onSelect = {}) { Text("tree") } }
        }
        onNodeWithText("IDE").assertIsDisplayed()
        onNodeWithText("tree").assertIsDisplayed()
    }

    @Test
    fun `without a project the header says so`() = runComposeUiTest {
        setContent {
            GlassTheme { SidebarFrame(projectName = null, active = SideView.FILES, onSelect = {}) {} }
        }
        onNodeWithText("No folder open").assertIsDisplayed()
    }
}
