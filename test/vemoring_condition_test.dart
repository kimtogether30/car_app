import 'package:flutter_test/flutter_test.dart';
import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:vemoring/presentation/vemoring_shell.dart';
import 'package:vemoring/presentation/screens/vemoring_profile_screen.dart';
import 'package:vemoring/presentation/logic/vemoring_condition.dart';
import 'package:vemoring/shared/models/vehicle_status.dart';

void main() {
  setUp(() => SharedPreferences.setMockInitialValues({}));
  testWidgets(
    'Home displays requested condition and sliders publish thresholds',
    (tester) async {
      await tester.pumpWidget(const MaterialApp(home: VemoringShell()));
      await tester.pumpAndSettle();
      expect(
        find.text('컨디션 최고! 지금처럼 쭉 달려보자! 앞으로 420km 더 달릴 수 있어.'),
        findsOneWidget,
      );
      expect(tester.takeException(), isNull);
      double? fuel, wash;
      await tester.pumpWidget(
        MaterialApp(
          home: VemoringProfileScreen(
            vehicle: const VehicleStatus(
              vehicleName: '테스트',
              licensePlate: '',
              vehicleType: VehicleType.combustion,
            ),
            vehicleModel: '테스트',
            navigationBar: const SizedBox.shrink(),
            onEditNickname: () {},
            onEditModel: () {},
            onRegister: () {},
            onAlertsChanged: (f, w) {
              fuel = f;
              wash = w;
            },
          ),
        ),
      );
      final washSlider = find.byKey(const ValueKey('세차 알림 기준'));
      await tester.scrollUntilVisible(washSlider, 150);
      await tester.pumpAndSettle();
      await tester.ensureVisible(washSlider);
      await tester.pumpAndSettle();
      await tester.drag(washSlider, const Offset(60, 0));
      await tester.pumpAndSettle();
      expect(fuel, 20);
      expect(wash, isNot(30));
      expect(tester.takeException(), isNull);
    },
  );
  VehicleStatus vehicle(double? fuel, double? clean) => VehicleStatus(
    vehicleName: '테스트',
    licensePlate: '',
    vehicleType: VehicleType.combustion,
    fuelLevelPercent: fuel,
    cleanlinessPercent: clean,
  );

  test(
    'Four condition messages use personal thresholds and requested ranges',
    () {
      const cases = [
        (68.0, 82.0, '컨디션 최고! 지금처럼 쭉 달려보자! 앞으로 420km 더 달릴 수 있어.', 420),
        (15.0, 82.0, '슬슬 배가 고파지네! 85km 더 달릴 수 있지만, 주유가 필요해!', 85),
        (15.0, 20.0, '배도 고프고 씻고도 싶어! 50km 더 달릴 수 있어. 주유랑 세차 둘 다 부탁해!', 50),
        (68.0, 20.0, '달릴 준비는 됐는데, 몸이 좀 찝찝해! 300km 더 달릴 수 있지만 세차 한번 부탁해!', 300),
      ];
      for (final (fuel, clean, message, range) in cases) {
        final result = vemoringCondition(
          vehicle(fuel, clean),
          fuelThreshold: 20,
          washThreshold: 30,
        );
        expect(result.message, message);
        expect(result.exampleRangeKm, range);
      }
    },
  );

  test(
    'Equality is low and custom thresholds change the same vehicle message',
    () {
      final status = vehicle(20, 30);
      expect(
        vemoringCondition(
          status,
          fuelThreshold: 20,
          washThreshold: 30,
        ).exampleRangeKm,
        50,
      );
      expect(
        vemoringCondition(
          status,
          fuelThreshold: 15,
          washThreshold: 25,
        ).exampleRangeKm,
        420,
      );
      expect(
        vemoringCondition(
          status,
          fuelThreshold: 20,
          washThreshold: 25,
        ).exampleRangeKm,
        85,
      );
      expect(
        vemoringCondition(
          status,
          fuelThreshold: 15,
          washThreshold: 30,
        ).exampleRangeKm,
        300,
      );
    },
  );

  test('Missing readings do not invent condition or mileage', () {
    for (final status in [
      vehicle(null, 82),
      vehicle(68, null),
      vehicle(null, null),
    ]) {
      final result = vemoringCondition(
        status,
        fuelThreshold: 20,
        washThreshold: 30,
      );
      expect(result.exampleRangeKm, isNull);
      expect(result.message, '연료와 청결도 정보를 확인하고 있어요.');
    }
  });
}
