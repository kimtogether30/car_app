import 'package:flutter/material.dart';

import '../../shared/models/vehicle_status.dart';
import '../widgets/caron_car_character.dart';
import '../widgets/caron_page.dart';

class CaronProfileScreen extends StatefulWidget {
  const CaronProfileScreen({
    super.key,
    required this.vehicle,
    required this.vehicleModel,
    required this.navigationBar,
    required this.onEditNickname,
    required this.onEditModel,
    required this.onRegister,
  });
  final VehicleStatus vehicle;
  final String vehicleModel;
  final Widget navigationBar;
  final VoidCallback onEditNickname, onEditModel, onRegister;
  @override
  State<CaronProfileScreen> createState() => _CaronProfileScreenState();
}

class _CaronProfileScreenState extends State<CaronProfileScreen> {
  bool _notifications = true;
  double _washCycle = 14;
  double _fuelAlert = 20;

  @override
  Widget build(BuildContext context) => CaronPage(
    navigationBar: widget.navigationBar,
    child: ListView(
      padding: const EdgeInsets.fromLTRB(22, 0, 22, 24),
      children: [
        const CaronPageHeader(
          eyebrow: 'MY GARAGE',
          title: '내 차와 설정',
          trailing: Icon(Icons.settings_outlined, color: Color(0xFF4C5572)),
        ),
        const CaronSampleNotice(),
        Container(
          padding: const EdgeInsets.symmetric(vertical: 14, horizontal: 12),
          decoration: BoxDecoration(
            color: caronNavy,
            borderRadius: BorderRadius.circular(23),
          ),
          child: Row(
            children: [
              const SizedBox(width: 100, child: CaronCarCharacter()),
              const SizedBox(width: 8),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text(
                      'MY CAR',
                      style: TextStyle(
                        fontSize: 8,
                        letterSpacing: 1.5,
                        color: Color(0xFF9CB0FF),
                      ),
                    ),
                    Text(
                      widget.vehicle.vehicleName,
                      style: const TextStyle(
                        fontSize: 19,
                        fontWeight: FontWeight.w700,
                        color: Colors.white,
                      ),
                    ),
                    Text(
                      '${widget.vehicleModel} · ${widget.vehicle.licensePlate}',
                      style: const TextStyle(
                        fontSize: 9,
                        color: Color(0xFFBEC8EE),
                      ),
                    ),
                  ],
                ),
              ),
              IconButton(
                tooltip: '차량 정보 편집',
                onPressed: widget.onRegister,
                icon: const Icon(
                  Icons.edit_outlined,
                  color: Colors.white,
                  size: 18,
                ),
              ),
            ],
          ),
        ),
        const SizedBox(height: 23),
        _group(
          '차량 정보',
          Column(
            children: [
              _row(
                Icons.person_outline,
                '닉네임',
                widget.vehicle.vehicleName,
                widget.onEditNickname,
              ),
              _row(
                Icons.directions_car_outlined,
                '차종',
                widget.vehicleModel,
                widget.onEditModel,
              ),
              _row(
                Icons.calendar_month_outlined,
                '차량 정보 다시 등록',
                '',
                widget.onRegister,
              ),
            ],
          ),
        ),
        _group(
          '알림 설정',
          SwitchListTile(
            contentPadding: const EdgeInsets.symmetric(horizontal: 14),
            secondary: _icon(Icons.notifications_none, caronOrange),
            title: const Text(
              '관리 알림',
              style: TextStyle(fontSize: 12, fontWeight: FontWeight.w700),
            ),
            subtitle: const Text(
              '예시 설정 · 실제 알림은 발송되지 않습니다.',
              style: TextStyle(fontSize: 10, color: Color(0xFF9A9DA7)),
            ),
            value: _notifications,
            activeTrackColor: caronBlue,
            onChanged: (value) => setState(() => _notifications = value),
          ),
        ),
        Container(
          padding: const EdgeInsets.all(14),
          decoration: caronCard(),
          child: Column(
            children: [
              const Row(
                children: [
                  Expanded(
                    child: Text(
                      '개인 커스텀',
                      style: TextStyle(
                        fontSize: 13,
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                  ),
                  Text(
                    '내 운전 습관에 맞게',
                    style: TextStyle(fontSize: 9, color: Color(0xFF9599A5)),
                  ),
                ],
              ),
              const SizedBox(height: 18),
              _range(
                '내 세차 주기',
                '${_washCycle.round()}일',
                Icons.local_car_wash_outlined,
                caronBlue,
                _washCycle,
                7,
                30,
                23,
                (value) => setState(() => _washCycle = value),
              ),
              const Divider(color: Color(0xFFF0F0F2)),
              _range(
                '주유 알림 기준',
                '${_fuelAlert.round()}%',
                Icons.local_gas_station_outlined,
                caronOrange,
                _fuelAlert,
                5,
                40,
                7,
                (value) => setState(() => _fuelAlert = value),
              ),
            ],
          ),
        ),
      ],
    ),
  );

  Widget _group(String title, Widget child) => Padding(
    padding: const EdgeInsets.only(bottom: 21),
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          title,
          style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w700),
        ),
        const SizedBox(height: 8),
        Container(
          decoration: caronCard(),
          clipBehavior: Clip.antiAlias,
          child: Material(color: Colors.transparent, child: child),
        ),
      ],
    ),
  );

  Widget _icon(IconData icon, Color color) => Container(
    width: 32,
    height: 32,
    decoration: BoxDecoration(
      color: color.withValues(alpha: .1),
      borderRadius: BorderRadius.circular(10),
    ),
    child: Icon(icon, size: 19, color: color),
  );

  Widget _row(IconData icon, String title, String value, VoidCallback onTap) =>
      ListTile(
        leading: _icon(icon, caronBlue),
        title: Text(
          title,
          style: const TextStyle(fontSize: 11, fontWeight: FontWeight.w700),
        ),
        subtitle: value.isEmpty
            ? null
            : Text(
                value,
                style: const TextStyle(fontSize: 10, color: Color(0xFF7F8391)),
              ),
        trailing: const Icon(
          Icons.chevron_right,
          size: 18,
          color: Color(0xFFA1A4AE),
        ),
        onTap: onTap,
      );

  Widget _range(
    String label,
    String valueLabel,
    IconData icon,
    Color color,
    double value,
    double min,
    double max,
    int divisions,
    ValueChanged<double> onChanged,
  ) => Column(
    children: [
      Row(
        children: [
          _icon(icon, color),
          const SizedBox(width: 10),
          Expanded(
            child: Text(
              label,
              style: const TextStyle(fontSize: 11, fontWeight: FontWeight.w700),
            ),
          ),
          Text(
            valueLabel,
            style: TextStyle(color: color, fontWeight: FontWeight.w700),
          ),
        ],
      ),
      Slider(
        label: valueLabel,
        semanticFormatterCallback: (_) => '$label $valueLabel',
        activeColor: color,
        value: value,
        min: min,
        max: max,
        divisions: divisions,
        onChanged: onChanged,
      ),
      Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text('${min.round()}${label == '내 세차 주기' ? '일' : '%'}'),
          Text('${max.round()}${label == '내 세차 주기' ? '일' : '%'}'),
        ],
      ),
    ],
  );
}
