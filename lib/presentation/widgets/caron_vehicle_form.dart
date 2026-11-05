import 'package:flutter/material.dart';

import '../../shared/models/vehicle_status.dart';
import 'caron_page.dart';
import '../data/care_history.dart';

class CaronVehicleEdit {
  const CaronVehicleEdit(
    this.vehicle,
    this.model,
    this.lastWash,
    this.lastOil, {
    this.lastFuel,
  });
  final VehicleStatus vehicle;
  final String model;
  final DateTime? lastWash, lastOil, lastFuel;
}

/// 서버 차량 목록이 없는 상태에서 사용하는 예시 등록 양식.
class CaronVehicleForm extends StatefulWidget {
  const CaronVehicleForm({
    super.key,
    required this.vehicle,
    required this.model,
    this.lastWash,
    this.lastOil,
    this.lastFuel,
  });
  final VehicleStatus vehicle;
  final String model;
  final DateTime? lastWash, lastOil, lastFuel;
  @override
  State<CaronVehicleForm> createState() => _CaronVehicleFormState();
}

class _CaronVehicleFormState extends State<CaronVehicleForm> {
  final _form = GlobalKey<FormState>();
  late final _name = TextEditingController(text: widget.vehicle.vehicleName);
  late final _plate = TextEditingController(text: widget.vehicle.licensePlate);
  late final _model = TextEditingController(text: widget.model);
  late VehicleType _type = widget.vehicle.vehicleType;
  late DateTime? _wash = widget.lastWash, _oil = widget.lastOil;
  late DateTime? _fuelDate = widget.lastFuel;
  String _brand = '현대';
  String _fuel = '휘발유';
  int _step = 1;
  @override
  void dispose() {
    _name.dispose();
    _plate.dispose();
    _model.dispose();
    super.dispose();
  }

  String? _required(String? value) =>
      value == null || value.trim().isEmpty ? '값을 입력해 주세요.' : null;

