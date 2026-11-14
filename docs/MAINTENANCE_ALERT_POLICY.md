# 정비·점검 알림 정책

2026-11-15 후속: [엔진오일 관리 정책](engine-oil/ENGINE_OIL_POLICY.md)이 엔진오일 주기·초기 추정·최종 교체 확인에 우선합니다. 기존의 전체 정비 경고 범위는 과거 인계안이며 이번 엔진오일 문서 갱신으로 모든 경고를 새로 구현·활성화하는 것은 아닙니다. 오일량·압력 정상화만으로 교체 완료를 만들지 않는 원칙은 유지합니다. 코드 변경 없음.

합의 범위: 차량의 정비·점검 경고 및 액량·워셔액 상태를 차량 앱에서 받아 Bluetooth로 휴대폰에 전달하고 로컬 알림을 생성합니다. 코드 구현·실차 검증 완료를 뜻하지 않습니다.

최신 [세부 처리안](CARE_IMPLEMENTATION_DETAILS.md)에 경고 그룹별 해석, 규칙표 필드, 활성화 보류 기준을 정리했습니다. 모든 경고의 차량별 지속시간·안전 문구를 이미 확인했다는 뜻은 아닙니다.

## 1. 대상

설치된 SDK의 실제 타입·권한·차량 capability로 재확인해야 합니다.

| 대상 | SDK 타입 후보 | 안내 범위 |
|---|---|---|
| 엔진 | ENGINE | 엔진·배출가스 계통 점검 |
| 엔진오일 압력 | ENGINE_OIL_PRESSURE | 오일 압력 이상 확인 |
| 엔진 온도 | ENGINE_TEMPERATURE | 과열 관련 확인 |
| 저전압 배터리·충전계통 | LOW_VOLT_BATTERY | 배터리·충전계통 점검 |
| ABS | ABS | 제동 보조 시스템 점검 |
| 전자식 주차 브레이크 이상 | ELECTRONIC_PARKING_BRAKE | 주차 브레이크 시스템 점검 |
| MDPS | MDPS | 조향 보조 시스템 점검 |
| TPMS | TPMS | 타이어 공기압·타이어 상태 확인 |
| ESC 이상 | ESC | 차체 자세 제어 시스템 점검 |
| 에어백 | AIRBAG | 에어백 시스템 점검 |
| 전조등 이상 | AFS, LED_HEADLAMP | 전조등 시스템 점검 |
| 입자 필터 | PARTICLE_FILTER | 해당 배출가스 계통 안내 확인 |
| SCR | SCR | 해당 배출가스·요소수 계통 안내 확인 |
| 수분 분리 | WATER_SEPARATOR | 해당 연료 계통 안내 확인 |
| 전기차 시스템 | EV_SYSTEM | 구동 시스템 점검 |
| 하이브리드 시스템 | HEV_SYSTEM | 구동 시스템 점검 |
| 수소전기차 시스템 | FCEV_SYSTEM | 구동 시스템 점검 |
| EREV 시스템 | EREV_SYSTEM | 구동 시스템 점검 |
| 수소 센서·누출 | H2_SENSOR, H2_LEAK | 해당 차량 안전·점검 안내 우선 |

별도 상태 API:

| 항목 | API 후보 | 처리 |
|---|---|---|
| 엔진오일량 | getEngineOilLevelState | 부족·매우 부족·과다 점검 |
| 브레이크액량 | getBrakeFluidLevelState | 액량 이상 점검 |
| 변속기 오일량 | getTransmissionOilLevelState | 액량 이상 점검 |
| 워셔액 | hasLowWasherFluidWarning | 부족 시 보충 안내 |

최종 합의는 네 가지 상태만이 아니라 위 **전체 정비 대상+네 가지 상태**입니다. 워셔액은 보충 안내로 별도 분류합니다.

## 2. 제외·해석

- 출력 제한, 일반 주차 브레이크, 방향지시등·조명 켜짐, 주행 모드는 정비 알림 제외.
- 통합 경고만으로 특정 고장 원인을 생성하지 않음.
- 오일 압력은 교체 수명 신호가 아님. 오일량 정상화도 교체 완료 증거 아님.
- 타입·원본 상태·색상·점멸 보존. ON이나 빨간색만으로 고장·조치 확정 금지.
- ESC 정상 개입/이상, EPB 정상 상태/이상, 시동 자체 점검/지속 이상 구분 필요.
- 공식 의미와 필요한 신호를 확보 못 하면 해당 자동 정비 알림 활성화 보류. 원인 추측 금지.
- 항목별 상태 의미·심각도·필터 근거를 공식 차량 안내와 대조. 모든 경고에 같은 지연 필터 적용 금지.
- 엔진 온도·오일 압력·수소 누출 등에 '정비소까지 운전'을 일괄 권고하지 않음. 공식 조치 확인 후 적용.
- 상태 ERROR, 미지원, 권한 거부, 미수신, 단절은 정상·부족·해제로 변환하지 않음.
- 경고 해제는 해제일 뿐 정비 완료가 아님. 정상 복귀 기록만 생성.

## 3. 통신·알림 공통

- 차량 앱: 실제 SDK 수집, 지원·권한 확인, 허용 구독 우선. 특권 권한 우회 금지.
- 공개 조회만 가능하면 실행 상태·서비스 정책에 맞춰 조회. 전경 조회를 상시 수신이라고 표현하지 않음.
- 휴대폰 앱: 상태·이력·알림 판단. Firebase 추가 없이 Bluetooth 수신 후 로컬 알림.
- 휴대폰 앱이 최종 설정·관리기록·계산 결과의 기준. 차량 앱 변경은 요청으로 전달하고 휴대폰이 버전을 부여해 회신. 최초 기존 설정 가져오기·오프라인·버전 충돌 처리는 세부 처리안에 따름.
- RFCOMM/BLE는 Pleos 지원·권한 확인 후 선택. 음악·통화 페어링만으로 앱 데이터가 전송된다고 가정 금지.
- 연결 대상 확인, Android 권한·백그라운드 정책 준수. 원본 VIN 불필요 노출 금지.
- 메시지: 프로토콜 버전, 차량 연결 ID, 세션·메시지 ID, 순번, 항목, 원본·정규화 상태, 관측 시각, 실제/시험 출처.
- ACK·재전송·중복 제거·역순·지연 처리, 제한된 미전송 저장, 재연결 최신 상태 동기화.
- 최초 유효 이상 한 번, 지속 동일 상태 반복 금지, 악화 알림 허용, 정상 후 재발 새 알림.
- 과거에 발생 후 해제된 이벤트는 재연결 때 현재 경고처럼 발송 금지. 이력은 보존.
- 알림 채널·OS 권한 처리. 거부 시 앱 내부에서 상태 확인 가능.
- 기존 캐릭터·색상 규칙 재사용. 단색 small icon과 컬러 large icon 용도 구분.
- 블루투스 범위 밖에서 실시간 전달 보장 금지. 최신 상태와 수신 시각 관리.

## 4. 참고 공식 문서

- [Pleos Vehicle SDK](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/intro)
- [Safety API](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/safety/entry)
- [Android Bluetooth 연결](https://developer.android.com/develop/connectivity/bluetooth/connect-bluetooth-devices)
- [Bluetooth 권한](https://developer.android.com/develop/connectivity/bluetooth/bt-permissions)

문서·설치 SDK·실차가 다르면 실제 지원 범위를 기록하고 규칙을 재검토합니다.
