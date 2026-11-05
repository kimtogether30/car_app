import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:car_app1/main.dart';
import 'package:shared_preferences/shared_preferences.dart';

Future<void> tapTab(WidgetTester tester, int index) async {
  await tester.pumpAndSettle();
  await tester.tap(find.byKey(ValueKey('tab-$index')));
  await tester.pumpAndSettle();
}

Future<void> tapVisible(WidgetTester tester, Finder finder) async {
  await tester.ensureVisible(finder);
  await tester.pumpAndSettle();
  await tester.tap(finder);
  await tester.pumpAndSettle();
}

void main() {
  setUp(() {
    SharedPreferences.setMockInitialValues({
      'vehicle_brand': '현대',
      'vehicle_model': '아반떼',
      'vehicle_variant': '아반떼',
      'personal_setup_v1': '{"fuel":"휘발유","nickname":"몽글이","oilEstimate":"기억이 안 나요","washAlert":30,"fuelAlert":20}',
    });
  });
  testWidgets('Tabs switch and vehicle edits propagate to home', (
    tester,
  ) async {
    await tester.pumpWidget(const MyApp());
    await tapTab(tester, 1);
    expect(find.text('MY CAR LOG'), findsOneWidget);
    await tester.tap(find.byTooltip('다음 달'));
    await tester.pumpAndSettle();
    expect(find.text('2025년 4월'), findsOneWidget);
    await tapTab(tester, 2);
    expect(find.text('내 차 설정'), findsOneWidget);
    await tapVisible(tester, find.text('닉네임'));
    await tester.enterText(find.byType(TextFormField), '구름이');
    await tester.tap(find.text('저장하기'));
    await tester.pumpAndSettle();
    expect(find.text('구름이'), findsWidgets);
    await tapTab(tester, 0);
    expect(find.text('오늘도 구름이와\n기분 좋은 드라이브!'), findsOneWidget);
    await tapTab(tester, 1);
    expect(find.text('2025년 4월'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('Calendar filters days and adds a validated session record', (
    tester,
  ) async {
    await tester.pumpWidget(const MyApp());
    await tapTab(tester, 1);
    await tester.tap(find.byKey(const ValueKey('day-2025-3-19')));
    await tester.pumpAndSettle();
    await tester.scrollUntilVisible(
      find.text('3월 19일 관리'),
      150,
      scrollable: find.byType(Scrollable).first,
    );
    expect(find.text('3월 19일 관리'), findsOneWidget);
    await tester.drag(find.byType(ListView), const Offset(0, 600));
    await tester.pumpAndSettle();
    await tester.tap(find.byTooltip('관리 기록 추가'));
    await tester.pumpAndSettle();
    await tapVisible(tester, find.text('기록 저장'));
    expect(find.text('기록 이름을 입력해 주세요.'), findsOneWidget);
    await tester.enterText(
      find.widgetWithText(TextFormField, '기록 이름'),
      '테스트 세차',
    );
    await tester.enterText(
      find.widgetWithText(TextFormField, '상세 내용 (장소·금액 등)'),
      '직접 입력한 예시',
    );
    await tapVisible(tester, find.text('기록 저장'));
    await tester.scrollUntilVisible(
      find.text('테스트 세차'),
      150,
      scrollable: find.byType(Scrollable).first,
    );
    await tapVisible(tester, find.text('테스트 세차'));
    expect(find.text('직접 입력한 예시'), findsWidgets);
    await tester.tap(find.byTooltip('닫기'));
    await tester.pumpAndSettle();
    await tapTab(tester, 0);
    await tapTab(tester, 1);
    expect(find.text('테스트 세차'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets(
    'Registration changes vehicle type and cancel keeps the saved name',
    (tester) async {
      await tester.pumpWidget(const MyApp());
      await tapTab(tester, 2);
      await tapVisible(tester, find.text('차량 정보 다시 등록'));
      await tapVisible(tester, find.text('다음'));
      await tapVisible(tester, find.text('내연기관'));
      // The vehicle-type menu is the first dropdown on this form.
      await tester.tap(find.text('전기').last);
      await tester.pumpAndSettle();
      await tester.enterText(
        find.widgetWithText(TextFormField, '차량 닉네임'),
        '전기 몽글이',
      );
      await tapVisible(tester, find.text('CAR:ON 시작하기'));
      await tapTab(tester, 0);
      expect(find.text('남은 배터리'), findsOneWidget);
      expect(find.text('남은 연료'), findsNothing);
      await tester.tap(find.byTooltip('닉네임 수정'));
      await tester.pumpAndSettle();
      await tester.enterText(find.byType(TextFormField), '취소할 이름');
      await tester.tap(find.byTooltip('닫기'));
      await tester.pumpAndSettle();
      expect(find.text('전기 몽글이'), findsOneWidget);
      expect(find.text('취소할 이름'), findsNothing);
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets(
    'Settings persist across tabs and narrow layouts do not overflow',
    (tester) async {
      tester.view.physicalSize = const Size(320, 740);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      await tester.pumpWidget(const MyApp());
      await tapTab(tester, 2);
      await tapVisible(tester, find.byType(Switch));
      expect(tester.widget<Switch>(find.byType(Switch)).value, isFalse);
      await tester.ensureVisible(find.byType(Slider).first);
      await tester.drag(find.byType(Slider).first, const Offset(40, 0));
      await tester.pumpAndSettle();
      final cycle = tester.widget<Slider>(find.byType(Slider).first).value;
      await tapTab(tester, 0);
      await tapTab(tester, 2);
      expect(tester.widget<Switch>(find.byType(Switch)).value, isFalse);
      expect(tester.widget<Slider>(find.byType(Slider).first).value, cycle);
      await tapTab(tester, 1);
      expect(tester.takeException(), isNull);
    },
  );
}
