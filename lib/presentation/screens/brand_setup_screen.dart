import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../../shared/models/vehicle_catalog.dart';
import '../caron_shell.dart';
import 'personal_setup_screen.dart';

class BrandSetupScreen extends StatefulWidget {
  const BrandSetupScreen({super.key});
  @override
  State<BrandSetupScreen> createState() => _BrandSetupScreenState();
}

class _BrandSetupScreenState extends State<BrandSetupScreen> {
  static const _blue = Color(0xFF335C81);
  String? _brand, _model;
  bool _loading = true, _complete = false, _saving = false;
  bool _details = false;
  bool _choosingModel = false;
  Map<String, dynamic>? _personal;
  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      if (!mounted) return;
      final brand = prefs.getString('vehicle_brand');
      _brand = vehicleCatalog.containsKey(brand) ? brand : null;
      _model = prefs.getString('vehicle_model');
      final validVehicle = isValidVehicleSelection(_brand, _model);
      final stored = prefs.getString('personal_setup_v1');
      if (stored != null) {
        _personal = Map<String, dynamic>.from(jsonDecode(stored));
      }
      _complete = validVehicle && _personal != null;
      if (!validVehicle) {
        _model = null;
      }
    } catch (_) {
      // 저장 데이터를 읽지 못하면 초기 설정 화면을 표시한다.
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _continue() async {
    if (!isValidVehicleSelection(_brand, _model) || _saving) return;
    setState(() => _saving = true);
    try {
      final prefs = await SharedPreferences.getInstance();
      await prefs.remove('personal_setup_v1');
      if (!await prefs.setString('vehicle_model', _model!) ||
          !await prefs.setString('vehicle_brand', _brand!)) {
        throw StateError('Vehicle could not be saved');
      }
      if (mounted) setState(() => _details = true);
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('설정을 저장하지 못했어요. 다시 시도해 주세요.')),
        );
      }
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  Widget _choice(String label, bool selected, VoidCallback onTap) => ChoiceChip(
    label: Text(label),
    selected: selected,
    onSelected: _saving ? null : (_) => onTap(),
    selectedColor: const Color(0xFFEAF0FF),
    labelStyle: TextStyle(color: selected ? _blue : const Color(0xFF202C35)),
  );
  @override
  Widget build(BuildContext context) {
    if (_loading) {
      return const Scaffold(body: Center(child: CircularProgressIndicator()));
    }
    if (_complete) {
      return CaronShell(
        initialModel: '$_brand $_model',
        initialPersonal: _personal,
      );
    }
    if (_details) {
      return PersonalSetupScreen(
        onBack: () => setState(() => _details = false),
        onComplete: (data) => setState(() {
          _personal = data;
          _complete = true;
        }),
      );
    }
    final categories = vehicleCatalog[_brand];
    return Scaffold(
      backgroundColor: const Color(0xFFE8E9ED),
      body: Center(
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 520),
          child: ColoredBox(
            color: const Color(0xFFF6F7F8),
            child: SafeArea(
              child: ListView(
                padding: const EdgeInsets.all(24),
                children: [
                  const Text(
                    'CAR:ON',
                    style: TextStyle(
                      color: _blue,
                      fontSize: 22,
                      fontWeight: FontWeight.w800,
                    ),
                  ),
                  const SizedBox(height: 32),
                  const Text(
                    '나에게 맞는 차량 관리의 시작',
                    style: TextStyle(color: _blue),
                  ),
                  const SizedBox(height: 12),
                  Text(
                    _choosingModel ? '차종을 선택해 주세요' : '어떤 브랜드의\n차량을 타고 계신가요?',
                    style: TextStyle(
                      fontSize: 28,
                      height: 1.35,
                      fontWeight: FontWeight.w800,
                    ),
                  ),
                  const SizedBox(height: 12),
                  const Text('브랜드와 차종을 차례로 선택해 주세요.'),
                  const SizedBox(height: 24),
                  if (_choosingModel)
                    TextButton(
                      onPressed: _saving
                          ? null
                          : () => setState(() => _choosingModel = false),
                      child: const Text('이전'),
                    ),
                  if (!_choosingModel)
                    Row(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        for (final brand in vehicleCatalog.keys)
                          Expanded(
                            child: Padding(
                              padding: EdgeInsets.only(
                                right: brand == '제네시스' ? 0 : 10,
                              ),
                              child: AspectRatio(
                                aspectRatio: 1,
                                child: Semantics(
                                  selected: _brand == brand,
                                  child: OutlinedButton(
                                    onPressed: _saving
                                        ? null
                                        : () => setState(() {
                                            _choosingModel = true;
                                            _brand = brand;
                                            _model = null;
                                          }),
                                    style: OutlinedButton.styleFrom(
                                      padding: const EdgeInsets.all(4),
                                      foregroundColor: _blue,
                                      backgroundColor: _brand == brand
                                          ? const Color(0xFFEAF0FF)
                                          : Colors.white,
                                      side: BorderSide(
                                        color: _brand == brand
                                            ? _blue
                                            : const Color(0xFFE0E2E8),
                                        width: _brand == brand ? 2 : 1,
                                      ),
                                      shape: RoundedRectangleBorder(
                                        borderRadius: BorderRadius.circular(16),
                                      ),
                                    ),
                                    child: FittedBox(
                                      child: Text(
                                        brand,
                                        style: const TextStyle(
                                          fontSize: 17,
                                          fontWeight: FontWeight.w700,
                                        ),
                                      ),
                                    ),
                                  ),
                                ),
                              ),
                            ),
                          ),
                      ],
                    ),
                  if (_choosingModel && categories != null) ...[
                    const SizedBox(height: 28),
                    Text(
                      '$_brand 차종',
                      style: const TextStyle(
                        fontSize: 20,
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                    for (final category in categories.entries) ...[
                      const SizedBox(height: 18),
                      Text(
                        category.key,
                        style: const TextStyle(
                          color: Color(0xFF66727D),
                          fontSize: 13,
                        ),
                      ),
                      const SizedBox(height: 8),
                      Wrap(
                        spacing: 8,
                        runSpacing: 8,
                        children: [
                          for (final model in category.value.keys)
                            _choice(model, _model == model, () {
                              setState(() => _model = model);
                              _continue();
                            }),
                        ],
                      ),
                    ],
                  ],
                  if (_saving) const Center(child: CircularProgressIndicator()),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}
