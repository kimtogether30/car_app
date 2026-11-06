import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';

class PersonalSetupScreen extends StatefulWidget {
  const PersonalSetupScreen({
    super.key,
    required this.onComplete,
    required this.onBack,
  });
  final ValueChanged<Map<String, dynamic>> onComplete;
  final VoidCallback onBack;
  @override
  State<PersonalSetupScreen> createState() => _PersonalSetupScreenState();
}

class _PersonalSetupScreenState extends State<PersonalSetupScreen> {
  int _step = 0;
  String? _fuel, _oilEstimate;
  DateTime? _fuelDate, _washDate;
  bool _fuelUnknown = false, _washUnknown = false, _saving = false;
  final _nickname = TextEditingController();
  final _form = GlobalKey<FormState>();
  final _scroll = ScrollController();
  Map<String, dynamic>? _saved;
  @override
  void dispose() {
    _nickname.dispose();
    _scroll.dispose();
    super.dispose();
  }

  void _move(int step) {
    setState(() => _step = step);
    if (_scroll.hasClients) _scroll.jumpTo(0);
  }

  bool get _electric => _fuel == '전기차';
  void _advanceCareIfReady() {
    if (_step == 1 && _ready) _move(2);
  }

  bool get _ready => switch (_step) {
    0 => _fuel != null,
    1 =>
      (_fuelDate != null || _fuelUnknown) &&
          (_washDate != null || _washUnknown) &&
          (_electric || _oilEstimate != null),
    _ => true,
  };
  Future<void> _save() async {
    if (!_form.currentState!.validate()) return;
    setState(() => _saving = true);
    final data = <String, dynamic>{
      'fuel': _fuel,
      'nickname': _nickname.text.trim(),
      'lastFuel': _fuelDate?.toIso8601String(),
      'lastWash': _washDate?.toIso8601String(),
      'oilEstimate': _electric ? '해당 없음' : _oilEstimate,
      'washAlert': 30,
      'fuelAlert': 20,
    };
    try {
      final prefs = await SharedPreferences.getInstance();
      if (!await prefs.setString('personal_setup_v1', jsonEncode(data))) {
        throw StateError('Save failed');
      }
      if (!mounted) return;
      _saved = data;
      _move(3);
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(const SnackBar(content: Text('저장하지 못했어요. 다시 시도해 주세요.')));
      }
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  Widget _date(
    String label,
    DateTime? date,
    bool unknown,
    ValueChanged<DateTime?> update,
    ValueChanged<bool> markUnknown,
  ) => Column(
    children: [
      ListTile(
        contentPadding: EdgeInsets.zero,
        title: Text(label),
        subtitle: Text(
          date == null
              ? '날짜를 선택해 주세요'
              : '${date.year}.${date.month}.${date.day}',
        ),
        trailing: const Icon(Icons.calendar_month_outlined),
        onTap: () async {
          final now = DateTime.now();
          final picked = await showDatePicker(
            context: context,
            initialDate: date ?? now,
            firstDate: DateTime(1980),
            lastDate: now,
          );
          if (picked != null && mounted) {
            setState(() {
              update(picked);
              markUnknown(false);
            });
            _advanceCareIfReady();
          }
        },
      ),
      CheckboxListTile(
        contentPadding: EdgeInsets.zero,
        title: const Text('기억이 안 나요'),
        value: unknown,
        onChanged: (value) {
          setState(() {
            markUnknown(value!);
            if (value) update(null);
          });
          _advanceCareIfReady();
        },
      ),
      const Divider(),
    ],
  );
  @override
  Widget build(BuildContext context) => Scaffold(
    backgroundColor: const Color(0xFFE8E9ED),
    body: Center(
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 520),
        child: Material(
          color: const Color(0xFFF6F7F8),
          child: SafeArea(
            child: ListView(
              controller: _scroll,
              padding: const EdgeInsets.all(24),
              children: [
                const Text(
                  'VEMORING',
                  style: TextStyle(
                    color: Color(0xFF335C81),
                    fontSize: 22,
                    fontWeight: FontWeight.w800,
                  ),
                ),
                const SizedBox(height: 20),
                if (_step < 3) ...[
                  LinearProgressIndicator(value: (_step + 3) / 6),
                  TextButton(
                    onPressed: _saving
                        ? null
                        : () {
                            if (_step == 0) {
                              widget.onBack();
                            } else {
                              _move(_step - 1);
                            }
                          },
                    child: const Text('이전'),
                  ),
                ],
                Text(
                  switch (_step) {
                    0 => '주유 방식을 선택해 주세요',
                    1 => '마지막으로 관리한 때는\n언제인가요?',
                    2 => '차량의 닉네임을 지어 주세요',
                    _ => '기본 알림 기준을 설정해 뒀어요! 🚗',
                  },
                  style: const TextStyle(
                    fontSize: 26,
                    fontWeight: FontWeight.w800,
                  ),
                ),
                const SizedBox(height: 24),
                if (_step == 0)
                  Wrap(
                    spacing: 10,
                    runSpacing: 12,
                    children: [
                      for (final fuel in ['휘발유', '경유', '하이브리드', '전기차'])
                        ChoiceChip(
                          label: Text(fuel),
                          selected: _fuel == fuel,
                          onSelected: (_) => setState(() {
                            if (_fuel != fuel) {
                              _fuelDate = null;
                              _fuelUnknown = false;
                              _oilEstimate = null;
                            }
                            _fuel = fuel;
                            _step = 1;
                          }),
                        ),
                    ],
                  ),
                if (_step == 1) ...[
                  _date(
                    _electric ? '마지막 충전일' : '마지막 주유일',
                    _fuelDate,
                    _fuelUnknown,
                    (date) => _fuelDate = date,
                    (value) => _fuelUnknown = value,
                  ),
                  _date(
                    '마지막 세차일',
                    _washDate,
                    _washUnknown,
                    (date) => _washDate = date,
                    (value) => _washUnknown = value,
                  ),
                  if (!_electric) ...[
                    const SizedBox(height: 16),
                    const Text(
                      '엔진오일은 대략 언제 교체하셨나요?',
                      style: TextStyle(fontWeight: FontWeight.w700),
                    ),
                    const Text('정확한 날짜는 몰라도 괜찮아요.'),
                    const SizedBox(height: 12),
                    Wrap(
                      spacing: 8,
                      runSpacing: 8,
                      children: [
                        for (final option in [
                          '1개월 이내',
                          '1~3개월 전',
                          '3~6개월 전',
                          '6~12개월 전',
                          '1년 이상 전',
                          '기억이 안 나요',
                        ])
                          ChoiceChip(
                            label: Text(option),
                            selected: _oilEstimate == option,
                            onSelected: (_) {
                              setState(() => _oilEstimate = option);
                              _advanceCareIfReady();
                            },
                          ),
                      ],
                    ),
                  ] else
                    const Text('전기차는 엔진오일 교체가 필요하지 않아요.'),
                ],
                if (_step == 2)
                  Form(
                    key: _form,
                    child: TextFormField(
                      controller: _nickname,
                      maxLength: 20,
                      textInputAction: TextInputAction.done,
                      decoration: const InputDecoration(
                        labelText: '차량 닉네임',
                        hintText: '예: 몽글이',
                        border: OutlineInputBorder(),
                      ),
                      validator: (value) =>
                          value == null || value.trim().isEmpty
                          ? '닉네임을 입력해 주세요.'
                          : null,
                    ),
                  ),
                if (_step == 3) ...[
                  const Text(
                    '🧽 세차 알림: 청결도 30% 이하',
                    style: TextStyle(fontSize: 17),
                  ),
                  const SizedBox(height: 16),
                  const Text(
                    '⛽ 주유 알림: 연료 20% 이하',
                    style: TextStyle(fontSize: 17),
                  ),
                  const SizedBox(height: 24),
                  const Text(
                    '언제든 [내 차 설정] 메뉴에서 내 운전 습관에 맞춰 바꿀 수 있어요!',
                    style: TextStyle(fontSize: 16, height: 1.6),
                  ),
                ],
                const SizedBox(height: 32),
                if (_step != 0)
                  FilledButton(
                    onPressed: !_ready || _saving
                        ? null
                        : () {
                            if (_step == 2) {
                              _save();
                            } else if (_step == 3) {
                              widget.onComplete(_saved!);
                            } else {
                              _move(_step + 1);
                            }
                          },
                    child: Text(
                      _saving
                          ? '저장 중…'
                          : _step == 3
                          ? '홈으로 가기'
                          : '다음',
                    ),
                  ),
              ],
            ),
          ),
        ),
      ),
    ),
  );
}
