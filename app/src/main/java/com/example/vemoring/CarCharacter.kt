package com.example.vemoring
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

@Composable
fun CarCharacter(modifier: Modifier = Modifier, concerned: Boolean = false) {
    Canvas(modifier.semantics { contentDescription = "반쯤 감긴 눈을 가진 파란색 자동차 캐릭터" }) {
        scale(size.width / 440f, size.height / 290f, Offset.Zero) {
            drawOval(Color(0x2410295A), Offset(56f,267f), Size(328f,20f))
            listOf(48f,322f).forEach { x -> drawRoundRect(Color(0xFF142F62), Offset(x,216f), Size(70f,67f), CornerRadius(29f)) }
            drawPath(Path().apply { moveTo(56f, 166f); cubicTo(64f, 149f, 69f, 123f, 76f, 96f); cubicTo(89f, 48f, 118f, 18f, 162f, 13f); cubicTo(200f, 9f, 240f, 9f, 278f, 13f); cubicTo(322f, 18f, 351f, 48f, 364f, 96f); cubicTo(371f, 123f, 376f, 149f, 384f, 166f); close() }, Color(0xFF0789E6))
            drawPath(Path().apply { moveTo(85f, 151f); cubicTo(72f, 163f, 60f, 168f, 38f, 174f); cubicTo(20f, 179f, 9f, 194f, 9f, 215f); lineTo(9f, 235f); cubicTo(9f, 259f, 28f, 278f, 52f, 278f); lineTo(388f, 278f); cubicTo(412f, 278f, 431f, 259f, 431f, 235f); lineTo(431f, 215f); cubicTo(431f, 194f, 420f, 179f, 402f, 174f); cubicTo(380f, 168f, 368f, 163f, 355f, 151f); cubicTo(331f, 179f, 286f, 193f, 220f, 193f); cubicTo(154f, 193f, 109f, 179f, 85f, 151f); close() }, Brush.verticalGradient(listOf(Color(0xFF149FF1),Color(0xFF0879DB)), startY = 70f, endY = 275f))
            drawPath(Path().apply { moveTo(82f, 161f); cubicTo(90f, 142f, 95f, 119f, 101f, 96f); cubicTo(110f, 61f, 130f, 41f, 166f, 37f); cubicTo(202f, 33f, 238f, 33f, 274f, 37f); cubicTo(310f, 41f, 330f, 61f, 339f, 96f); cubicTo(345f, 119f, 350f, 142f, 358f, 161f); lineTo(333f, 181f); lineTo(107f, 181f); close() }, Brush.verticalGradient(listOf(Color(0xFF48C5F2),Color(0xFF28AEEB)), startY = 70f, endY = 275f))
            drawPath(Path().apply { moveTo(112f, 129f); lineTo(206f, 129f); lineTo(206f, 141f); cubicTo(206f, 174f, 182f, 195f, 149f, 195f); cubicTo(110f, 195f, 88f, 171f, 85f, 129f); close() }, Color(0xFFF8FAF8))
            drawPath(Path().apply { moveTo(153f, 129f); lineTo(196f, 129f); lineTo(196f, 141f); cubicTo(196f, 164f, 182f, 181f, 162f, 181f); cubicTo(140f, 181f, 126f, 163f, 126f, 137f); lineTo(126f, 129f); close() }, Color(0xFF1B2D58))
            drawPath(Path().apply { moveTo(234f, 129f); lineTo(328f, 129f); cubicTo(325f, 171f, 303f, 195f, 264f, 195f); cubicTo(231f, 195f, 207f, 174f, 207f, 141f); lineTo(207f, 129f); close() }, Color(0xFFF8FAF8))
            drawPath(Path().apply { moveTo(244f, 129f); lineTo(287f, 129f); lineTo(287f, 137f); cubicTo(287f, 163f, 273f, 181f, 251f, 181f); cubicTo(231f, 181f, 217f, 164f, 217f, 141f); lineTo(217f, 129f); close() }, Color(0xFF1B2D58))
            drawPath(Path().apply { moveTo(124f, 61f); cubicTo(134f, 50f, 149f, 47f, 156f, 53f); cubicTo(162f, 60f, 156f, 71f, 144f, 80f); cubicTo(133f, 88f, 120f, 90f, 115f, 84f); cubicTo(110f, 79f, 115f, 69f, 124f, 61f); close() }, Color(0xE6DDF7F8))
            drawPath(Path().apply { moveTo(10f, 191f); cubicTo(34f, 189f, 52f, 182f, 67f, 167f); cubicTo(76f, 158f, 82f, 146f, 85f, 131f); lineTo(95f, 161f); cubicTo(87f, 183f, 69f, 197f, 42f, 203f); lineTo(10f, 210f); close() }, Color(0xFF159CEC))
            drawPath(Path().apply { moveTo(430f, 191f); cubicTo(406f, 189f, 388f, 182f, 373f, 167f); cubicTo(364f, 158f, 358f, 146f, 355f, 131f); lineTo(345f, 161f); cubicTo(353f, 183f, 371f, 197f, 398f, 203f); lineTo(430f, 210f); close() }, Color(0xFF159CEC))
            drawPath(Path().apply { moveTo(53f, 207f); cubicTo(60f, 191f, 77f, 187f, 98f, 195f); lineTo(119f, 203f); cubicTo(135f, 210f, 136f, 229f, 122f, 238f); cubicTo(107f, 247f, 74f, 242f, 60f, 233f); cubicTo(50f, 226f, 48f, 216f, 53f, 207f); close() }, Color(0xFFFFDB50))
            drawPath(Path().apply { moveTo(387f, 207f); cubicTo(380f, 191f, 363f, 187f, 342f, 195f); lineTo(321f, 203f); cubicTo(305f, 210f, 304f, 229f, 318f, 238f); cubicTo(333f, 247f, 366f, 242f, 380f, 233f); cubicTo(390f, 226f, 392f, 216f, 387f, 207f); close() }, Color(0xFFFFDB50))
            drawPath(Path().apply { moveTo(158f, 237f); lineTo(282f, 237f); cubicTo(290f, 237f, 296f, 243f, 296f, 250f); cubicTo(296f, 257f, 290f, 263f, 282f, 263f); lineTo(158f, 263f); cubicTo(150f, 263f, 144f, 257f, 144f, 250f); cubicTo(144f, 243f, 150f, 237f, 158f, 237f); close() }, Color(0xFF086DCA))
            if(concerned) {
                drawPath(Path().apply {
                    moveTo(180f,255f); quadraticTo(220f,225f,260f,255f)
                }, Color(0xFF1B2D58), style=androidx.compose.ui.graphics.drawscope.Stroke(width=7f))
            }
            drawPath(Path().apply { moveTo(55f, 266f); lineTo(385f, 266f); cubicTo(377f, 274f, 365f, 278f, 351f, 278f); lineTo(89f, 278f); cubicTo(75f, 278f, 63f, 274f, 55f, 266f); close() }, Color(0xFF0875D3))
        }
    }
}
