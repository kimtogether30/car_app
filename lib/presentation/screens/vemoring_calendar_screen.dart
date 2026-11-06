import 'package:flutter/material.dart';

import '../data/care_history.dart';
import '../widgets/vemoring_page.dart';

extension on CareKind {
  IconData get icon => switch (this) {
    CareKind.wash => Icons.local_car_wash_outlined,
    CareKind.fuel => Icons.local_gas_station_outlined,
    CareKind.oil => Icons.oil_barrel_outlined,
  };
  Color get color => switch (this) {
    CareKind.wash => vemoringBlue,
    CareKind.fuel => vemoringOrange,
    CareKind.oil => vemoringMint,
  };
}

class VemoringCalendarScreen extends StatefulWidget {
  const VemoringCalendarScreen({
    super.key,
    required this.navigationBar,
    required this.records,
    required this.onSave,
  });
  final Widget navigationBar;
  final List<CareRecord> records;
  final Future<void> Function(CareRecord) onSave;
  @override
  State<VemoringCalendarScreen> createState() => _VemoringCalendarScreenState();
}

class _VemoringCalendarScreenState extends State<VemoringCalendarScreen> {
  late DateTime _month = DateTime(DateTime.now().year, DateTime.now().month);
  DateTime? _selected;
  bool _all = false;
  void _move(int offset) => setState(() {
    _month = DateTime(_month.year, _month.month + offset);
    _selected = null;
  });

  Future<void> _edit([CareRecord? record]) async {
    if (record == null && _selected != null && isFutureCareDay(_selected!)) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('미래 날짜에는 관리 기록을 등록할 수 없어요.')),
      );
      return;
    }
    final result = await vemoringSheet<CareRecord>(
      context,
      CareRecordForm(
        initialDate: record?.date ?? _selected ?? careDay(DateTime.now()),
        existing: record,
      ),
    );
    if (result == null || !mounted) return;
    try {
      await widget.onSave(result);
      if (!mounted) return;
      setState(() {
        _month = DateTime(result.date.year, result.date.month);
        _selected = result.date;
      });
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('저장하지 못했어요. 날짜를 확인하고 다시 시도해 주세요.')),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final offset = _month.weekday % 7;
    final days = DateTime(_month.year, _month.month + 1, 0).day;
    final sorted = [...widget.records]
      ..sort((a, b) => b.date.compareTo(a.date));
    final visible = _all
        ? sorted
        : _selected != null
        ? sorted.where((r) => DateUtils.isSameDay(r.date, _selected)).toList()
        : sorted.take(3).toList();
    return VemoringPage(
      navigationBar: widget.navigationBar,
      child: ListView(
        padding: const EdgeInsets.fromLTRB(22, 0, 22, 24),
        children: [
          VemoringPageHeader(
            eyebrow: 'MY CAR LOG',
            title: '관리 기록',
            trailing: IconButton.filled(
              tooltip: '관리 기록 추가',
              onPressed: () => _edit(),
              style: IconButton.styleFrom(
                backgroundColor: vemoringBlue,
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(14),
                ),
              ),
              icon: const Icon(Icons.add),
            ),
          ),
          const Padding(
            padding: EdgeInsets.only(bottom: 12),
            child: Text(
              '직접 입력한 기록 · 이 기기/브라우저에 저장됩니다.',
              style: TextStyle(fontSize: 10),
            ),
          ),
          Container(
            decoration: vemoringCard(),
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
              for (final kind in CareKind.values)
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
                    Text(kind.label, style: const TextStyle(fontSize: 10)),
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
            decoration: vemoringCard(),
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
                            key: ValueKey('record-${record.id}'),
                            leading: Icon(
                              record.kind.icon,
                              color: record.kind.color,
                            ),
                            title: Text(
                              record.title,
                              style: const TextStyle(
                                fontSize: 12,
                                fontWeight: FontWeight.w700,
                              ),
                            ),
                            subtitle: record.detail.isEmpty
                                ? null
                                : Text(
                                    record.detail,
                                    style: const TextStyle(
                                      fontSize: 10,
                                      color: Color(0xFF979AA5),
                                    ),
                                  ),
                            trailing: Text(
                              careDateLabel(record.date),
                              style: const TextStyle(fontSize: 10),
                            ),
                            onTap: () => vemoringSheet<void>(
                              context,
                              SingleChildScrollView(
                                child: Padding(
                                  padding: const EdgeInsets.all(22),
                                  child: Column(
                                    crossAxisAlignment:
                                        CrossAxisAlignment.start,
                                    mainAxisSize: MainAxisSize.min,
                                    children: [
                                      VemoringSheetHeader(title: record.title),
                                      Text(careDateLabel(record.date)),
                                      if (record.detail.isNotEmpty) ...[
                                        const SizedBox(height: 12),
                                        Text(record.detail),
                                      ],
                                      TextButton(
                                        onPressed: () {
                                          Navigator.pop(context);
                                          _edit(record);
                                        },
                                        child: const Text('기록 수정'),
                                      ),
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
    final matching = widget.records
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
            border: selected ? Border.all(color: vemoringBlue, width: 2) : null,
          ),
          padding: const EdgeInsets.only(top: 6),
          child: Column(
            children: [
              Container(
                padding: const EdgeInsets.all(4),
                decoration: BoxDecoration(
                  color: today ? vemoringBlue : null,
                  shape: BoxShape.circle,
                ),
                child: Text(
                  '$day',
                  style: TextStyle(
                    fontSize: 11,
                    color: today
                        ? Colors.white
                        : isFutureCareDay(date)
                        ? Colors.grey
                        : vemoringInk,
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

class CareRecordForm extends StatefulWidget {
  const CareRecordForm({super.key, required this.initialDate, this.existing});
  final DateTime initialDate;
  final CareRecord? existing;
  @override
  State<CareRecordForm> createState() => _CareRecordFormState();
}

class _CareRecordFormState extends State<CareRecordForm> {
  final _form = GlobalKey<FormState>();
  late final _title = TextEditingController(text: widget.existing?.title);
  late final _detail = TextEditingController(text: widget.existing?.detail);
  late DateTime _date = isFutureCareDay(widget.initialDate)
      ? careDay(DateTime.now())
      : widget.initialDate;
  late CareKind _kind = widget.existing?.kind ?? CareKind.wash;
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
            VemoringSheetHeader(
              title: widget.existing == null ? '관리 기록 추가' : '관리 기록 수정',
            ),
            DropdownButtonFormField<CareKind>(
              initialValue: _kind,
              decoration: const InputDecoration(labelText: '관리 종류'),
              items: [
                for (final kind in CareKind.values)
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
                  firstDate: DateTime(1980),
                  lastDate: careDay(DateTime.now()),
                );
                if (date != null && mounted) setState(() => _date = date);
              },
            ),
            const SizedBox(height: 16),
            SizedBox(
              width: double.infinity,
              child: FilledButton(
                onPressed: () {
                  if (_form.currentState!.validate() &&
                      !isFutureCareDay(_date)) {
                    Navigator.pop(
                      context,
                      CareRecord(
                        id:
                            widget.existing?.id ??
                            DateTime.now().microsecondsSinceEpoch.toString(),
                        date: _date,
                        kind: _kind,
                        title: _title.text.trim(),
                        detail: _detail.text.trim(),
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