  @override
  Widget build(BuildContext context) => SingleChildScrollView(
    child: Padding(
      padding: const EdgeInsets.all(22),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        mainAxisSize: MainAxisSize.min,
        children: [
          const CaronSheetHeader(title: '차량 정보 등록'),
          const CaronSampleNotice(),
          LinearProgressIndicator(value: _step / 2, color: caronBlue),
          const SizedBox(height: 24),
          Text(
            '$_step / 2 · ${_step == 1 ? '차량 선택' : '기본 정보'}',
            style: const TextStyle(
              color: caronBlue,
              fontWeight: FontWeight.w700,
            ),
          ),
          const SizedBox(height: 10),
          if (_step == 1) ...[
            const Text(
              '어떤 차와 함께\n달리고 계신가요?',
              style: TextStyle(fontSize: 27, fontWeight: FontWeight.w700),
            ),
            const SizedBox(height: 20),
            const Text('브랜드 (예시 목록)'),
            Wrap(
              spacing: 8,
              children: [
                for (final brand in ['현대', '기아', '제네시스', '쉐보레', '르노', '기타'])
                  ChoiceChip(
                    label: Text(brand),
                    selected: _brand == brand,
                    onSelected: (_) => setState(() => _brand = brand),
                  ),
              ],
            ),
            const SizedBox(height: 12),
            const Text('차종 (예시 목록 · 직접 입력 가능)'),
            Wrap(
              spacing: 8,
              children: [
                for (final model in ['캐스퍼', '아반떼', '쏘나타', '투싼'])
                  ActionChip(
                    label: Text(model),
                    onPressed: () =>
                        setState(() => _model.text = '$_brand $model'),
                  ),
              ],
            ),
            TextField(
              controller: _model,
              decoration: const InputDecoration(labelText: '차종'),
            ),
            const SizedBox(height: 20),
            SizedBox(
              width: double.infinity,
              child: FilledButton(
                onPressed: () => setState(() => _step = 2),
                child: const Text('다음'),
              ),
            ),
          ] else ...[
            TextButton(
              onPressed: () => setState(() => _step = 1),
              child: const Text('‹ 이전'),
            ),
            Form(
              key: _form,
              child: Column(
                children: [
                  TextFormField(
                    controller: _model,
                    decoration: const InputDecoration(labelText: '차종'),
                    validator: _required,
                  ),
                  DropdownButtonFormField<VehicleType>(
                    initialValue: _type,
                    decoration: const InputDecoration(labelText: '차량 종류'),
                    items: const [
                      DropdownMenuItem(
                        value: VehicleType.combustion,
                        child: Text('내연기관'),
                      ),
                      DropdownMenuItem(
                        value: VehicleType.electric,
                        child: Text('전기'),
                      ),
                      DropdownMenuItem(
                        value: VehicleType.hybrid,
                        child: Text('하이브리드'),
                      ),
                    ],
                    onChanged: (value) => setState(() => _type = value!),
                  ),
                  if (_type != VehicleType.electric)
                    DropdownButtonFormField<String>(
                      initialValue: _fuel,
                      decoration: const InputDecoration(
                        labelText: '주유 타입 (참고 입력)',
                      ),
                      items: const [
                        DropdownMenuItem(value: '휘발유', child: Text('휘발유')),
                        DropdownMenuItem(value: '경유', child: Text('경유')),
                      ],
                      onChanged: (value) => _fuel = value!,
                    ),
                  TextFormField(
                    controller: _plate,
                    decoration: const InputDecoration(labelText: '차량 번호'),
                    validator: _required,
                  ),
                  TextFormField(
                    controller: _name,
                    decoration: const InputDecoration(labelText: '차량 닉네임'),
                    validator: _required,
                  ),
                  _dateRow(
                    '마지막 주유일',
                    _fuelDate,
                    (date) => setState(() => _fuelDate = date),
                  ),
                  _dateRow(
                    '마지막 세차일',
                    _wash,
                    (date) => setState(() => _wash = date),
                  ),
                  _dateRow(
                    '마지막 엔진오일 교체일',
                    _oil,
                    (date) => setState(() => _oil = date),
                  ),
                ],
              ),
            ),
            const Text(
              '주유 타입은 참고 입력이며 기존 공통 모델에는 저장되지 않습니다. 세차 주기와 주유 알림은 내 차 화면에서 설정할 수 있어요.',
              style: TextStyle(fontSize: 10, color: Color(0xFF777D90)),
            ),
            const SizedBox(height: 20),
            SizedBox(
              width: double.infinity,
              child: FilledButton(
                onPressed: () {
                  if (!_form.currentState!.validate()) return;
                  if ([
                    _wash,
                    _oil,
                    _fuelDate,
                  ].whereType<DateTime>().any(isFutureCareDay)) {
                    ScaffoldMessenger.of(context).showSnackBar(
                      const SnackBar(
                        content: Text('미래 날짜에는 관리 기록을 등록할 수 없어요.'),
                      ),
                    );
                    return;
                  }
                  final old = widget.vehicle;
                  Navigator.pop(
                    context,
                    CaronVehicleEdit(
                      VehicleStatus(
                        vehicleName: _name.text.trim(),
                        licensePlate: _plate.text.trim(),
                        vehicleType: _type,
                        fuelLevelPercent: _type == VehicleType.electric
                            ? null
                            : old.fuelLevelPercent,
                        batteryLevelPercent: _type == VehicleType.combustion
                            ? null
                            : old.batteryLevelPercent,
                        cleanlinessPercent: old.cleanlinessPercent,
                        characterMessage: old.characterMessage,
                      ),
                      _model.text.trim(),
                      _wash,
                      _oil,
                      lastFuel: _fuelDate,
                    ),
                  );
                },
                child: const Text('CAR:ON 시작하기'),
              ),
            ),
          ],
        ],
      ),
    ),
  );

  Widget _dateRow(
    String label,
    DateTime? value,
    ValueChanged<DateTime> onChanged,
  ) => ListTile(
    contentPadding: EdgeInsets.zero,
    title: Text(label, style: const TextStyle(fontSize: 12)),
    subtitle: Text(
      value == null
          ? '날짜 선택'
          : '${value.year}-${value.month.toString().padLeft(2, '0')}-${value.day.toString().padLeft(2, '0')}',
    ),
    trailing: const Icon(Icons.calendar_month),
    onTap: () async {
      final date = await showDatePicker(
        context: context,
        initialDate: value == null || isFutureCareDay(value)
            ? careDay(DateTime.now())
            : value,
        firstDate: DateTime(1980),
        lastDate: careDay(DateTime.now()),
      );
      if (date != null && mounted) onChanged(date);
    },
  );
}

class CaronTextEdit extends StatefulWidget {
  const CaronTextEdit({super.key, required this.title, required this.value});
  final String title, value;
  @override
  State<CaronTextEdit> createState() => _CaronTextEditState();
}

class _CaronTextEditState extends State<CaronTextEdit> {
  late final _controller = TextEditingController(text: widget.value);
  final _form = GlobalKey<FormState>();
  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => SingleChildScrollView(
    child: Padding(
      padding: const EdgeInsets.all(22),
      child: Form(
        key: _form,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            CaronSheetHeader(title: '${widget.title} 변경'),
            const CaronSampleNotice(),
            TextFormField(
              autofocus: true,
              controller: _controller,
              decoration: InputDecoration(labelText: widget.title),
              validator: (value) =>
                  value == null || value.trim().isEmpty ? '값을 입력해 주세요.' : null,
            ),
            const SizedBox(height: 20),
            SizedBox(
              width: double.infinity,
              child: FilledButton(
                onPressed: () {
                  if (_form.currentState!.validate()) {
                    Navigator.pop(context, _controller.text.trim());
                  }
                },
                child: const Text('저장하기'),
              ),
            ),
          ],
        ),
      ),
    ),
  );
}
