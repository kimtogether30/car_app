import 'package:flutter/material.dart';

import '../widgets/caron_page.dart';

enum _CareKind {
  wash('세차', Icons.local_car_wash_outlined, caronBlue),
  fuel('주유', Icons.local_gas_station_outlined, caronOrange),
  oil('엔진오일', Icons.oil_barrel_outlined, caronMint);

  const _CareKind(this.label, this.icon, this.color);
  final String label;
  final IconData icon;
  final Color color;
}

class _CareRecord {
  const _CareRecord(this.date, this.kind, this.title, this.detail);
  final DateTime date;
  final _CareKind kind;
  final String title, detail;
}

class CaronCalendarScreen extends StatefulWidget {
  const CaronCalendarScreen({super.key, required this.navigationBar});
  final Widget navigationBar;
  @override
  State<CaronCalendarScreen> createState() => _CaronCalendarScreenState();
}

class _CaronCalendarScreenState extends State<CaronCalendarScreen> {
  DateTime _month = DateTime(2025, 3);
  DateTime? _selected;
  bool _all = false;
  final _records = <_CareRecord>[
    _CareRecord(
      DateTime(2025, 3, 29),
      _CareKind.wash,
      '자동 세차',
      '워시존 성수점 · 18,000원',
    ),
    _CareRecord(
      DateTime(2025, 3, 19),
      _CareKind.fuel,
      '주유',
      '휘발유 34L · 56,400원',
    ),
    _CareRecord(
      DateTime(2025, 3, 14),
      _CareKind.oil,
      '엔진오일 교체',
      '카센터 101 · 84,000원',
    ),
    _CareRecord(DateTime(2025, 3, 9), _CareKind.fuel, '주유', '원본 달력 예시'),
    _CareRecord(DateTime(2025, 3, 3), _CareKind.wash, '세차', '원본 달력 예시'),
  ];

  void _move(int offset) => setState(() {
    _month = DateTime(_month.year, _month.month + offset);
    _selected = null;
  });

  Future<void> _add() async {
    final result = await caronSheet<_CareRecord>(
      context,
      _RecordForm(initialDate: _selected ?? _month),
    );
    if (result == null || !mounted) return;
    setState(() {
      final index = _records.indexWhere(
        (record) => record.date.isBefore(result.date),
      );
      _records.insert(index < 0 ? _records.length : index, result);
      _month = DateTime(result.date.year, result.date.month);
      _selected = result.date;
    });
  }

