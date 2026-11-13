package page.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class TextCenteringTest {

    @Test
    fun `small text sits in the middle of a taller button`() = runComposeUiTest {
        val sizes = listOf(11, 12, 13, 14)
        setContent {
            GlassTheme {
                Column {
                    sizes.forEach { size ->
                        Box(Modifier.width(120.dp).height(24.dp).background(Color.Black).testTag("cell-$size"), contentAlignment = Alignment.Center) {
                            Text(if (size % 2 == 0) "Show files" else "HHHH", color = Color.White, fontSize = size.sp)
                        }
                    }
                }
            }
        }
        for (size in sizes) {
            val image = onNodeWithTag("cell-$size").captureToImage().toAwtImage()
            val inked = (0 until image.height).filter { y -> (0 until image.width).any { x -> (image.getRGB(x, y) and 0xff) > 60 } }
            val above = inked.first()
            val below = image.height - 1 - inked.last()
            assertTrue(abs(above - below) <= 2, "${size}sp text is off center: $above px above, $below px below")
        }
    }
}
