# 정비·세차 기준 인계 문서

작성일: 2026-11-09 · 기준 코드: `0c51dbb` · **합의된 제품 정책 및 구현·검증 지침**

이 문서는 친구 PC에서 기준을 읽고 구현·시뮬레이션하도록 전달하는 자료입니다. 이번 변경은 문서만 추가하며 앱 동작을 변경하지 않습니다. 문서의 정책은 구현 완료나 실차 수신 성공을 뜻하지 않습니다.

## 읽는 순서

1. 이 문서: 범위와 코드 연결 위치
2. [세차 계산·감지 기준](WASH_POLICY.md)
3. [정비 경고 기준](MAINTENANCE_ALERT_POLICY.md)
4. [수신 검증·시뮬레이션 체크리스트](CARE_VALIDATION_PLAN.md)

친구 PC에서는 원래 작업을 보존한 후 main의 최신 문서를 받습니다.

```powershell
git switch main
git pull --ff-only origin main
```

로컬 변경 때문에 전환·pull이 막히면 강제 초기화하지 말고 먼저 커밋 또는 백업합니다. Android Studio에서 저장소 루트를 열고 `docs/CARE_CRITERIA_HANDOFF.md`부터 읽습니다.

## 범위

- `app/`: Pleos 차량 앱, 차량 신호·위치·방문 근거 수집.
- `mobile-app/`: Android 휴대폰 앱, 전달받은 상태·기록 기반 계산과 로컬 알림.
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
| `mobile-app/` | 현재 별도 휴대폰 앱 모듈. 기준 코드에서는 시작 화면 위주이며 통신·알림 완료 상태 아님 |

`CareCondition`과 `MaintenancePanel` 등 모든 화면에서 동일한 계산·기록을 사용해야 합니다. 기존 하루 2점 날짜 차감을 새 평균 20km/일 추정과 함께 적용하면 중복 차감입니다.

## 구현 순서

1. 실제 SDK·휴대폰 위치·Bluetooth의 수신 가능성을 먼저 기록.
2. 기록·시간 구간·메시지 식별자와 단일 계산기 설계.
3. 계산 정책과 기록 수정·취소 재계산 구현.
4. 실제 지도 정보에 맞춘 세차 감지 및 휴대폰 위치 보완.
5. 기존 알림 설정을 사용하는 휴대폰 알림 연결.
6. 시험 데이터 검증 → 에뮬레이터 SDK 수신 → 실기기 전송을 구분해 검증.

수신 불가 항목은 가짜 정상값으로 대체하지 않습니다. 미검증 내용은 체크리스트에 남기고, 정책이나 임계값 변경은 변경 이유와 함께 기록합니다.