  @override
  Widget build(BuildContext context) {
    final offset = _month.weekday % 7;
    final days = DateTime(_month.year, _month.month + 1, 0).day;
    final visible = _all
        ? _records
        : _selected != null
        ? _records.where((r) => DateUtils.isSameDay(r.date, _selected)).toList()
        : _records
              .where(
                (r) =>
                    r.date.year == _month.year && r.date.month == _month.month,
              )
              .take(3)
              .toList();
    return CaronPage(
      navigationBar: widget.navigationBar,
      child: ListView(
        padding: const EdgeInsets.fromLTRB(22, 0, 22, 24),
        children: [
          CaronPageHeader(
            eyebrow: 'MY CAR LOG',
            title: '관리 기록',
            trailing: IconButton.filled(
              tooltip: '관리 기록 추가',
              onPressed: _add,
              style: IconButton.styleFrom(
                backgroundColor: caronBlue,
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(14),
                ),
              ),
              icon: const Icon(Icons.add),
            ),
          ),
          const CaronSampleNotice(),
          Container(
            decoration: caronCard(),
            padding: const EdgeInsets.all(13),
            child: Column(
              children: [
                Row(
                  children: [
                    IconButton(
                      tooltip: '이전 달',
                      onPressed: () => _move(-1),
                      icon: const Icon(Icons.chevron_left),
                    ),
                    Expanded(
                      child: Text(
                        '${_month.year}년 ${_month.month}월',
                        textAlign: TextAlign.center,
                        style: const TextStyle(
                          fontSize: 16,
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                    ),
                    IconButton(
                      tooltip: '다음 달',
                      onPressed: () => _move(1),
                      icon: const Icon(Icons.chevron_right),
                    ),
                  ],
                ),
                Row(
                  children: [
                    for (final day in ['일', '월', '화', '수', '목', '금', '토'])
                      Expanded(
                        child: Text(
                          day,
                          textAlign: TextAlign.center,
                          style: TextStyle(
                            fontSize: 10,
                            color: day == '일'
                                ? const Color(0xFFE47B6B)
                                : const Color(0xFFA4A7B0),
                          ),
                        ),
                      ),
                  ],
                ),
                const SizedBox(height: 6),
                LayoutBuilder(
                  builder: (context, constraints) => Wrap(
                    children: [
                      for (
                        var cell = 0;
                        cell < ((offset + days + 6) ~/ 7) * 7;
                        cell++
                      )
                        SizedBox(
                          width: constraints.maxWidth / 7,
                          height: 68,
                          child: cell < offset || cell >= offset + days
                              ? null
                              : _day(cell - offset + 1),
                        ),
                    ],
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 12),
          Wrap(
            alignment: WrapAlignment.center,
            spacing: 17,
            children: [
              for (final kind in _CareKind.values)
                Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Container(
                      width: 7,
                      height: 7,
                      decoration: BoxDecoration(
                        color: kind.color,
                        borderRadius: BorderRadius.circular(2),
                      ),
                    ),
                    const SizedBox(width: 5),
                    Text(
                      kind.label,
                      style: const TextStyle(
                        fontSize: 10,
                        color: Color(0xFF7A7F8E),
                      ),
                    ),
                  ],
                ),
            ],
          ),
          const SizedBox(height: 18),
          Row(
            children: [
              Expanded(
                child: Text(
                  _all
                      ? '전체 관리 기록'
                      : _selected != null
                      ? '${_selected!.month}월 ${_selected!.day}일 관리'
                      : '최근 관리',
                  style: const TextStyle(
                    fontSize: 16,
                    fontWeight: FontWeight.w700,
                  ),
                ),
              ),
              TextButton(
                onPressed: () => setState(() {
                  _all = !_all;
                  _selected = null;
                }),
                child: Text(_all ? '최근 보기' : '전체 보기'),
              ),
            ],
          ),
          Container(
            decoration: caronCard(),
            child: Material(
              color: Colors.transparent,
              borderRadius: BorderRadius.circular(20),
              clipBehavior: Clip.antiAlias,
              child: visible.isEmpty
                  ? const Padding(
                      padding: EdgeInsets.all(24),
                      child: Text(
                        '등록된 관리 기록이 없어요.',
                        textAlign: TextAlign.center,
                      ),
                    )
                  : Column(
                      children: [
                        for (final record in visible)
                          ListTile(
                            leading: Container(
                              padding: const EdgeInsets.all(9),
                              decoration: BoxDecoration(
                                color: record.kind.color.withValues(alpha: .1),
                                borderRadius: BorderRadius.circular(12),
                              ),
                              child: Icon(
                                record.kind.icon,
                                color: record.kind.color,
                                size: 20,
                              ),
                            ),
                            title: Text(
                              record.title,
                              style: const TextStyle(
                                fontSize: 12,
                                fontWeight: FontWeight.w700,
                              ),
                            ),
                            subtitle: Text(
                              record.detail,
                              style: const TextStyle(
                                fontSize: 10,
                                color: Color(0xFF979AA5),
                              ),
                            ),
                            trailing: Text(
                              '${record.date.month}월 ${record.date.day}일',
                              style: const TextStyle(fontSize: 10),
                            ),
                            onTap: () => caronSheet<void>(
                              context,
                              SingleChildScrollView(
                                child: Padding(
                                  padding: const EdgeInsets.all(22),
                                  child: Column(
                                    crossAxisAlignment:
                                        CrossAxisAlignment.start,
                                    mainAxisSize: MainAxisSize.min,
                                    children: [
                                      CaronSheetHeader(title: record.title),
                                      const CaronSampleNotice(),
                                      Text(
                                        '${record.date.year}년 ${record.date.month}월 ${record.date.day}일',
                                      ),
                                      const SizedBox(height: 12),
                                      Text(record.detail),
                                    ],
                                  ),
                                ),
                              ),
                            ),
                          ),
                      ],
                    ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _day(int day) {
    final date = DateTime(_month.year, _month.month, day);
    final matching = _records
        .where((r) => DateUtils.isSameDay(r.date, date))
        .toList();
    final selected = DateUtils.isSameDay(_selected, date);
    final today = DateUtils.isSameDay(DateTime.now(), date);
    return Semantics(
      selected: selected,
      child: InkWell(
        key: ValueKey('day-${date.year}-${date.month}-$day'),
        borderRadius: BorderRadius.circular(12),
        onTap: () => setState(() {
          _selected = selected ? null : date;
          _all = false;
        }),
        child: Container(
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(12),
            color: selected ? const Color(0xFFF5F7FF) : null,
            border: selected ? Border.all(color: caronBlue, width: 2) : null,
          ),
          padding: const EdgeInsets.only(top: 6),
          child: Column(
            children: [
              Container(
                padding: const EdgeInsets.all(4),
                decoration: BoxDecoration(
                  color: today ? caronBlue : null,
                  shape: BoxShape.circle,
                ),
                child: Text(
                  '$day',
                  style: TextStyle(
                    fontSize: 11,
                    color: today ? Colors.white : caronInk,
                  ),
                ),
              ),
              if (matching.isNotEmpty)
                Padding(
                  padding: const EdgeInsets.only(top: 3),
                  child: Text(
                    matching.length > 1
                        ? '${matching.length}건'
                        : matching.first.kind.label,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: TextStyle(
                      fontSize: 8,
                      fontWeight: FontWeight.w700,
                      color: matching.first.kind.color,
                    ),
                  ),
                ),
            ],
          ),
        ),
      ),
    );
  }
}

class _RecordForm extends StatefulWidget {
  const _RecordForm({required this.initialDate});
  final DateTime initialDate;
  @override
  State<_RecordForm> createState() => _RecordFormState();
}

class _RecordFormState extends State<_RecordForm> {
  final _form = GlobalKey<FormState>();
  final _title = TextEditingController();
  final _detail = TextEditingController();
  late DateTime _date = widget.initialDate;
  _CareKind _kind = _CareKind.wash;
  @override
  void dispose() {
    _title.dispose();
    _detail.dispose();
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
            const CaronSheetHeader(title: '관리 기록 추가'),
            const CaronSampleNotice(),
            DropdownButtonFormField<_CareKind>(
              initialValue: _kind,
              decoration: const InputDecoration(labelText: '관리 종류'),
              items: [
                for (final kind in _CareKind.values)
                  DropdownMenuItem(value: kind, child: Text(kind.label)),
              ],
              onChanged: (value) => setState(() => _kind = value!),
            ),
            TextFormField(
              controller: _title,
              decoration: const InputDecoration(labelText: '기록 이름'),
              validator: (value) => value == null || value.trim().isEmpty
                  ? '기록 이름을 입력해 주세요.'
                  : null,
            ),
            TextFormField(
              controller: _detail,
              decoration: const InputDecoration(labelText: '상세 내용 (장소·금액 등)'),
              maxLines: 2,
            ),
            ListTile(
              contentPadding: EdgeInsets.zero,
              title: const Text('관리 날짜'),
              subtitle: Text(
                '${_date.year}-${_date.month.toString().padLeft(2, '0')}-${_date.day.toString().padLeft(2, '0')}',
              ),
              trailing: const Icon(Icons.calendar_month),
              onTap: () async {
                final date = await showDatePicker(
                  context: context,
                  initialDate: _date,
                  firstDate: DateTime(2000),
                  lastDate: DateTime(2100),
                );
                if (date != null && mounted) setState(() => _date = date);
              },
            ),
            const SizedBox(height: 16),
            SizedBox(
              width: double.infinity,
              child: FilledButton(
                onPressed: () {
                  if (_form.currentState!.validate()) {
                    Navigator.pop(
                      context,
                      _CareRecord(
                        _date,
                        _kind,
                        _title.text.trim(),
                        _detail.text.trim(),
                      ),
                    );
                  }
                },
                child: const Text('기록 저장'),
              ),
            ),
          ],
        ),
      ),
    ),
  );
}
