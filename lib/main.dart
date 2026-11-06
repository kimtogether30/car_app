import 'package:flutter/material.dart';

import 'presentation/screens/brand_setup_screen.dart';
import 'presentation/widgets/caron_page.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) => MaterialApp(
    title: 'CAR:ON',
    debugShowCheckedModeBanner: false,
    theme: ThemeData(
      useMaterial3: true,
      colorScheme: ColorScheme.fromSeed(
        seedColor: caronBlue,
        surface: Colors.white,
      ),
      scaffoldBackgroundColor: const Color(0xFFF6F7F8),
      textTheme: const TextTheme(
        bodyMedium: TextStyle(color: caronInk, fontSize: 14, height: 1.5),
        bodySmall: TextStyle(
          color: Color(0xFF66727D),
          fontSize: 12,
          height: 1.5,
        ),
      ),
      dividerTheme: const DividerThemeData(
        color: Color(0xFFE2E6E9),
        thickness: 1,
      ),
      chipTheme: ChipThemeData(
        showCheckmark: true,
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
        side: const BorderSide(color: Color(0xFFD9DFE4)),
      ),
    ),
    home: const BrandSetupScreen(),
  );
}
