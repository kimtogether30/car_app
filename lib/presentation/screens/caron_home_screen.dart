import 'package:flutter/material.dart';

import '../../shared/models/vehicle_status.dart';
import '../widgets/caron_car_character.dart';

const _blue = Color(0xFF3157D5);
const _ink = Color(0xFF18203B);
const _navy = Color(0xFF182E78);
const _muted = Color(0xFF858996);
const _mint = Color(0xFF20B486);

/// Figma Make 홈 화면. 예시 값은 UI 확인용이며 실제 상태를 주입할 수 있습니다.
/// 관리 기록·내 차 화면은 구현하지 않고 콜백으로 기존 기능에 연결합니다.
class CaronHomeScreen extends StatelessWidget {
  const CaronHomeScreen({
    super.key,
    this.vehicle = const VehicleStatus(
      vehicleName: '몽글이',
      licensePlate: '123가 4567',
      vehicleType: VehicleType.combustion,
      fuelLevelPercent: 68,
      cleanlinessPercent: 82,
      characterMessage: '컨디션 최고!',
    ),
    this.vehicleModel = '현대 캐스퍼',
    this.dateLabel = 'MONDAY, 24 MARCH',
    this.rangeLabel = '412 km',
    this.lastWashLabel = '5일 전',
    this.lastFuelLabel,
    this.lastOilLabel,
    this.nextCareLabel = '엔진오일 교체까지 12일 남았어요',
    this.onNotifications,
    this.onEditNickname,
    this.onCalendar,
    this.onProfile,
    this.navigationBar,
  });

  final VehicleStatus vehicle;
  final String? lastFuelLabel, lastOilLabel;
  final String vehicleModel,
      dateLabel,
      rangeLabel,
      lastWashLabel,
      nextCareLabel;
  final VoidCallback? onNotifications, onEditNickname, onCalendar, onProfile;
  final Widget? navigationBar;

  void _action(BuildContext context, VoidCallback? callback, String message) {
    if (callback != null) {
      callback();
    } else {
      ScaffoldMessenger.of(context)
          .showSnackBar(SnackBar(content: Text(message)));
    }
  }

