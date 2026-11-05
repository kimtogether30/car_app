import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:car_app1/presentation/data/care_history.dart';
import 'package:car_app1/presentation/caron_shell.dart';
import 'package:car_app1/presentation/screens/caron_calendar_screen.dart';

void main() {
  setUp(() => SharedPreferences.setMockInitialValues({}));

  Future<CareHistory> load() async {
    final history = CareHistory(await SharedPreferences.getInstance());
    await history.load();
    return history;
  }

  test(
    'Imports exact setup dates once and preserves personal settings',
    () async {
      final prefs = await SharedPreferences.getInstance();
      final today = careDay(DateTime.now());
      final personal = {
        'nickname': '내 차',
        'fuelAlert': 25,
        'washAlert': 35,
        'lastWash': today.subtract(const Duration(days: 3)).toIso8601String(),
        'lastFuel': today.subtract(const Duration(days: 2)).toIso8601String(),
        'lastOil': today.subtract(const Duration(days: 1)).toIso8601String(),
        'oilEstimate': '1~3개월 전',
      };
      await prefs.setString('personal_setup_v1', jsonEncode(personal));
      final history = await load();
      expect(history.records.map((r) => r.kind), [
        CareKind.oil,
        CareKind.fuel,
        CareKind.wash,
      ]);
      expect(history.records.every((r) => r.detail.isEmpty), isTrue);
      expect(jsonDecode(prefs.getString('personal_setup_v1')!), personal);
      await history.put(
        CareRecord(
          id: 'setup-wash',
          date: today,
          kind: CareKind.wash,
          title: '직접 세차',
        ),
      );
      final restarted = await load();
      expect(restarted.records, hasLength(3));
      expect(restarted.latest(CareKind.wash), today);
    },
  );

  test('Unknown and estimated dates never create records', () async {
    final history = CareHistory(await SharedPreferences.getInstance());
    await history.load(
      initialPersonal: {
        'lastWash': null,
        'lastFuel': '',
        'oilEstimate': '1~3개월 전',
      },
    );
    expect(history.records, isEmpty);
  });

  test(
    'Records, corrections and latest dates survive restart without duplicates',
    () async {
      final history = await load();
      final today = careDay(DateTime.now());
      for (final kind in CareKind.values) {
        await history.put(
          CareRecord(
            id: kind.name,
            date: today.subtract(Duration(days: kind.index + 1)),
            kind: kind,
            title: kind.label,
            detail: kind == CareKind.fuel ? '사용자 입력 32L' : '',
          ),
        );
      }
      await history.setLatestDates({CareKind.wash: today});
      final restarted = await load();
      expect(restarted.records, hasLength(3));
      expect(restarted.latest(CareKind.wash), today);
      expect(restarted.records.first.kind, CareKind.wash);
      expect(
        restarted.records.singleWhere((r) => r.kind == CareKind.fuel).detail,
        '사용자 입력 32L',
      );
    },
  );

  test(
    'Future creation and correction are rejected including after restart',
    () async {
      final history = await load();
      final tomorrow = careDay(DateTime.now()).add(const Duration(days: 1));
      await expectLater(
        history.put(
          CareRecord(
            id: 'future',
            date: tomorrow,
            kind: CareKind.fuel,
            title: '주유',
          ),
        ),
        throwsArgumentError,
      );
      await expectLater(
        history.setLatestDates({CareKind.oil: tomorrow}),
        throwsArgumentError,
      );
      expect((await load()).records, isEmpty);
    },
  );

  test(
    'Corrupt persisted records report an error without overwriting data',
    () async {
      final prefs = await SharedPreferences.getInstance();
      await prefs.setString(CareHistory.storageKey, 'invalid');
      await expectLater(load(), throwsFormatException);
      expect(prefs.getString(CareHistory.storageKey), 'invalid');
    },
  );

  testWidgets(
    'Calendar opens this month, dates agree in all three record details',
    (tester) async {
      final today = careDay(DateTime.now());
      final history = await load();
      for (final kind in CareKind.values) {
        await history.put(
          CareRecord(
            id: kind.name,
            date: today.subtract(Duration(days: kind.index)),
            kind: kind,
            title: kind.label,
          ),
        );
      }
      await tester.pumpWidget(
        MaterialApp(
          home: CaronCalendarScreen(
            navigationBar: const SizedBox.shrink(),
            records: history.records,
            onSave: history.put,
          ),
        ),
      );
      expect(find.text('${today.year}년 ${today.month}월'), findsOneWidget);
      expect(find.text('워시존 성수점 · 18,000원'), findsNothing);
      for (final kind in CareKind.values) {
        final record = history.records.singleWhere((r) => r.kind == kind);
        final tile = find.byKey(ValueKey('record-${kind.name}'));
        await tester.scrollUntilVisible(tile, 150);
        await tester.pumpAndSettle();
        await tester.ensureVisible(tile);
        await tester.pumpAndSettle();
        expect(
          find.descendant(
            of: tile,
            matching: find.text(careDateLabel(record.date)),
          ),
          findsOneWidget,
        );
        await tester.tap(tile);
        await tester.pumpAndSettle();
        expect(find.text(careDateLabel(record.date)), findsWidgets);
        expect(find.text('기록 수정'), findsOneWidget);
        await tester.tap(find.byTooltip('닫기'));
        await tester.pumpAndSettle();
      }
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets('Future calendar selection cannot open a new record form', (
    tester,
  ) async {
    final now = DateTime.now();
    final nextMonth = DateTime(now.year, now.month + 1);
    await tester.pumpWidget(
      MaterialApp(
        home: CaronCalendarScreen(
          navigationBar: const SizedBox.shrink(),
          records: const [],
          onSave: (_) async {},
        ),
      ),
    );
    await tester.tap(find.byTooltip('다음 달'));
    await tester.pumpAndSettle();
    await tester.tap(
      find.byKey(ValueKey('day-${nextMonth.year}-${nextMonth.month}-1')),
    );
    await tester.tap(find.byTooltip('관리 기록 추가'));
    await tester.pump();
    expect(find.text('미래 날짜에는 관리 기록을 등록할 수 없어요.'), findsOneWidget);
    expect(find.byType(CareRecordForm), findsNothing);
  });

  testWidgets('Home reloads actual dates from history on restart', (
    tester,
  ) async {
    final history = await load();
    final today = careDay(DateTime.now());
    await history.put(
      CareRecord(id: 'wash', date: today, kind: CareKind.wash, title: '세차'),
    );
    await tester.pumpWidget(const MaterialApp(home: CaronShell()));
    await tester.pumpAndSettle();
    expect(
      find.text('마지막 세차일: ${today.year}.${today.month}.${today.day}'),
      findsOneWidget,
    );
    await tester.pumpWidget(const SizedBox.shrink());
    await tester.pumpWidget(const MaterialApp(home: CaronShell()));
    await tester.pumpAndSettle();
    expect(
      find.text('마지막 세차일: ${today.year}.${today.month}.${today.day}'),
      findsOneWidget,
    );
    expect(tester.takeException(), isNull);
  });

  testWidgets('Calendar save updates home dates immediately for every kind', (
    tester,
  ) async {
    final today = careDay(DateTime.now());
    await tester.pumpWidget(const MaterialApp(home: CaronShell()));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('tab-1')));
    await tester.pumpAndSettle();
    for (final kind in CareKind.values) {
      final calendar = tester.widget<CaronCalendarScreen>(
        find.byType(CaronCalendarScreen),
      );
      await calendar.onSave(
        CareRecord(id: kind.name, date: today, kind: kind, title: kind.label),
      );
      await tester.pumpAndSettle();
    }
    final day = find.byKey(
      ValueKey('day-${today.year}-${today.month}-${today.day}'),
    );
    expect(find.descendant(of: day, matching: find.text('3건')), findsOneWidget);
    await tester.tap(find.byKey(const ValueKey('tab-0')));
    await tester.pumpAndSettle();
    for (final (key, title) in [
      ('wash', '마지막 세차일'),
      ('fuel', '마지막 주유일'),
      ('oil', '마지막 엔진오일 교체일'),
    ]) {
      expect(
        tester.widget<Text>(find.byKey(ValueKey('home-last-$key'))).data,
        '$title: ${today.year}.${today.month}.${today.day}',
      );
    }
    expect(tester.takeException(), isNull);
  });
}
