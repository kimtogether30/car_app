import 'package:flutter/material.dart';

/// Figma Make의 440 × 290 SVG를 같은 좌표계로 그린 캐릭터.
class VemoringCarCharacter extends StatelessWidget {
  const VemoringCarCharacter({super.key});

  @override
  Widget build(BuildContext context) => Semantics(
    label: '반쯤 감긴 눈을 가진 파란색 자동차 캐릭터',
    image: true,
    child: const AspectRatio(
      aspectRatio: 440 / 290,
      child: CustomPaint(painter: _CarPainter()),
    ),
  );
}

class _CarPainter extends CustomPainter {
  const _CarPainter();

  @override
  void paint(Canvas canvas, Size size) {
    canvas.save();
    canvas.scale(size.width / 440, size.height / 290);
    void fill(Path path, int color) =>
        canvas.drawPath(path, Paint()..color = Color(color));
    canvas.drawOval(
      const Rect.fromLTWH(56, 267, 328, 20),
      Paint()..color = const Color(0x2410295A),
    );
    for (final x in [48.0, 322.0]) {
      canvas.drawRRect(
        RRect.fromRectAndRadius(
          Rect.fromLTWH(x, 216, 70, 67),
          const Radius.circular(29),
        ),
        Paint()..color = const Color(0xFF142F62),
      );
    }
    fill(
      Path()
        ..moveTo(56, 166)
        ..cubicTo(64, 149, 69, 123, 76, 96)
        ..cubicTo(89, 48, 118, 18, 162, 13)
        ..cubicTo(200, 9, 240, 9, 278, 13)
        ..cubicTo(322, 18, 351, 48, 364, 96)
        ..cubicTo(371, 123, 376, 149, 384, 166)
        ..close(),
      0xFF0789E6,
    );
    final body = Path()
      ..moveTo(85, 151)
      ..cubicTo(72, 163, 60, 168, 38, 174)
      ..cubicTo(20, 179, 9, 194, 9, 215)
      ..lineTo(9, 235)
      ..cubicTo(9, 259, 28, 278, 52, 278)
      ..lineTo(388, 278)
      ..cubicTo(412, 278, 431, 259, 431, 235)
      ..lineTo(431, 215)
      ..cubicTo(431, 194, 420, 179, 402, 174)
      ..cubicTo(380, 168, 368, 163, 355, 151)
      ..cubicTo(331, 179, 286, 193, 220, 193)
      ..cubicTo(154, 193, 109, 179, 85, 151)
      ..close();
    canvas.drawPath(
      body,
      Paint()
        ..shader = const LinearGradient(
          colors: [Color(0xFF149FF1), Color(0xFF0879DB)],
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
        ).createShader(const Rect.fromLTRB(0, 150, 440, 275)),
    );
    final glass = Path()
      ..moveTo(82, 161)
      ..cubicTo(90, 142, 95, 119, 101, 96)
      ..cubicTo(110, 61, 130, 41, 166, 37)
      ..cubicTo(202, 33, 238, 33, 274, 37)
      ..cubicTo(310, 41, 330, 61, 339, 96)
      ..cubicTo(345, 119, 350, 142, 358, 161)
      ..lineTo(333, 181)
      ..lineTo(107, 181)
      ..close();
    canvas.drawPath(
      glass,
      Paint()
        ..shader = const LinearGradient(
          colors: [Color(0xFF48C5F2), Color(0xFF28AEEB)],
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
        ).createShader(const Rect.fromLTRB(0, 70, 440, 184)),
    );
    fill(
      Path()
        ..moveTo(112, 129)
        ..lineTo(206, 129)
        ..lineTo(206, 141)
        ..cubicTo(206, 174, 182, 195, 149, 195)
        ..cubicTo(110, 195, 88, 171, 85, 129)
        ..close(),
      0xFFF8FAF8,
    );
    fill(
      Path()
        ..moveTo(153, 129)
        ..lineTo(196, 129)
        ..lineTo(196, 141)
        ..cubicTo(196, 164, 182, 181, 162, 181)
        ..cubicTo(140, 181, 126, 163, 126, 137)
        ..lineTo(126, 129)
        ..close(),
      0xFF1B2D58,
    );
    fill(
      Path()
        ..moveTo(234, 129)
        ..lineTo(328, 129)
        ..cubicTo(325, 171, 303, 195, 264, 195)
        ..cubicTo(231, 195, 207, 174, 207, 141)
        ..lineTo(207, 129)
        ..close(),
      0xFFF8FAF8,
    );
    fill(
      Path()
        ..moveTo(244, 129)
        ..lineTo(287, 129)
        ..lineTo(287, 137)
        ..cubicTo(287, 163, 273, 181, 251, 181)
        ..cubicTo(231, 181, 217, 164, 217, 141)
        ..lineTo(217, 129)
        ..close(),
      0xFF1B2D58,
    );
    fill(
      Path()
        ..moveTo(124, 61)
        ..cubicTo(134, 50, 149, 47, 156, 53)
        ..cubicTo(162, 60, 156, 71, 144, 80)
        ..cubicTo(133, 88, 120, 90, 115, 84)
        ..cubicTo(110, 79, 115, 69, 124, 61)
        ..close(),
      0xE6DDF7F8,
    );
    fill(
      Path()
        ..moveTo(10, 191)
        ..cubicTo(34, 189, 52, 182, 67, 167)
        ..cubicTo(76, 158, 82, 146, 85, 131)
        ..lineTo(95, 161)
        ..cubicTo(87, 183, 69, 197, 42, 203)
        ..lineTo(10, 210)
        ..close(),
      0xFF159CEC,
    );
    fill(
      Path()
        ..moveTo(430, 191)
        ..cubicTo(406, 189, 388, 182, 373, 167)
        ..cubicTo(364, 158, 358, 146, 355, 131)
        ..lineTo(345, 161)
        ..cubicTo(353, 183, 371, 197, 398, 203)
        ..lineTo(430, 210)
        ..close(),
      0xFF159CEC,
    );
    fill(
      Path()
        ..moveTo(53, 207)
        ..cubicTo(60, 191, 77, 187, 98, 195)
        ..lineTo(119, 203)
        ..cubicTo(135, 210, 136, 229, 122, 238)
        ..cubicTo(107, 247, 74, 242, 60, 233)
        ..cubicTo(50, 226, 48, 216, 53, 207)
        ..close(),
      0xFFFFDB50,
    );
    fill(
      Path()
        ..moveTo(387, 207)
        ..cubicTo(380, 191, 363, 187, 342, 195)
        ..lineTo(321, 203)
        ..cubicTo(305, 210, 304, 229, 318, 238)
        ..cubicTo(333, 247, 366, 242, 380, 233)
        ..cubicTo(390, 226, 392, 216, 387, 207)
        ..close(),
      0xFFFFDB50,
    );
    fill(
      Path()
        ..moveTo(158, 237)
        ..lineTo(282, 237)
        ..cubicTo(290, 237, 296, 243, 296, 250)
        ..cubicTo(296, 257, 290, 263, 282, 263)
        ..lineTo(158, 263)
        ..cubicTo(150, 263, 144, 257, 144, 250)
        ..cubicTo(144, 243, 150, 237, 158, 237)
        ..close(),
      0xFF086DCA,
    );
    fill(
      Path()
        ..moveTo(55, 266)
        ..lineTo(385, 266)
        ..cubicTo(377, 274, 365, 278, 351, 278)
        ..lineTo(89, 278)
        ..cubicTo(75, 278, 63, 274, 55, 266)
        ..close(),
      0xFF0875D3,
    );
    canvas.restore();
  }

  @override
  bool shouldRepaint(covariant _CarPainter oldDelegate) => false;
}
