// This is a basic Flutter widget test.
//
// To perform an interaction with a widget in your test, use the WidgetTester
// utility in the flutter_test package. For example, you can send tap and scroll
// gestures. You can also use WidgetTester to find child widgets in the widget
// tree, read text, and verify that the values of widget properties are correct.

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:car_app1/main.dart';

void main() {
  testWidgets('App starts on the CAR:ON home screen', (tester) async {
    await tester.pumpWidget(const MyApp());

    expect(find.text('CAR:ON'), findsOneWidget);
    expect(find.text('남은 연료'), findsOneWidget);
    expect(find.text('차량 청결도'), findsOneWidget);
    expect(find.text('Flutter Demo Home Page'), findsNothing);
    expect(tester.takeException(), isNull);
  });

  testWidgets('Counter increments smoke test', (WidgetTester tester) async {
    // Build our app and trigger a frame.
    await tester.pumpWidget(
      const MaterialApp(home: MyHomePage(title: 'Flutter Demo Home Page')),
    );

    // Verify that our counter starts at 0.
    expect(find.text('0'), findsOneWidget);
    expect(find.text('1'), findsNothing);

    // Tap the '+' icon and trigger a frame.
    await tester.tap(find.byIcon(Icons.add));
    await tester.pump();

    // Verify that our counter has incremented.
    expect(find.text('0'), findsNothing);
    expect(find.text('1'), findsOneWidget);
  });
}
