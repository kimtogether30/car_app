// This is a basic Flutter widget test.
//
// To perform an interaction with a widget in your test, use the WidgetTester
// utility in the flutter_test package. For example, you can send tap and scroll
// gestures. You can also use WidgetTester to find child widgets in the widget
// tree, read text, and verify that the values of widget properties are correct.

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:vemoring/main.dart';
import 'package:vemoring/presentation/screens/vemoring_calendar_screen.dart';
import 'package:vemoring/presentation/screens/vemoring_profile_screen.dart';

void main() {
  testWidgets('App starts on the VEMORING home screen', (tester) async {
    SharedPreferences.setMockInitialValues({
      'vehicle_brand': '현대',
      'vehicle_model': '아반떼',
      'vehicle_variant': '아반떼',
      'personal_setup_v1': '{"fuel":"휘발유","nickname":"몽글이","oilEstimate":"기억이 안 나요","washAlert":30,"fuelAlert":20}',
    });
    await tester.pumpWidget(const MyApp());
    await tester.pumpAndSettle();

    expect(find.text('VEMORING'), findsOneWidget);
    expect(find.text('남은 연료'), findsOneWidget);
    expect(find.text('차량 청결도'), findsOneWidget);
    expect(find.text('Flutter Demo Home Page'), findsNothing);
    expect(find.byType(VemoringCalendarScreen, skipOffstage: false), findsNothing);
    expect(find.byType(VemoringProfileScreen, skipOffstage: false), findsNothing);
    await tester.tap(find.byKey(const ValueKey('tab-1')));
    await tester.pumpAndSettle();
    expect(find.byType(VemoringCalendarScreen), findsOneWidget);
    await tester.tap(find.byTooltip('다음 달'));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('tab-0')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('tab-1')));
    await tester.pumpAndSettle();
    final nextMonth = DateTime(DateTime.now().year, DateTime.now().month + 1);
    expect(find.text('${nextMonth.year}년 ${nextMonth.month}월'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('First launch requires a brand and saves the selection', (
    tester,
  ) async {
    SharedPreferences.setMockInitialValues({});
    await tester.pumpWidget(const MyApp());
    await tester.pumpAndSettle();

    expect(find.text('현대'), findsOneWidget);
    expect(find.text('기아'), findsOneWidget);
    expect(find.text('제네시스'), findsOneWidget);
    expect(find.byType(FilledButton), findsNothing);

    await tester.tap(find.text('기아'));
    await tester.pump();
    await tester.ensureVisible(find.text('K5'));
    await tester.tap(find.text('K5'));
    await tester.pumpAndSettle();
    expect(find.text('주유 방식을 선택해 주세요'), findsOneWidget);
    expect(find.text('세부 모델'), findsNothing);

    expect(
      (await SharedPreferences.getInstance()).getString('vehicle_brand'),
      '기아',
    );
    expect(
      (await SharedPreferences.getInstance()).getString('vehicle_model'),
      'K5',
    );
    await tester.tap(find.text('하이브리드'));
    await tester.pumpAndSettle();
    await tester.tap(find.byType(CheckboxListTile).at(0));
    await tester.tap(find.byType(CheckboxListTile).at(1));
    await tester.pumpAndSettle();
    await tester.scrollUntilVisible(find.text('1~3개월 전'), 150);
    await tester.pumpAndSettle();
    await tester.ensureVisible(find.text('1~3개월 전'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('1~3개월 전'));
    await tester.pumpAndSettle();
    expect(find.text('차량의 닉네임을 지어 주세요'), findsOneWidget);
    await tester.enterText(find.byType(TextFormField), '구름이');
    await tester.tap(find.text('다음'));
    await tester.pumpAndSettle();
    expect(find.text('기본 알림 기준을 설정해 뒀어요! 🚗'), findsOneWidget);
    expect(find.text('🧽 세차 알림: 청결도 30% 이하'), findsOneWidget);
    await tester.tap(find.text('홈으로 가기'));
    await tester.pumpAndSettle();
    expect(find.text('남은 연료'), findsOneWidget);
    expect(find.text('오늘도 구름이와\n기분 좋은 드라이브!'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });
}
