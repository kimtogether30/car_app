# 정비·세차 기준 인계 문서

2026-11-11 후속 구현: [주행 구간·시간별 날씨·점수 연결](DRIVING_WEATHER_IMPLEMENTATION.md). 데이터 연결 코드와 시험 범위/실수신 미검증을 구분한다. 최초 인계 문서의 ‘주행 날씨 미구현’ 표기보다 이 후속 문서를 우선한다.

2026-11-11 추가: [7일 예보 조회 기능·주행 날씨 연결 인계](WEATHER_INTEGRATION_HANDOFF.md). 공통 일별 예보 조회만 코드 추가, 주행 구간/시간별 날씨 연결은 친구 구현 대상. 추천·화면·화면 갱신 정책은 보류. 최신 거리 동일 처리와 날씨 연결 기준은 해당 문서를 우선한다.

2026-11-10 추가 합의: [HEV 주유·충전·재알림 기준](HEV_FUEL_ALERT_POLICY.md). 일반 HEV는 정수 연료 잔량과 사용자 임계값만 사용하며 SOC·차량LOW_FUEL 경고로 우회하지 않습니다. PHEV·수소차는 지원 목록 제외. 재알림 회복은 임계값+10%p 이상을 최소30초 간격의 별도SDK조회 성공3회로 확인합니다. **기준 문서만 추가했으며 이 정책의 코드 적용·실수신 검증 완료를 뜻하지 않습니다.** 최신 모듈은 `app/`·`phone/`이며 아래의 과거 `mobile-app/` 표기는 당시 코드 기록입니다.

2026-11-10 사용자 재확인: 시설별 업로드 기준을 충족하면 먼저 세차로 반영한다. 전용 셀프 세차장/주유와 명확히 분리된 세차 구역은 전체 조건 충족 후 ‘세차 완료!’, 구분이 애매한 적격 방문은 ‘세차한 것 같아!’ / ‘세차 안 했어요’로 취소 가능. P 기반 정차식에 N단 조건을 강제하지 않는다. 시설 입장만으로 완료하지 않는다. 나중에 실행할 Bluetooth 검증은 [일괄 목록](BLUETOOTH_VALIDATION_CHECKLIST.md)에 모은다. [배너 구현·검증](BANNER_NOTIFICATIONS.md)을 참고한다.

작성일/세부안 갱신일: 2026-11-09 · 확인한 앱 코드: `0c51dbb` · **합의된 제품 정책·최신 세부 처리안 및 구현·검증 지침**

이 문서는 친구 PC에서 기준을 읽고 구현·시뮬레이션하도록 전달하는 자료입니다. 이번 변경은 문서만 추가하며 앱 동작을 변경하지 않습니다. 문서의 정책은 구현 완료나 실차 수신 성공을 뜻하지 않습니다.

## 읽는 순서

1. 이 문서: 범위와 코드 연결 위치
2. [세차 계산·감지 기준](WASH_POLICY.md)
3. [정비 경고 기준](MAINTENANCE_ALERT_POLICY.md)
4. [세부 처리·초기 시험값·검증 상태](CARE_IMPLEMENTATION_DETAILS.md)
5. [수신 검증·시뮬레이션 체크리스트](CARE_VALIDATION_PLAN.md)
6. [HEV 주유·충전·회복 예외·시뮬레이션](HEV_FUEL_ALERT_POLICY.md)

이 문서 묶음은 첫 문서 저장 이후 논의한 위치 시험값, 180초 정의, 중복 기록 처리, 휴대폰 기준 설정 동기화, 장기 계산 장부, 경고별 규칙표까지 반영합니다. 앱 소스가 그대로인 것과 문서 정책이 최신인 것을 구분합니다.

## 문서 우선순위와 확정 수준

- WASH_POLICY와 MAINTENANCE_ALERT_POLICY: 적용할 제품 정책.
- CARE_IMPLEMENTATION_DETAILS: 최신 구체화 방안과 초기 시험 프로필. 기존 문서의 '구현 전 결정 필요' 항목을 이 문서로 구체화합니다.
- CARE_VALIDATION_PLAN: 성공을 주장하기 전 실행할 검사. 체크되지 않은 항목은 미검증.
- '초기 시험값'은 누락된 결정이 아니라 시험에 사용할 설정이며, 실제 수신 검증 전 정확도 보장이나 실차 적용 완료를 의미하지 않습니다.
- 정비 경고별 모델별 지속시간·안전 문구는 공식 의미와 필요한 신호 확인 없이 임의로 채우지 않습니다. 미확인 항목은 자동 정비 알림 활성화 보류로 처리합니다.

