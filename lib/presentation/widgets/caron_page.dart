import 'package:flutter/material.dart';

const caronBlue = Color(0xFF335C81);
const caronInk = Color(0xFF202C35);
const caronNavy = Color(0xFF253F52);
const caronMint = Color(0xFF387969);
const caronOrange = Color(0xFFFF8A3D);

BoxDecoration caronCard() => BoxDecoration(
  color: Colors.white,
  borderRadius: BorderRadius.circular(14),
  border: Border.all(color: const Color(0xFFECECEF)),
);

class CaronPage extends StatelessWidget {
  const CaronPage({
    super.key,
    required this.child,
    required this.navigationBar,
  });
  final Widget child, navigationBar;
  @override
  Widget build(BuildContext context) => ColoredBox(
    color: const Color(0xFFE8E9ED),
    child: Center(
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 520),
        child: Scaffold(
          backgroundColor: const Color(0xFFF6F7F8),
          body: SafeArea(
            bottom: false,
            child: DefaultTextStyle.merge(
              style: const TextStyle(color: caronInk, fontSize: 12),
              child: child,
            ),
          ),
          bottomNavigationBar: navigationBar,
        ),
      ),
    ),
  );
}

class CaronPageHeader extends StatelessWidget {
  const CaronPageHeader({
    super.key,
    required this.eyebrow,
    required this.title,
    required this.trailing,
  });
  final String eyebrow, title;
  final Widget trailing;
  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.only(top: 24, bottom: 18),
    child: Row(
      children: [
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                eyebrow,
                style: const TextStyle(
                  fontSize: 12,
                  letterSpacing: 1.0,
                  fontWeight: FontWeight.w700,
                  color: caronBlue,
                ),
              ),
              const SizedBox(height: 4),
              Text(
                title,
                style: const TextStyle(
                  fontSize: 27,
                  fontWeight: FontWeight.w700,
                  letterSpacing: -1,
                ),
              ),
            ],
          ),
        ),
        trailing,
      ],
    ),
  );
}

class CaronSampleNotice extends StatelessWidget {
  const CaronSampleNotice({super.key});
  @override
  Widget build(BuildContext context) => const Padding(
    padding: EdgeInsets.only(bottom: 12),
    child: Text(
      '관리 날짜·기록과 알림 기준은 이 기기/브라우저에 저장됩니다.\n차량 상태·날씨·예상 주행거리는 예시입니다.',
      style: TextStyle(fontSize: 12, color: Color(0xFF777D90)),
    ),
  );
}

Future<T?> caronSheet<T>(BuildContext context, Widget child) =>
    showModalBottomSheet<T>(
      context: context,
      isScrollControlled: true,
      useSafeArea: true,
      backgroundColor: const Color(0xFFFBFAF6),
      constraints: const BoxConstraints(maxWidth: 520),
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(28)),
      ),
      builder: (context) => Padding(
        padding: EdgeInsets.only(
          bottom: MediaQuery.viewInsetsOf(context).bottom,
        ),
        child: ConstrainedBox(
          constraints: BoxConstraints(
            maxHeight: MediaQuery.sizeOf(context).height * .88,
          ),
          child: child,
        ),
      ),
    );

class CaronSheetHeader extends StatelessWidget {
  const CaronSheetHeader({super.key, required this.title});
  final String title;
  @override
  Widget build(BuildContext context) => Row(
    children: [
      Expanded(
        child: Text(
          title,
          style: const TextStyle(
            fontSize: 24,
            fontWeight: FontWeight.w700,
            color: caronInk,
          ),
        ),
      ),
      IconButton(
        tooltip: '닫기',
        onPressed: () => Navigator.pop(context),
        icon: const Icon(Icons.close),
      ),
    ],
  );
}
