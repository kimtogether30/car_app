import '../../shared/models/vehicle_status.dart';

/// 주행거리는 요청된 멘트용 예시이며 실제 주행거리 예측이 아닙니다.
({String message, int? exampleRangeKm}) caronCondition(
  VehicleStatus vehicle, {
  required double fuelThreshold,
  required double washThreshold,
}) {
  final fuel = vehicle.fuelLevelPercent;
  final clean = vehicle.cleanlinessPercent;
  if (fuel == null || clean == null) {
    return (message: '연료와 청결도 정보를 확인하고 있어요.', exampleRangeKm: null);
  }
  // 이상·이하가 겹치는 경계값은 관리가 필요한 '이하'로 처리합니다.
  final hungry = fuel <= fuelThreshold;
  final dirty = clean <= washThreshold;
  if (hungry && dirty) {
    return (
      message: '배도 고프고 씻고도 싶어! 50km 더 달릴 수 있어. 주유랑 세차 둘 다 부탁해!',
      exampleRangeKm: 50,
    );
  }
  if (hungry) {
    return (
      message: '슬슬 배가 고파지네! 85km 더 달릴 수 있지만, 주유가 필요해!',
      exampleRangeKm: 85,
    );
  }
  if (dirty) {
    return (
      message: '달릴 준비는 됐는데, 몸이 좀 찝찝해! 300km 더 달릴 수 있지만 세차 한번 부탁해!',
      exampleRangeKm: 300,
    );
  }
  return (
    message: '컨디션 최고! 지금처럼 쭉 달려보자! 앞으로 420km 더 달릴 수 있어.',
    exampleRangeKm: 420,
  );
}
