import 'dart:async';

import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'data/care_history.dart';

import '../shared/models/vehicle_status.dart';
import '../shared/services/vehicle_service.dart';
import 'logic/vemoring_condition.dart';
import 'screens/vemoring_calendar_screen.dart';
import 'screens/vemoring_home_screen.dart';
import 'screens/vemoring_profile_screen.dart';
import 'widgets/vemoring_navigation_bar.dart';
import 'widgets/vemoring_page.dart';
import 'widgets/vemoring_vehicle_form.dart';

/// UI 예시 상태의 소유자. 실제 API·친구의 기능과 독립적으로 동작합니다.
class VemoringShell extends StatefulWidget {
  const VemoringShell({
    super.key,
    this.initialModel = '현대 캐스퍼',
    this.initialPersonal,
  });
  final String initialModel;
  final Map<String, dynamic>? initialPersonal;
  @override
  State<VemoringShell> createState() => _VemoringShellState();
}

class _VemoringShellState extends State<VemoringShell> {
  int _tab = 0;
  final _visitedTabs = <int>{0};
  final _vehicleService = VehicleService();
  StreamSubscription<int>? _batterySubscription;

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
  CareHistory? _history;
  bool _loadingHistory = true;
  String? _historyError;
  DateTime? get _lastWash => _history?.latest(CareKind.wash);
  DateTime? get _lastOil => _history?.latest(CareKind.oil);
  DateTime? get _lastFuel => _history?.latest(CareKind.fuel);

  Future<void> _loadHistory() async {
    try {
      final history = CareHistory(await SharedPreferences.getInstance());
      await history.load(initialPersonal: widget.initialPersonal);
      if (mounted) {
        setState(() {
          _history = history;
          _loadingHistory = false;
          _historyError = null;
        });
      }
    } catch (_) {
      if (mounted) {
        setState(() {
          _loadingHistory = false;
          _historyError = '관리 기록을 불러오지 못했어요. 기존 저장 데이터는 보존됩니다.';
        });
      }
    }
  }

  Future<void> _saveRecord(CareRecord record) async {
    await _history!.put(record);
    if (mounted) setState(() {});
  }

  late double _fuelThreshold =
      (widget.initialPersonal?['fuelAlert'] as num? ?? 20).toDouble();
  late double _washThreshold =
      (widget.initialPersonal?['washAlert'] as num? ?? 30).toDouble();

  @override
  void initState() {
    super.initState();
    _loadHistory();
    _connectPleosVehicle();
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
  }

  Future<void> _connectPleosVehicle() async {
    final initialize = await _vehicleService.initialize();
    if (!mounted) return;
    if (initialize['status'] != 'ready') {
      setState(() {
        _vehicle = VehicleStatus(
          vehicleName: _vehicle.vehicleName,
          licensePlate: _vehicle.licensePlate,
          vehicleType: _vehicle.vehicleType,
          fuelLevelPercent: _vehicle.fuelLevelPercent,
          batteryLevelPercent: null,
          cleanlinessPercent: _vehicle.cleanlinessPercent,
          characterMessage: _vehicle.characterMessage,
          batteryDataSource: VehicleDataSource.unavailable,
        );
      });
      return;
    }

    _vehicleService.startBatteryUpdates();
    _batterySubscription?.cancel();
    _batterySubscription = _vehicleService.batteryUpdates.listen((value) {
      if (!mounted) return;
      setState(() {
        _vehicle = VehicleStatus(
          vehicleName: _vehicle.vehicleName,
          licensePlate: _vehicle.licensePlate,
          vehicleType: _vehicle.vehicleType,
          fuelLevelPercent: _vehicle.fuelLevelPercent,
          batteryLevelPercent: value.toDouble(),
          cleanlinessPercent: _vehicle.cleanlinessPercent,
          characterMessage: _vehicle.characterMessage,
          batteryDataSource: VehicleDataSource.pleos,
        );
      });
    });

    final battery = await _vehicleService.getBattery();
    if (!mounted) return;
    final status = battery['status'] as String?;
    final value = battery['value'];
    setState(() {
      _vehicle = VehicleStatus(
        vehicleName: _vehicle.vehicleName,
        licensePlate: _vehicle.licensePlate,
        vehicleType: _vehicle.vehicleType,
        fuelLevelPercent: _vehicle.fuelLevelPercent,
        batteryLevelPercent: status == 'ok' && value is num
            ? value.toDouble()
            : null,
        cleanlinessPercent: _vehicle.cleanlinessPercent,
        characterMessage: _vehicle.characterMessage,
        batteryDataSource: status == 'ok'
            ? VehicleDataSource.pleos
            : status == 'unsupported'
            ? VehicleDataSource.unsupported
            : VehicleDataSource.unavailable,
      );
    });
  }