  @override
  Widget build(BuildContext context) => DefaultTextStyle.merge(
    style: const TextStyle(color: _ink, fontSize: 12, height: 1.4),
    child: ColoredBox(
      color: const Color(0xFFE8E9ED),
      child: Center(
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 520),
          child: Scaffold(
            backgroundColor: const Color(0xFFF8F7F2),
            body: SafeArea(
              bottom: false,
              child: SingleChildScrollView(
                padding: const EdgeInsets.fromLTRB(23, 18, 23, 16),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    _header(context),
                    const SizedBox(height: 28),
                    Text(
                      dateLabel,
                      style: const TextStyle(
                        color: _blue,
                        fontSize: 10,
                        fontWeight: FontWeight.w700,
                        letterSpacing: 1.8,
                      ),
                    ),
                    const SizedBox(height: 8),
                    Text.rich(
                      TextSpan(
                        children: [
                          TextSpan(text: '오늘도 ${vehicle.vehicleName}와\n'),
                          const TextSpan(
                            text: '기분 좋은 드라이브!',
                            style: TextStyle(fontWeight: FontWeight.w800),
                          ),
                        ],
                      ),
                      style: const TextStyle(
                        fontSize: 25,
                        height: 1.4,
                        letterSpacing: -.9,
                        color: _ink,
                      ),
                    ),
                    _hero(),
                    Row(
                      children: [
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                vehicle.vehicleName,
                                style: const TextStyle(
                                  fontSize: 22,
                                  fontWeight: FontWeight.w700,
                                  letterSpacing: -.5,
                                ),
                              ),
                              const SizedBox(height: 3),
                              Text(
                                '$vehicleModel · ${vehicle.licensePlate}',
                                style: const TextStyle(
                                  color: _muted,
                                  fontSize: 12,
                                ),
                              ),
                            ],
                          ),
                        ),
                        IconButton(
                          tooltip: '닉네임 수정',
                          onPressed: () => _action(
                            context,
                            onEditNickname,
                            '닉네임 수정 기능은 아직 연결되지 않았어요.',
                          ),
                          style: IconButton.styleFrom(
                            backgroundColor: const Color(0xFFECEEF5),
                            shape: RoundedRectangleBorder(
                              borderRadius: BorderRadius.circular(10),
                            ),
                          ),
                          icon: const Icon(
                            Icons.edit_outlined,
                            size: 17,
                            color: Color(0xFF60677C),
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 21),
                    _stats(),
                    if (lastFuelLabel != null || lastOilLabel != null)
                      Wrap(
                        spacing: 12,
                        runSpacing: 4,
                        children: [
                          Text(
                            '마지막 세차일: $lastWashLabel',
                            key: const ValueKey('home-last-wash'),
                            style: const TextStyle(fontSize: 10, color: _muted),
                          ),
                          if (lastFuelLabel != null)
                            Text(
                              '마지막 주유일: $lastFuelLabel',
                              key: const ValueKey('home-last-fuel'),
                              style: const TextStyle(
                                fontSize: 10,
                                color: _muted,
                              ),
                            ),
                          if (lastOilLabel != null)
                            Text(
                              '마지막 엔진오일 교체일: $lastOilLabel',
                              key: const ValueKey('home-last-oil'),
                              style: const TextStyle(
                                fontSize: 10,
                                color: _muted,
                              ),
                            ),
                        ],
                      ),
                    const SizedBox(height: 12),
                    const _WeatherCard(),
                    const SizedBox(height: 12),
                    Material(
                      color: _navy,
                      borderRadius: BorderRadius.circular(18),
                      child: InkWell(
                        borderRadius: BorderRadius.circular(18),
                        onTap: () => _action(
                          context,
                          onCalendar,
                          '관리 기록 화면은 아직 연결되지 않았어요.',
                        ),
                        child: Padding(
                          padding: const EdgeInsets.symmetric(
                            horizontal: 14,
                            vertical: 13,
                          ),
                          child: Row(
                            children: [
                              _badge(
                                Icons.oil_barrel_outlined,
                                Colors.white,
                                const Color(0x22FFFFFF),
                                size: 39,
                              ),
                              const SizedBox(width: 11),
                              Expanded(
                                child: Column(
                                  crossAxisAlignment: CrossAxisAlignment.start,
                                  children: [
                                    const Text(
                                      '다가오는 관리 일정',
                                      style: TextStyle(
                                        color: Color(0xAAFFFFFF),
                                        fontSize: 9,
                                      ),
                                    ),
                                    Text(
                                      nextCareLabel,
                                      style: const TextStyle(
                                        color: Colors.white,
                                        fontSize: 12,
                                        fontWeight: FontWeight.w700,
                                      ),
                                    ),
                                  ],
                                ),
                              ),
                              const Icon(
                                Icons.chevron_right,
                                color: Colors.white,
                                size: 20,
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
            bottomNavigationBar: navigationBar ?? _bottomNav(context),
          ),
        ),
      ),
    ),
  );

  Widget _header(BuildContext context) => Row(
    children: [
      Transform.rotate(
        angle: -.052,
        child: _badge(
          Icons.directions_car_outlined,
          Colors.white,
          _blue,
          size: 35,
        ),
      ),
      const SizedBox(width: 9),
      const Text(
        'CAR:ON',
        style: TextStyle(
          color: _ink,
          fontSize: 19,
          fontWeight: FontWeight.w800,
          letterSpacing: -.5,
        ),
      ),
      const Spacer(),
      Stack(
        children: [
          IconButton(
            tooltip: '알림 열기',
            onPressed: () =>
                _action(context, onNotifications, '알림 화면은 아직 연결되지 않았어요.'),
            style: IconButton.styleFrom(
              backgroundColor: Colors.white.withValues(alpha: .75),
              side: const BorderSide(color: Color(0xFFE2E2DE)),
            ),
            icon: const Icon(
              Icons.notifications_none_rounded,
              color: Color(0xFF303750),
              size: 22,
            ),
          ),
          Positioned(
            right: 7,
            top: 7,
            child: IgnorePointer(
              child: Container(
                width: 10,
                height: 10,
                decoration: BoxDecoration(
                  color: const Color(0xFFFF7B32),
                  shape: BoxShape.circle,
                  border: Border.all(color: Colors.white, width: 2),
                ),
              ),
            ),
          ),
        ],
      ),
    ],
  );

  Widget _hero() => SizedBox(
    width: double.infinity,
    height: 218,
    child: Stack(
      alignment: Alignment.bottomCenter,
      children: [
        Positioned(
          bottom: 14,
          child: const ClipOval(
            child: SizedBox(
              width: 270,
              height: 145,
              child: ColoredBox(color: Color(0xFFE5EAFF)),
            ),
          ),
        ),
        const SizedBox(width: 310, child: CaronCarCharacter()),
        Positioned(
          right: 13,
          top: 20,
          child: Container(
            constraints: const BoxConstraints(maxWidth: 230),
            padding: const EdgeInsets.symmetric(horizontal: 13, vertical: 9),
            decoration: const BoxDecoration(
              color: _navy,
              borderRadius: BorderRadius.only(
                topLeft: Radius.circular(15),
                topRight: Radius.circular(15),
                bottomLeft: Radius.circular(15),
                bottomRight: Radius.circular(3),
              ),
              boxShadow: [
                BoxShadow(
                  color: Color(0x382543A3),
                  offset: Offset(0, 7),
                  blurRadius: 16,
                ),
              ],
            ),
            child: Text(
              vehicle.characterMessage,
              style: const TextStyle(
                color: Colors.white,
                fontSize: 12,
                fontWeight: FontWeight.w700,
              ),
            ),
          ),
        ),
      ],
    ),
  );

  Widget _stats() {
    final cards = <Widget>[
      if (vehicle.vehicleType != VehicleType.electric)
        _StatCard(
          title: '남은 연료',
          value: vehicle.fuelLevelPercent,
          icon: Icons.local_gas_station_outlined,
          color: _blue,
          detail: '주행 가능 거리',
          detailValue: rangeLabel,
        ),
      if (vehicle.vehicleType != VehicleType.combustion)
        _StatCard(
          title: '남은 배터리',
          value: vehicle.batteryLevelPercent,
          icon: Icons.battery_charging_full_outlined,
          color: _blue,
          detail: '주행 가능 거리',
          detailValue: rangeLabel,
        ),
      _StatCard(
        title: '차량 청결도',
        value: vehicle.cleanlinessPercent,
        icon: Icons.auto_awesome_outlined,
        color: _mint,
        detail: '마지막 세차',
        detailValue: lastWashLabel,
      ),
    ];
    return LayoutBuilder(
      builder: (context, constraints) => Wrap(
        spacing: 11,
        runSpacing: 11,
        children: [
          for (final card in cards)
            SizedBox(width: (constraints.maxWidth - 11) / 2, child: card),
        ],
      ),
    );
  }

  Widget _bottomNav(BuildContext context) => Container(
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
            _NavItem(
              label: '홈',
              icon: Icons.home_outlined,
              selected: true,
              onTap: () {},
            ),
            _NavItem(
              label: '관리 기록',
              icon: Icons.calendar_month_outlined,
              onTap: () =>
                  _action(context, onCalendar, '관리 기록 화면은 아직 연결되지 않았어요.'),
            ),
            _NavItem(
              label: '내 차',
              icon: Icons.person_outline,
              onTap: () => _action(context, onProfile, '내 차 화면은 아직 연결되지 않았어요.'),
            ),
          ],
        ),
      ),
    ),
  );
}

Widget _badge(
  IconData icon,
  Color color,
  Color background, {
  double size = 29,
}) => Container(
  width: size,
  height: size,
  decoration: BoxDecoration(
    color: background,
    borderRadius: BorderRadius.circular(size * .31),
  ),
  child: Icon(icon, size: size == 29 ? 19 : 22, color: color),
);

BoxDecoration _cardDecoration() => BoxDecoration(
  color: Colors.white,
  borderRadius: BorderRadius.circular(20),
  border: Border.all(color: const Color(0xFFEEEEF0)),
  boxShadow: const [
    BoxShadow(color: Color(0x0E212B4B), offset: Offset(0, 6), blurRadius: 20),
  ],
);

class _StatCard extends StatelessWidget {
  const _StatCard({
    required this.title,
    required this.value,
    required this.icon,
    required this.color,
    required this.detail,
    required this.detailValue,
  });
  final String title, detail, detailValue;
  final double? value;
  final IconData icon;
  final Color color;

  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.all(16),
    decoration: _cardDecoration(),
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            _badge(
              icon,
              color,
              color == _mint
                  ? const Color(0xFFDEF7EF)
                  : const Color(0xFFE9EDFF),
            ),
            const SizedBox(width: 8),
            Expanded(
              child: Text(
                title,
                style: const TextStyle(
                  fontSize: 11,
                  fontWeight: FontWeight.w700,
                  color: Color(0xFF606578),
                ),
              ),
            ),
          ],
        ),
        const SizedBox(height: 10),
        Text.rich(
          TextSpan(
            children: [
              TextSpan(text: value == null ? '—' : value!.toStringAsFixed(0)),
              if (value != null)
                const TextSpan(text: ' %', style: TextStyle(fontSize: 15)),
            ],
          ),
          style: const TextStyle(
            fontSize: 27,
            fontWeight: FontWeight.w700,
            letterSpacing: -1,
          ),
        ),
        const SizedBox(height: 7),
        Semantics(
          label: '$title ${value == null ? '정보 없음' : '${value!.round()}퍼센트'}',
          child: ClipRRect(
            borderRadius: BorderRadius.circular(9),
            child: LinearProgressIndicator(
              value: ((value ?? 0) / 100).clamp(0, 1),
              minHeight: 5,
              backgroundColor: const Color(0xFFEEF0F5),
              color: color,
            ),
          ),
        ),
        const SizedBox(height: 8),
        Text.rich(
          TextSpan(
            children: [
              TextSpan(text: value == null ? '정보를 기다리고 있어요' : '$detail '),
              if (value != null)
                TextSpan(
                  text: detailValue,
                  style: const TextStyle(
                    color: Color(0xFF4C5163),
                    fontWeight: FontWeight.w700,
                  ),
                ),
            ],
          ),
          style: const TextStyle(fontSize: 10, color: Color(0xFF999CA7)),
        ),
      ],
    ),
  );
}

