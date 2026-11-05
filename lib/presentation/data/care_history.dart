import 'dart:convert';

import 'package:shared_preferences/shared_preferences.dart';

enum CareKind { wash, fuel, oil }

extension CareKindLabel on CareKind {
  String get label => switch (this) {
    CareKind.wash => '세차',
    CareKind.fuel => '주유',
    CareKind.oil => '엔진오일 교체',
  };
  String get personalKey => switch (this) {
    CareKind.wash => 'lastWash',
    CareKind.fuel => 'lastFuel',
    CareKind.oil => 'lastOil',
  };
}

DateTime careDay(DateTime date) => DateTime(date.year, date.month, date.day);
bool isFutureCareDay(DateTime date) =>
    careDay(date).isAfter(careDay(DateTime.now()));
String careDateLabel(DateTime date) =>
    '${date.year}년 ${date.month}월 ${date.day}일';

class CareRecord {
  CareRecord({
    required this.id,
    required DateTime date,
    required this.kind,
    required this.title,
    this.detail = '',
  }) : date = careDay(date);
  final String id, title, detail;
  final DateTime date;
  final CareKind kind;
  Map<String, dynamic> toJson() => {
    'id': id,
    'date': date.toIso8601String(),
    'kind': kind.name,
    'title': title,
    'detail': detail,
  };
  factory CareRecord.fromJson(Map<String, dynamic> json) => CareRecord(
    id: json['id'] as String,
    date: DateTime.parse(json['date'] as String),
    kind: CareKind.values.byName(json['kind'] as String),
    title: json['title'] as String,
    detail: json['detail'] as String? ?? '',
  );
}

/// 관리 기록은 하나의 JSON에 저장하며 기존 개인 설정은 보존합니다.
class CareHistory {
  CareHistory(this.preferences);
  static const storageKey = 'care_history_v1';
  final SharedPreferences preferences;
  List<CareRecord> _records = [];
  List<CareRecord> get records => List.unmodifiable(_records);

  Future<void> load({Map<String, dynamic>? initialPersonal}) async {
    final stored = preferences.getString(storageKey);
    if (stored != null) {
      final decoded = jsonDecode(stored) as List;
      _records = decoded
          .map((e) => CareRecord.fromJson(Map<String, dynamic>.from(e as Map)))
          .toList();
      _sort();
      return;
    }
    final personalJson = preferences.getString('personal_setup_v1');
    final personal = personalJson == null
        ? initialPersonal ?? <String, dynamic>{}
        : Map<String, dynamic>.from(jsonDecode(personalJson) as Map);
    final migrated = <CareRecord>[];
    for (final kind in CareKind.values) {
      final raw = personal[kind.personalKey];
      final date = raw is String ? DateTime.tryParse(raw) : null;
      if (date != null && !isFutureCareDay(date)) {
        migrated.add(
          CareRecord(
            id: 'setup-${kind.name}',
            date: date,
            kind: kind,
            title: kind.label,
          ),
        );
      }
    }
    await _save(migrated);
  }

  DateTime? latest(CareKind kind) {
    for (final record in _records) {
      if (record.kind == kind) return record.date;
    }
    return null;
  }

  Future<void> put(CareRecord record) async {
    _validate(record.date);
    await _save([..._records.where((r) => r.id != record.id), record]);
  }

  /// 최종 관리일을 수정할 때 최신 기록을 갱신하고 중복 추가하지 않습니다.
  Future<void> setLatestDates(Map<CareKind, DateTime?> dates) async {
    final updated = [..._records];
    for (final entry in dates.entries) {
      if (entry.value == null || entry.value == latest(entry.key)) continue;
      _validate(entry.value!);
      final index = updated.indexWhere((r) => r.kind == entry.key);
      final old = index < 0 ? null : updated.removeAt(index);
      updated.add(
        CareRecord(
          id: old?.id ?? 'setup-${entry.key.name}',
          date: entry.value!,
          kind: entry.key,
          title: old?.title ?? entry.key.label,
          detail: old?.detail ?? '',
        ),
      );
    }
    await _save(updated);
  }

  void _validate(DateTime date) {
    if (isFutureCareDay(date)) {
      throw ArgumentError('미래 날짜에는 관리 기록을 등록할 수 없습니다.');
    }
  }

  void _sort() => _records.sort((a, b) => b.date.compareTo(a.date));
  Future<void> _save(List<CareRecord> updated) async {
    if (!await preferences.setString(
      storageKey,
      jsonEncode(updated.map((r) => r.toJson()).toList()),
    )) {
      throw StateError('관리 기록을 저장하지 못했어요.');
    }
    _records = updated;
    _sort();
  }
}
