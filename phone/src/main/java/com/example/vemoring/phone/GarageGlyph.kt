package com.example.vemoring.phone

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Small native line icons: no font glyphs, bitmaps, or extra icon dependency. */
@Composable
fun GarageGlyph(kind: Int, modifier: Modifier = Modifier, color: Color = Color(0xFF087BBF)) {
    Canvas(modifier) {
        scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
            val stroke = Stroke(width = 1.8f, cap = StrokeCap.Round)
            when (kind) {
                0 -> {
                    val roof = Path().apply { moveTo(3f, 11f); lineTo(12f, 3f); lineTo(21f, 11f) }
                    drawPath(roof, color, style = stroke)
                    val house = Path().apply { moveTo(5f, 10f); lineTo(5f, 21f); lineTo(19f, 21f); lineTo(19f, 10f) }
                    drawPath(house, color, style = stroke)
                    drawRoundRect(color, Offset(10f, 14f), Size(4f, 7f), CornerRadius(1f), style = stroke)
                }
                1 -> {
                    drawRoundRect(color, Offset(3f, 5f), Size(18f, 16f), CornerRadius(3f), style = stroke)
                    drawLine(color, Offset(3f, 10f), Offset(21f, 10f), 1.8f)
                    drawLine(color, Offset(8f, 3f), Offset(8f, 7f), 1.8f, StrokeCap.Round)
                    drawLine(color, Offset(16f, 3f), Offset(16f, 7f), 1.8f, StrokeCap.Round)
                    drawCircle(color, 1.2f, Offset(8f, 15f)); drawCircle(color, 1.2f, Offset(15f, 15f))
                }
                else -> {
                    val car = Path().apply {
                        moveTo(3f, 12f); lineTo(6f, 5f); lineTo(18f, 5f); lineTo(21f, 12f)
                        lineTo(21f, 19f); lineTo(3f, 19f); close()
                    }
                    drawPath(car, color, style = stroke)
                    drawLine(color, Offset(4f, 11f), Offset(20f, 11f), 1.8f)
                    drawCircle(color, 1.2f, Offset(7f, 15f)); drawCircle(color, 1.2f, Offset(17f, 15f))
                    drawLine(color, Offset(5f, 19f), Offset(5f, 22f), 1.8f, StrokeCap.Round)
                    drawLine(color, Offset(19f, 19f), Offset(19f, 22f), 1.8f, StrokeCap.Round)
                }
            }
        }
    }
}
