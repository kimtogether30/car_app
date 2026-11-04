import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:car_app1/presentation/screens/caron_home_screen.dart';
import 'package:car_app1/shared/models/vehicle_status.dart';

void main() {
  testWidgets('Home fits a narrow phone and opens the calendar callback', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(320, 740);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    var opened = false;
    await tester.pumpWidget(
      MaterialApp(home: CaronHomeScreen(onCalendar: () => opened = true)),
    );
    expect(find.text('CAR:ON'), findsOneWidget);
    expect(find.text('남은 연료'), findsOneWidget);
    expect(find.text('차량 청결도'), findsOneWidget);
    await tester.tap(find.text('관리 기록'));
    await tester.pump();
    expect(opened, isTrue);
    await tester.drag(
      find.byType(SingleChildScrollView),
      const Offset(0, -500),
    );
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
  });

  testWidgets('Electric vehicle shows unknown battery without a fuel card', (
    tester,
  ) async {
    await tester.pumpWidget(
      const MaterialApp(
        home: CaronHomeScreen(
          vehicle: VehicleStatus(
            vehicleName: '전기차',
            licensePlate: '12가 3456',
            vehicleType: VehicleType.electric,
          ),
        ),
      ),
    );
    expect(find.text('남은 연료'), findsNothing);
    expect(find.text('남은 배터리'), findsOneWidget);
    expect(find.text('정보를 기다리고 있어요'), findsNWidgets(2));
    expect(tester.takeException(), isNull);
  });

  testWidgets('Hybrid cards fit with enlarged text', (tester) async {
    tester.view.physicalSize = const Size(320, 740);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    await tester.pumpWidget(
      MaterialApp(
        builder: (context, child) => MediaQuery(
          data: MediaQuery.of(context)
              .copyWith(textScaler: const TextScaler.linear(1.5)),
          child: child!,
        ),
        home: const CaronHomeScreen(
          vehicle: VehicleStatus(
            vehicleName: '몽글이',
            licensePlate: '123가 4567',
            vehicleType: VehicleType.hybrid,
            fuelLevelPercent: 68,
            batteryLevelPercent: 45,
            cleanlinessPercent: 82,
          ),
        ),
      ),
    );
    expect(find.text('남은 연료'), findsOneWidget);
    expect(find.text('남은 배터리'), findsOneWidget);
    await tester.drag(
      find.byType(SingleChildScrollView),
      const Offset(0, -500),
    );
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
  });
}