  @override
  void dispose() {
    _batterySubscription?.cancel();
    _vehicleService.release();
    super.dispose();
  }

  Future<void> _edit(bool nickname) async {
    final result = await vemoringSheet<String>(
      context,
      VemoringTextEdit(
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
          batteryDataSource: old.batteryDataSource,
        );
      } else {
        _model = result;
      }
    });
  }

  Future<void> _register() async {
    final result = await vemoringSheet<VemoringVehicleEdit>(
      context,
      VemoringVehicleForm(
        vehicle: _vehicle,
        model: _model,
        lastWash: _lastWash,
        lastOil: _lastOil,
        lastFuel: _lastFuel,
      ),
    );
    if (result == null || !mounted) return;
    try {
      await _history!.setLatestDates({
        CareKind.wash: result.lastWash,
        CareKind.oil: result.lastOil,
        CareKind.fuel: result.lastFuel,
      });
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('관리 날짜를 저장하지 못했어요. 다시 시도해 주세요.')),
        );
      }
      return;
    }
    if (!mounted) return;
    setState(() {
      _vehicle = result.vehicle;
      _model = result.model;
    });
  }

  void _notifications() => vemoringSheet<void>(
    context,
    SingleChildScrollView(
      child: Padding(
        padding: const EdgeInsets.all(22),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const VemoringSheetHeader(title: '알림 내역'),
            const VemoringSampleNotice(),
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
                leading: Icon(item.$1, color: vemoringBlue),
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
    if (_loadingHistory) {
      return const Scaffold(body: Center(child: CircularProgressIndicator()));
    }
    if (_historyError != null) {
      return Scaffold(
        body: Center(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(_historyError!),
              TextButton(
                onPressed: () {
                  setState(() => _loadingHistory = true);
                  _loadHistory();
                },
                child: const Text('다시 시도'),
              ),
            ],
          ),
        ),
      );
    }
    final condition = vemoringCondition(
      _vehicle,
      fuelThreshold: _fuelThreshold,
      washThreshold: _washThreshold,
    );
    final displayedVehicle = VehicleStatus(
      vehicleName: _vehicle.vehicleName,
      licensePlate: _vehicle.licensePlate,
      vehicleType: _vehicle.vehicleType,
      fuelLevelPercent: _vehicle.fuelLevelPercent,
      batteryLevelPercent: _vehicle.batteryLevelPercent,
      cleanlinessPercent: _vehicle.cleanlinessPercent,
      characterMessage: condition.message,
      batteryDataSource: _vehicle.batteryDataSource,
    );
    final nav = VemoringNavigationBar(
      selectedIndex: _tab,
      onSelected: _selectTab,
    );
    return IndexedStack(
      index: _tab,
      children: [
        VemoringHomeScreen(
          vehicle: displayedVehicle,
          rangeLabel: condition.exampleRangeKm == null
              ? '정보 없음'
              : '${condition.exampleRangeKm} km (예시)',
          vehicleModel: _model,
          dateLabel: careDateLabel(DateTime.now()),
          nextCareLabel: '저장된 차량 관리 기록을 확인해 보세요',
          navigationBar: nav,
          lastWashLabel: _lastWash == null
              ? '기록 없음'
              : '${_lastWash!.year}.${_lastWash!.month}.${_lastWash!.day}',
          lastFuelLabel: _lastFuel == null
              ? '기록 없음'
              : '${_lastFuel!.year}.${_lastFuel!.month}.${_lastFuel!.day}',
          lastOilLabel: _lastOil == null
              ? '기록 없음'
              : '${_lastOil!.year}.${_lastOil!.month}.${_lastOil!.day}',
          onCalendar: () => _selectTab(1),
          onProfile: () => _selectTab(2),
          onEditNickname: () => _edit(true),
          onNotifications: _notifications,
        ),
        if (_visitedTabs.contains(1))
          VemoringCalendarScreen(
            navigationBar: nav,
            records: _history!.records,
            onSave: _saveRecord,
          )
        else
          const SizedBox.shrink(),
        if (_visitedTabs.contains(2))
          VemoringProfileScreen(
            vehicle: _vehicle,
            vehicleModel: _model,
            navigationBar: nav,
            onEditNickname: () => _edit(true),
            onEditModel: () => _edit(false),
            onRegister: _register,
            initialPersonal: widget.initialPersonal,
            onAlertsChanged: (fuel, wash) => setState(() {
              _fuelThreshold = fuel;
              _washThreshold = wash;
            }),
          )
        else
          const SizedBox.shrink(),
      ],
    );
  }
}
