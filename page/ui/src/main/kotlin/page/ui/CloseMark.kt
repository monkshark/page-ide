package page.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun CloseMark(tint: Color, size: Dp = 10.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val stroke = 1.5.dp.toPx()
        val inset = stroke / 2f
        drawLine(tint, Offset(inset, inset), Offset(this.size.width - inset, this.size.height - inset), stroke, StrokeCap.Round)
        drawLine(tint, Offset(this.size.width - inset, inset), Offset(inset, this.size.height - inset), stroke, StrokeCap.Round)
    }
}
