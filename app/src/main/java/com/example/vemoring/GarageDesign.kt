package com.example.vemoring

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

@Composable
fun CharacterStage(message: String, compact: Boolean = false, mood: MascotMood = MascotMood.NORMAL) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val wide = maxWidth >= 760.dp
        val characterWidth = if (wide) (if (compact) 390.dp else 460.dp) else maxWidth.coerceAtMost(320.dp)
        Box(Modifier.fillMaxWidth().height(if (compact) 390.dp else if (wide) 470.dp else 360.dp)) {
            CarCharacter(Modifier.align(Alignment.BottomCenter)
                .width(characterWidth)
                .fillMaxHeight(), mood = mood)
            Column(Modifier.align(Alignment.TopEnd).widthIn(max = if (wide) 360.dp else 300.dp)) {
                Surface(color = Color.White, shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp)) {
                    Text(message, Modifier.padding(horizontal = 28.dp, vertical = 22.dp),
                        color = Ink, fontSize = 28.sp, lineHeight = 40.sp, fontWeight = FontWeight.SemiBold)
                }
                Canvas(Modifier.padding(start = 42.dp).size(36.dp, 26.dp)) {
                    drawPath(Path().apply {
                        moveTo(0f, 0f); lineTo(size.width, 0f)
                        lineTo(0f, size.height); close()
                    }, Color.White)
                }
            }
        }
    }
}

@Composable
fun HomePage(content: @Composable ColumnScope.(Boolean) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxWidth >= 980.dp && maxHeight >= 900.dp &&
            androidx.compose.ui.platform.LocalDensity.current.fontScale <= 1.15f
        val scroll = rememberScrollState()
        Column(Modifier.fillMaxSize()
            .verticalScroll(scroll)
            .padding(start = 32.dp, top = if (compact) 4.dp else 12.dp, end = 32.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 16.dp)) {
            content(compact)
        }
    }
}

val GarageColors = lightColorScheme(
    primary = Blue, onPrimary = Color.White,
    primaryContainer = Color(0xFFDCEFFD), onPrimaryContainer = Ink,
    secondary = Mint, onSecondary = Color.White,
    secondaryContainer = Color(0xFFDEEFEA), onSecondaryContainer = Ink,
    background = Color(0xFFF0F4F8), onBackground = Ink,
    surface = Color.White, onSurface = Ink,
    surfaceVariant = Color(0xFFE6EEF5), onSurfaceVariant = Color(0xFF526678),
    outline = Color(0xFF6D8293), outlineVariant = Color(0xFFDCE5ED)
)

/** Single breakpoint, no device assumptions or per-frame layout work. */
@Composable
fun SplitContent(first: @Composable (Modifier) -> Unit, second: @Composable (Modifier) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth >= 980.dp) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(28.dp),
                verticalAlignment = Alignment.Top) {
                first(Modifier.weight(1f))
                second(Modifier.weight(1f))
            }
        } else {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                first(Modifier.fillMaxWidth())
                second(Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
fun SectionLabel(eyebrow: String, title: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(eyebrow, color = Blue, fontSize = 19.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium)
        Text(title, color = Ink, fontSize = 28.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun GarageNavigation(tab: Int, select: (Int) -> Unit) {
    NavigationBar(containerColor = Color.White, tonalElevation = 0.dp, modifier = Modifier.heightIn(min = 96.dp)) {
        listOf("홈", "관리 기록", "내 차").forEachIndexed { index, label ->
            NavigationBarItem(selected = tab == index, onClick = { select(index) },
                icon = { GarageGlyph(index, Modifier.size(28.dp), if (tab == index) Blue else Color(0xFF526678)) },
                label = { Text(label, fontSize = 22.sp, fontWeight = if (tab == index) FontWeight.Bold else FontWeight.Medium) },
                colors = NavigationBarItemDefaults.colors(selectedIconColor = Blue, selectedTextColor = Blue,
                    indicatorColor = Color(0xFFE2F0FA), unselectedTextColor = Color(0xFF526678)))
        }
    }
}

/** Small native line icons: no font glyphs, bitmaps, or extra icon dependency. */
@Composable
fun GarageGlyph(kind: Int, modifier: Modifier = Modifier, color: Color = Blue) {
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
