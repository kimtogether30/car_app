# SDK 연동 결과 (2026-11-08)

2026-11-15 후속 안내: 아래는 당시 구현·검증 기록입니다. 현재 교체주기 데이터는 [별도 폴더](engine-oil/README.md), 새 엔진오일 목표 정책은 [관리 기준](engine-oil/ENGINE_OIL_POLICY.md)에 있습니다. 당시 “DB 없음·사용자 입력만 계산” 기록을 새 정책의 금지 조건으로 해석하지 않습니다. 정책 문서 추가는 실제 SDK OFF·ON 수신 또는 새 계산 구현 성공을 뜻하지 않습니다.

코드/빌드 검증과 실데이터 검증을 구분합니다. 이번 작업에서 현재 PC의 Pleos 에뮬레이터를 실행하지 않았습니다.

| 기능 | 코드 구현 | 빌드 검증 | 실제 SDK 검증 | 비고 |
|---|---|---|---|---|
| CarInfo | VIN/CRN capability/get, 즉시 마스킹 | 통과 | 미검증 | CRN은 SDK deprecated. 제조사/모델/연식은 사용자 입력 |
| Fuel/EV | 잔량, 연비/전비, 충전 스위치, 잔량 구독 | 통과 | 미검증 | 충전 스위치와 충전 진행 상태를 동일시하지 않음 |
| HVAC | 영역 온도/AC/AUTO/풍량/송풍 방향/순환/청정 조회 | 통과 | 미검증 | 제어 없음, 첫 지원 영역 이름 표시 |
| Odometer | 누적/현재 트립 거리·운행 시간 | 통과 | 미검증 | 단위 자동 검증 불가. 원시 수치 진단 + 사용자 확인 단위 |
| Safety | 엔진오일/브레이크액/워셔액/연료/오일압 경고 | 통과 | 미검증 | 스마트키 배터리 API 미확인으로 Unsupported |
| FusedLocation | 지원/권한/초기화/조회/구독/중지/차단 안내/영역 등록·해제 | 통과 | 미검증 | 기능 사용 시만 위치 접근, 최신 위치 조건 |
| NaviHelper | 목적지/경유지/옵션/재탐색/경로·주행 이벤트/화면 요청 | 통과 | 미검증 | 확인 후 변경. 요청 전송과 콜백 성공 구분 |
| 날씨/대기질 | Open-Meteo/CAMS 기온·강수 예보·PM/dust | 통과 | SDK 대상 아님 | 공개 테스트 좌표에서 실제 HTTP 응답 확인. Android 기기 경로 미검증 |
| 주변 장소 | Overpass 실제 4종류 OSM 장소 검색 | 통과 | SDK 대상 아님 | 공개 테스트 좌표 GET 응답 260 elements. Android 기기 경로 미검증 |
| 청결도 | 날짜/확인 거리/모델 PM·dust 추정 | 통과 | 실제 추정 정확도 미검증 | 실측 아님. 일반 관리 참고치 |
| 엔진오일 주기 | 관리 당시 km + 사용자 설명서 주기 | 통과 | 미검증 | 별도 SharedPreferences, 기존 기록 보존 |
| Real/Demo/진단 | 출처/수신시각/상태/요청·콜백 로그 | 통과 | 장치 UI 미검증 | 가상 Gateway와 실제 Gateway 분리 |
| 실수신 테스트 | 값/콜백 없으면 실패하는 3개 테스트 | 계측 APK 통과 | 실행하지 않음 | 정상 PC용 |

## 미지원 / 미구현 / 제한
- 제조사/차종/연식/파워트레인 자동 취득, 스마트키 배터리 경고: 이 SDK public API에서 미확인.
- 충전 스위치 조회를 '현재 충전 중'이라고 표시하지 않음.
- 연비/전비/트립 시간/풍량 단위는 확인 전 수치 사용 보류.
- 누적 거리 단위는 API getter 미확인. 자동 단위 성공을 주장하지 않으며 테스트 담당자의 확인 입력이 필요.
- 관측 강수량 API 자동 연동, 관측소/시간대/주행 이력별 강수 보정: 미구현.
  CareEstimator는 관측 강수 입력 인터페이스를 가지지만 예보를 넘기지 않음.