친구 PC에서는 원래 작업을 보존한 후 main의 최신 문서를 받습니다.

```powershell
git switch main
git pull --ff-only origin main
```

로컬 변경 때문에 전환·pull이 막히면 강제 초기화하지 말고 먼저 커밋 또는 백업합니다. Android Studio에서 저장소 루트를 열고 `docs/CARE_CRITERIA_HANDOFF.md`부터 읽습니다.

## 범위

- `app/`: Pleos 차량 앱, 차량 신호·위치·방문 근거 수집.
- `phone/`: Android 휴대폰 앱, 설정·관리기록·계산 결과의 최종 기준, 전달받은 상태·기록 기반 로컬 알림. 차량 앱은 데이터 수집과 설정 사본 보관.
- 차량→휴대폰은 Bluetooth 연결을 목표로 하되 실제 Pleos 지원·권한을 먼저 검증합니다.
- 세차 점수, 관리기록 반영, 세차 자동 추정·취소, 정비 경고 전달이 대상입니다.
- 엔진오일 교체 추정·공식 교체 주기 기반 수명 퍼센트는 이번 정비 알림 범위에서 제외합니다.
- 기존 주유·충전 기능, 캐릭터·색상 규칙, 관리기록을 보존합니다. 사용자 데이터 자동 삭제 금지.

## 현재 코드와 정책의 차이

최신 main을 읽어 확인한 위치입니다. 친구가 구현할 때 다시 확인하십시오.

| 파일 | 현재 역할 / 변경 시 주의 |
|---|---|
| `app/src/main/java/com/example/vemoring/Data.kt` | `CarProfile.washAlert` 기본 30, `notifications`, 날짜 기반 `CareRecord`, 설정·기록 저장 |
| `app/src/main/java/com/example/vemoring/MainActivity.kt` | 세차 알림 기준 5~50, 5 단위 설정. 기존 설정을 재사용 |
| `app/src/main/java/com/example/vemoring/CareCondition.kt` | 홈·캐릭터용 관리 상태 계산과 washAlert 비교. 새 계산과 일관성 유지 |
| `app/src/main/java/com/example/vemoring/integration/IntegrationState.kt` | 기존 `CareEstimator.cleanliness`: 날짜·거리·현재 대기질 계산. 새 정책과 다르므로 이중 차감 금지 |
| `app/src/main/java/com/example/vemoring/integration/MaintenancePanel.kt` | 별도 관리 추정 화면·거리 기준 연결. 홈과 계산 결과가 갈라지지 않게 단일 계산기 사용 |
| `app/src/main/java/com/example/vemoring/integration/ExternalData.kt` | 날씨·대기질·OSM 장소 조회. 기존 요약값만으로 구간별 이력 계산·세차 경계를 확보했다고 주장 금지 |
| `app/src/main/java/com/example/vemoring/vehicle/PleosVehicleGateway.kt` | SDK 조회·구독·상태 처리. 경고 전체 및 변속기 액량 연결 여부 재확인 |
| `phone/` | 휴대폰 앱 모듈. 2026-11-10 merge에서 시작 화면 뼈대였던 `mobile-app/`을 대체함. 실기기 Bluetooth 검증은 미완료 |

`CareCondition`과 `MaintenancePanel` 등 모든 화면에서 동일한 계산·기록을 사용해야 합니다. 기존 하루 2점 날짜 차감을 새 평균 20km/일 추정과 함께 적용하면 중복 차감입니다.

## 구현 순서

1. 실제 SDK·휴대폰 위치·Bluetooth의 수신 가능성을 먼저 기록.
2. 기록·시간 구간·메시지 식별자와 단일 계산기 설계.
3. 계산 정책과 기록 수정·취소 재계산 구현.
4. 실제 지도 정보에 맞춘 세차 감지 및 휴대폰 위치 보완.
5. 기존 알림 설정을 사용하는 휴대폰 알림 연결.
6. 시험 데이터 검증 → 에뮬레이터 SDK 수신 → 실기기 전송을 구분해 검증.

수신 불가 항목은 가짜 정상값으로 대체하지 않습니다. 미검증 내용은 체크리스트에 남기고, 정책이나 임계값 변경은 변경 이유와 함께 기록합니다.
