package page.app

import page.runtime.*

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

@Composable
fun TreeChevron(expanded: Boolean, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier = modifier.size(16.dp)) {
        val stroke = 1.25.dp.toPx()
        val arm = 3.dp.toPx()
        val cx = size.width / 2f
        val cy = size.height / 2f
        val path = Path()
        if (expanded) {
            path.moveTo(cx - arm, cy - arm / 2f)
            path.lineTo(cx, cy + arm / 2f)
            path.lineTo(cx + arm, cy - arm / 2f)
        } else {
            path.moveTo(cx - arm / 2f, cy - arm)
            path.lineTo(cx + arm / 2f, cy)
            path.lineTo(cx - arm / 2f, cy + arm)
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}
