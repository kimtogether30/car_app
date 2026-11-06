/// 차량 종류에 따라 연료와 배터리 항목의 표시 여부를 결정합니다.
enum VehicleType { combustion, electric, hybrid }

enum VehicleDataSource { example, pleos, unavailable, unsupported }

/// 에너지 관리(feature_a)와 세차 관리(feature_b)가 함께 사용하는 차량 상태.
///
/// 두 기능에서 별도 상태를 만들지 않고 같은 차량 상태를 전달받아 사용합니다.
class VehicleStatus {
  const VehicleStatus({
    required this.vehicleName,
    required this.licensePlate,
    required this.vehicleType,
    this.fuelLevelPercent,
    this.batteryLevelPercent,
    this.cleanlinessPercent,
    this.characterMessage = '차량 상태를 확인하고 있어요.',
    this.batteryDataSource = VehicleDataSource.example,
  }) : assert(
         fuelLevelPercent == null ||
             (fuelLevelPercent >= 0 && fuelLevelPercent <= 100),
       ),
       assert(
         batteryLevelPercent == null ||
             (batteryLevelPercent >= 0 && batteryLevelPercent <= 100),
       ),
       assert(
         cleanlinessPercent == null ||
             (cleanlinessPercent >= 0 && cleanlinessPercent <= 100),
       );

  /// 차량 모델명 또는 사용자가 정한 별명.
  final String vehicleName;

  /// 문자와 공백을 포함하는 차량 번호.
  final String licensePlate;

  /// 내연기관: 연료, 전기: 배터리, 하이브리드: 둘 다 표시합니다.
  final VehicleType vehicleType;

  /// 연료 잔여량(0~100%). 미수신 또는 해당 없음은 null입니다.
  final double? fuelLevelPercent;

  /// 배터리 잔여량(0~100%). 미수신 또는 해당 없음은 null입니다.
  final double? batteryLevelPercent;

  /// 청결도(0~100%). 높을수록 깨끗하며, 미수신은 null입니다.
  final double? cleanlinessPercent;

  /// 공통 상태 판단 로직에서 결정할 캐릭터 멘트.
  final String characterMessage;

  /// 배터리 값이 실제 SDK 값인지, 예시인지, 또는 미수신 상태인지 나타냅니다.
  final VehicleDataSource batteryDataSource;
}
