import 'package:flutter/material.dart';

import 'caron_page.dart';

class CaronNavigationBar extends StatelessWidget {
  const CaronNavigationBar({
    super.key,
    required this.selectedIndex,
    required this.onSelected,
  });
  final int selectedIndex;
  final ValueChanged<int> onSelected;
  @override
  Widget build(BuildContext context) => Container(
    decoration: const BoxDecoration(
      color: Color(0xF7FFFFFF),
      border: Border(top: BorderSide(color: Color(0xFFE8E8E9))),
    ),
    child: SafeArea(
      top: false,
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 28),
        child: Row(
          children: [
            for (final (index, item) in const [
              ('홈', Icons.home_outlined),
              ('관리 기록', Icons.calendar_month_outlined),
              ('내 차', Icons.person_outline),
            ].indexed)
              Expanded(
                child: InkWell(
                  key: ValueKey('tab-$index'),
                  onTap: () => onSelected(index),
                  child: Semantics(
                    selected: selectedIndex == index,
                    button: true,
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Container(
                          width: 22,
                          height: 3,
                          decoration: BoxDecoration(
                            color: selectedIndex == index
                                ? caronBlue
                                : Colors.transparent,
                            borderRadius: const BorderRadius.vertical(
                              bottom: Radius.circular(5),
                            ),
                          ),
                        ),
                        Padding(
                          padding: const EdgeInsets.symmetric(vertical: 12),
                          child: Column(
                            children: [
                              Icon(
                                item.$2,
                                size: 24,
                                color: selectedIndex == index
                                    ? caronBlue
                                    : const Color(0xFF66727D),
                              ),
                              const SizedBox(height: 3),
                              Text(
                                item.$1,
                                style: TextStyle(
                                  fontSize: 12,
                                  fontWeight: FontWeight.w600,
                                  color: selectedIndex == index
                                      ? caronBlue
                                      : const Color(0xFF66727D),
                                ),
                              ),
                            ],
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
              ),
          ],
        ),
      ),
    ),
  );
}