- 해안/흙먼지 지역 자동 판정 미구현. roadWeight 확장 인자만 제공.
- CAMS dust는 황사 특보/현장 황사 센서가 아님.
- 제조사 공식 정비 주기 DB/엔진오일 상태 진단 없음. 사용자가 확인한 주기로만 계산.
- 장소 영업 여부/충전기 실시간 가용 상태는 제공하지 않음. OSM 누락·갱신 지연 가능.
- 주행 중 잠금은 사용자 주차 확인으로 제한한 진단 UI. 실제 속도 기반 자동 잠금은 미구현.
- Vehicle 서비스가 영구히 콜백을 반환하지 않는 경우 기존 어댑터의 release 지연 한계가 남음.
- 외부 HTTP 호출은 작업 스레드, 제한 시간/HTTP 오류 표시, 반복 자동 호출 없음.
- SDK의 동시 외부 경로 변경을 원자적으로 검사하는 API가 없어 최종 안내 확인 필요.
- 로컬 JVM 테스트 통과가 실제 서비스/계측 UI 테스트 통과를 대체하지 않음.

## 외부 서비스 / 키
- 사용자 승인: 조회 시 현재 위도·경도를 api.open-meteo.com, air-quality-api.open-meteo.com, overpass-api.de로 전송.
- API 키 없음. Kakao 비밀키를 APK에 넣는 방안은 사용하지 않음.
- 날씨: https://open-meteo.com/en/docs
- 대기질: https://open-meteo.com/en/docs/air-quality-api
- 장소: https://wiki.openstreetmap.org/wiki/Overpass_API
- OSM 출처/라이선스: https://www.openstreetmap.org/copyright
- Pleos 공식 FusedLocation 1.1.3 / NaviHelper 3.0.5 / Vehicle 2.3.2와 배포 AAR public signature 대조.
- 외부 응답의 위치/차량 식별자/API 키/원문 예외를 진단 로그에 기록하지 않음.

## 변경 파일
기존 파일:
- app/build.gradle.kts
- app/src/main/AndroidManifest.xml
- app/src/main/java/com/example/vemoring/MainActivity.kt
- app/src/main/java/com/example/vemoring/CarCharacter.kt
- README.md (실행 안내 링크)
- .gitignore (로컬 설정/키/생성 파일 제외)

신규/이전 Vehicle 단계에서 추가한 뒤 이번에 확장한 파일:
- vehicle/VehicleState.kt, PleosVehicleGateway.kt, VehicleViewModel.kt, VehicleDetails.kt
- integration/IntegrationState.kt, PleosLocationGateway.kt, PleosNavigationGateway.kt
- integration/IntegrationViewModel.kt, IntegrationScreen.kt, ExternalData.kt, MaintenancePanel.kt
- app/src/test/java/com/example/vemoring/IntegrationTest.kt
- app/src/test/java/com/example/vemoring/VehicleStateTest.kt (이전 단계)
- app/src/androidTest/java/com/example/vemoring/RealSdkReceptionTest.kt
- app/src/androidTest/java/com/example/vemoring/VehicleGatewaySmokeTest.kt (이전 단계)
- docs/PLEOS_INTEGRATION.md, PLEOS_VALIDATION.md, OTHER_PC_TESTING.md, SDK_FEATURE_MATRIX.md
- tools/New-PortableSource.ps1

친구의 기존 Data.kt/Setup.kt/VehicleCatalog.kt/PleosVehicle.kt는 수정하지 않음.
feature 브랜치 commit/push 및 PR 검토 대상. main 병합은 별도 승인 후.