class _WeatherCard extends StatelessWidget {
  const _WeatherCard();

  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.fromLTRB(12, 16, 12, 13),
    decoration: _cardDecoration(),
    child: Column(
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(4, 0, 4, 10),
          child: Wrap(
            spacing: 8,
            runSpacing: 8,
            crossAxisAlignment: WrapCrossAlignment.center,
            children: [
              const Text(
                '이번 주 날씨',
                style: TextStyle(fontSize: 13, fontWeight: FontWeight.w800),
              ),
              const Text(
                '서울특별시',
                style: TextStyle(fontSize: 9, color: Color(0xFFA0A3AD)),
              ),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 5),
                decoration: BoxDecoration(
                  color: const Color(0xFFEDF1FF),
                  borderRadius: BorderRadius.circular(9),
                ),
                child: const Text(
                  '목요일 세차는 피해주세요',
                  style: TextStyle(fontSize: 9, color: Color(0xFF3760D6)),
                ),
              ),
            ],
          ),
        ),
        Row(
          children: [
            for (final (index, day) in const [
              ('오늘', Icons.wb_sunny_rounded, '18°'),
              ('화', Icons.wb_sunny_rounded, '20°'),
              ('수', Icons.cloud_rounded, '17°'),
              ('목', Icons.umbrella_rounded, '14°'),
              ('금', Icons.cloud_rounded, '16°'),
              ('토', Icons.wb_sunny_rounded, '21°'),
              ('일', Icons.wb_sunny_rounded, '22°'),
            ].indexed)
              Expanded(
                child: Container(
                  margin: const EdgeInsets.symmetric(horizontal: 1.5),
                  padding: const EdgeInsets.symmetric(vertical: 7),
                  decoration: BoxDecoration(
                    color: index == 0
                        ? const Color(0xFFF1F3FB)
                        : Colors.transparent,
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: Column(
                    children: [
                      Text(
                        day.$1,
                        style: TextStyle(
                          fontSize: 9,
                          color: index == 0 ? _blue : const Color(0xFF9396A2),
                        ),
                      ),
                      const SizedBox(height: 6),
                      Icon(
                        day.$2,
                        size: 20,
                        color: day.$2 == Icons.cloud_rounded
                            ? const Color(0xFFAEB7C8)
                            : day.$2 == Icons.umbrella_rounded
                            ? const Color(0xFF5E85DC)
                            : const Color(0xFFFFAD28),
                      ),
                      const SizedBox(height: 6),
                      Text(
                        day.$3,
                        style: const TextStyle(
                          fontSize: 11,
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                    ],
                  ),
                ),
              ),
          ],
        ),
      ],
    ),
  );
}

class _NavItem extends StatelessWidget {
  const _NavItem({
    required this.label,
    required this.icon,
    required this.onTap,
    this.selected = false,
  });
  final String label;
  final IconData icon;
  final VoidCallback onTap;
  final bool selected;

  @override
  Widget build(BuildContext context) => Expanded(
    child: InkWell(
      onTap: onTap,
      child: Semantics(
        selected: selected,
        button: true,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Container(
              width: 22,
              height: 3,
              decoration: BoxDecoration(
                color: selected ? _blue : Colors.transparent,
                borderRadius: const BorderRadius.vertical(
                  bottom: Radius.circular(5),
                ),
              ),
            ),
            Padding(
              padding: const EdgeInsets.fromLTRB(0, 12, 0, 12),
              child: Column(
                children: [
                  Icon(
                    icon,
                    size: 22,
                    color: selected ? _blue : const Color(0xFFA4A7B1),
                  ),
                  const SizedBox(height: 3),
                  Text(
                    label,
                    style: TextStyle(
                      fontSize: 9,
                      fontWeight: FontWeight.w600,
                      color: selected ? _blue : const Color(0xFFA4A7B1),
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    ),
  );
}
