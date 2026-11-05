import 'package:flutter/material.dart';

import '../shared/models/vehicle_status.dart';
import 'screens/caron_calendar_screen.dart';
import 'screens/caron_home_screen.dart';
import 'screens/caron_profile_screen.dart';
import 'widgets/caron_navigation_bar.dart';
import 'widgets/caron_page.dart';
import 'widgets/caron_vehicle_form.dart';

/// UI 예시 상태의 소유자. 실제 API·친구의 기능과 독립적으로 동작합니다.
class CaronShell extends StatefulWidget {
  const CaronShell({
    super.key,
    this.initialModel = '현대 캐스퍼',
    this.initialPersonal,
  });
  final String initialModel;
  final Map<String, dynamic>? initialPersonal;
  @override
  State<CaronShell> createState() => _CaronShellState();
}

class _CaronShellState extends State<CaronShell> {
  int _tab = 0;
  final _visitedTabs = <int>{0};

  void _selectTab(int index) {
    if (_tab == index) return;
    setState(() {
      _tab = index;
      _visitedTabs.add(index);
    });
  }

  VehicleStatus _vehicle = const VehicleStatus(
    vehicleName: '몽글이',
    licensePlate: '123가 4567',
    vehicleType: VehicleType.combustion,
    fuelLevelPercent: 68,
    cleanlinessPercent: 82,
    characterMessage: '컨디션 최고!',
  );
  late String _model = widget.initialModel;
  DateTime? _lastWash, _lastOil;

  @override
  void initState() {
    super.initState();
    final data = widget.initialPersonal;
    if (data == null) return;
    final fuel = data['fuel'];
    _vehicle = VehicleStatus(
      vehicleName: data['nickname'] as String,
      licensePlate: '',
      vehicleType: fuel == '전기차'
          ? VehicleType.electric
          : fuel == '하이브리드'
          ? VehicleType.hybrid
          : VehicleType.combustion,
      characterMessage: '오늘도 안전 운전하세요!',
    );
    _lastWash = DateTime.tryParse(data['lastWash'] as String? ?? '');
  }

  Future<void> _edit(bool nickname) async {
    final result = await caronSheet<String>(
      context,
      CaronTextEdit(
        title: nickname ? '닉네임' : '차종',
        value: nickname ? _vehicle.vehicleName : _model,
      ),
    );
    if (result == null || !mounted) return;
    setState(() {
      if (nickname) {
        final old = _vehicle;
        _vehicle = VehicleStatus(
          vehicleName: result,
          licensePlate: old.licensePlate,
          vehicleType: old.vehicleType,
          fuelLevelPercent: old.fuelLevelPercent,
          batteryLevelPercent: old.batteryLevelPercent,
          cleanlinessPercent: old.cleanlinessPercent,
          characterMessage: old.characterMessage,
        );
      } else {
        _model = result;
      }
    });
  }

  Future<void> _register() async {
    final result = await caronSheet<CaronVehicleEdit>(
      context,
      CaronVehicleForm(
        vehicle: _vehicle,
        model: _model,
        lastWash: _lastWash,
        lastOil: _lastOil,
      ),
    );
    if (result == null || !mounted) return;
    setState(() {
      _vehicle = result.vehicle;
      _model = result.model;
      _lastWash = result.lastWash;
      _lastOil = result.lastOil;
    });
  }

  void _notifications() => caronSheet<void>(
    context,
    SingleChildScrollView(
      child: Padding(
        padding: const EdgeInsets.all(22),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const CaronSheetHeader(title: '알림 내역'),
            const CaronSampleNotice(),
            for (final item in const [
              (
                Icons.local_gas_station_outlined,
                '연료를 확인해주세요',
                '예시 · 연료가 20% 아래로 내려가면 알려드릴게요.',
              ),
              (
                Icons.oil_barrel_outlined,
                '엔진오일 교체가 다가와요',
                '예시 · 교체 예정일까지 12일 남았습니다.',
              ),
              (
                Icons.local_car_wash_outlined,
                '세차하기 좋은 날이에요',
                '예시 · 오늘은 맑고 미세먼지가 적어요.',
              ),
            ])
              ListTile(
                leading: Icon(item.$1, color: caronBlue),
                title: Text(
                  item.$2,
                  style: const TextStyle(
                    fontSize: 12,
                    fontWeight: FontWeight.w700,
                  ),
                ),
                subtitle: Text(item.$3, style: const TextStyle(fontSize: 10)),
              ),
            FilledButton(
              onPressed: () => Navigator.pop(context),
              child: const Text('모두 확인했어요'),
            ),
          ],
        ),
      ),
    ),
  );

  @override
  Widget build(BuildContext context) {
    final nav = CaronNavigationBar(selectedIndex: _tab, onSelected: _selectTab);
    return IndexedStack(
      index: _tab,
      children: [
        CaronHomeScreen(
          vehicle: _vehicle,
          vehicleModel: _model,
          navigationBar: nav,
          lastWashLabel: _lastWash == null
              ? (widget.initialPersonal == null ? '5일 전' : '기록 없음')
              : '${_lastWash!.year}.${_lastWash!.month}.${_lastWash!.day}',
          onCalendar: () => _selectTab(1),
          onProfile: () => _selectTab(2),
          onEditNickname: () => _edit(true),
          onNotifications: _notifications,
        ),
        if (_visitedTabs.contains(1))
          CaronCalendarScreen(navigationBar: nav)
        else
          const SizedBox.shrink(),
        if (_visitedTabs.contains(2))
          CaronProfileScreen(
            vehicle: _vehicle,
            vehicleModel: _model,
            navigationBar: nav,
            onEditNickname: () => _edit(true),
            onEditModel: () => _edit(false),
            onRegister: _register,
            initialPersonal: widget.initialPersonal,
          )
        else
          const SizedBox.shrink(),
      ],
    );
  }
}
