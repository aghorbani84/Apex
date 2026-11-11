package com.apex.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apex.domain.MonthlyReturnRecord
import kotlin.math.abs
import kotlin.math.max

/**
 * Pure Compose Multiplatform Monthly Returns Bar Chart.
 * Green bars represent positive returns; red bars represent negative returns.
 */
@Composable
fun MonthlyReturnsChart(
    monthlyReturns: List<MonthlyReturnRecord>,
    modifier: Modifier = Modifier.fillMaxWidth().height(160.dp),
) {
    if (monthlyReturns.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("No monthly return data", color = Color.Gray, fontSize = 12.sp)
        }
        return
    }

    val maxVal = max(5.0, monthlyReturns.maxOfOrNull { abs(it.returnPct) } ?: 5.0)

    Canvas(modifier = modifier.background(Color(0xFF16191F)).padding(8.dp)) {
        val w = size.width
        val h = size.height
        val midY = h / 2.0f

        // Center 0% baseline
        drawLine(
            color = Color(0xFF455A64),
            start = Offset(0f, midY),
            end = Offset(w, midY),
            strokeWidth = 1.dp.toPx(),
        )

        val barCount = monthlyReturns.size
        val barWidth = (w / barCount) * 0.7f
        val gap = (w / barCount) * 0.3f

        monthlyReturns.forEachIndexed { i, m ->
            val barHeight = ((abs(m.returnPct) / maxVal) * (h / 2.0f)).toFloat()
            val x = i * (barWidth + gap) + gap / 2.0f
            val y = if (m.returnPct >= 0) midY - barHeight else midY
            val color = if (m.returnPct >= 0) Color(0xFF66BB6A) else Color(0xFFEF5350)

            drawRect(
                color = color,
                topLeft = Offset(x, y),
                size = Size(barWidth, max(2f, barHeight)),
            )
        }
    }
}

/**
 * Pure Compose Multiplatform Drawdown Underwater Line/Area Chart.
 * Includes a dashed red reference line at the -10.0% max-drawdown kill-switch limit.
 */
@Composable
fun DrawdownChart(
    drawdownSeries: List<Double>,
    modifier: Modifier = Modifier.fillMaxWidth().height(140.dp),
) {
    if (drawdownSeries.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("No drawdown data", color = Color.Gray, fontSize = 12.sp)
        }
        return
    }

    val maxDd = max(12.0, (drawdownSeries.maxOrNull() ?: 10.0) * 1.2)

    Canvas(modifier = modifier.background(Color(0xFF16191F)).padding(8.dp)) {
        val w = size.width
        val h = size.height

        // Top 0% line
        drawLine(
            color = Color(0xFF37474F),
            start = Offset(0f, 0f),
            end = Offset(w, 0f),
            strokeWidth = 1.dp.toPx(),
        )

        // 10% Kill-Switch horizontal barrier
        val killSwitchY = ((10.0 / maxDd) * h).toFloat()
        drawLine(
            color = Color(0xFFFF5252),
            start = Offset(0f, killSwitchY),
            end = Offset(w, killSwitchY),
            strokeWidth = 1.5.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f),
        )

        val path = Path()
        val fillPath = Path()
        val stepX = w / (drawdownSeries.size - 1).coerceAtLeast(1)

        drawdownSeries.forEachIndexed { index, dd ->
            val x = index * stepX
            val y = ((dd / maxDd) * h).toFloat()
            if (index == 0) {
                path.moveTo(x, y)
                fillPath.moveTo(x, 0f)
                fillPath.lineTo(x, y)
            } else {
                path.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
        }

        fillPath.lineTo(w, 0f)
        fillPath.close()

        drawPath(fillPath, color = Color(0x33EF5350))
        drawPath(path, color = Color(0xFFE57373), style = Stroke(width = 2.dp.toPx()))
    }
}
