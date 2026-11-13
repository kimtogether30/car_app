# VEMORING Pleos 통합 구조

기준: origin/main 1d49158 Kotlin / Compose, feature/pleos-sdk-integration worktree.
기존 Flutter 공동 작업 폴더와 main을 변경하지 않음. 전체 결과는 [SDK_FEATURE_MATRIX.md](SDK_FEATURE_MATRIX.md),
다른 PC 실행은 [OTHER_PC_TESTING.md](OTHER_PC_TESTING.md).

## 데이터 경계

- Data.kt: 기존 CarProfile/CareRecord/GarageStore, garage_v1 원자적 저장 및 Flutter 최초 가져오기 그대로 보존.
- vehicle/VehicleState.kt: ReadingState(0/NoData/Unsupported/PermissionDenied/Disconnected/Error/Demo), 값/수신 시각, StateFlow Repository.
- vehicle/PleosVehicleGateway.kt: 실제 Vehicle 2.3.2 capability READ/READ_WRITE 확인 후 조회.
  연료/SOC/거리/일부 안전 경고 구독. public telltale 조회는 foreground에서 30초마다 갱신.
- vehicle/VehicleViewModel.kt: 등록 완료 + foreground에서만 연결. Real/Demo 변경 시 Repository 교체, 이전 세션 콜백 차단.
- integration/PleosLocationGateway.kt: FusedLocation 1.1.3 지원/권한/생명주기/마지막 위치/구독/근접 영역/차단 안내.
- integration/PleosNavigationGateway.kt: NaviHelper 3.0.5 실제 public API + 이벤트.
- integration/ExternalData.kt: IO 스레드에서 사용자 조회만 수행. Open-Meteo/CAMS 및 Overpass GET.
- integration/IntegrationViewModel.kt: 위치/날씨/장소/내비 StateFlow, 출처, 수신 시각, 최대 100개 메모리 진단 로그.
- integration/IntegrationScreen.kt: 주차 확인, 위치 권한, 영역 반경, 외부 조회, 장소 선택, 내비 변경 확인, 필드별 진단.
- integration/MaintenancePanel.kt: 별도 vemoring_sdk_care_v1 저장, 차량 프로필별 설정 및 기록 ID+날짜별 거리 연결.
- integration/IntegrationState.kt: Real/Demo 모델, 좌표 유효성/최신성, 거리 계산, 명시적 Demo Gateway, 관리 추정 함수.

기존 자동차 Canvas 디자인은 유지하고 에너지 부족/안전 경고 시 우려 표정만 추가.
안전 경고 메시지가 일반 관리/에너지 메시지보다 우선.
내연기관은 연료, EV는 SOC, 하이브리드는 두 잔량 표시. 파워트레인은 User Input.

## 실제 API 대응

| 도메인 | 실제 호출 | 제한 |
|---|---|---|
| CarInfo | checkVINInfoCapabilities/getVINInfo, checkCRNInfoCapabilities/getCRNInfo | 식별자는 즉시 마스킹, CRN deprecated. 제조사/차종/연식 getter 미확인 |
| Fuel | checkFuelPercentCapabilities/getFuelPercent, registerFuelPercent, getCurrentFuelEconomy | 연비 단위 미확인 |
| EvBattery | getEvBatteryStateOfCharge/registerEvBatteryStateOfCharge, getCurrentEvEfficiency/getEvChargeSwitchState | 충전 스위치 != 실제 충전 진행. 전비 단위 미확인 |
| HVAC | 온도(CELSIUS), 현재 온도, AC/AUTO/풍량/송풍 방향/내기/청정 getter | capability가 제공하는 첫 좌석 영역, 제어 없음 |
| Odometer | getCurrentDistanceDriven/registerDistanceDriven, getTripCurrentDrivingDistance/getTripCurrentDrivingTime | 거리/시간 단위 자동 확인 불가 |
| Safety | getEngineOilLevelState/getBrakeFluidLevelState/hasLowWasherFluidWarning/getTelltalesInfo | 스마트키 배터리 미지원. telltale capability/listener는 PRIVATE_CAR_SAFETY라 사용하지 않음. getter도 2.3.2 내부 구현 권한 불일치로 거절됨: SDK_LOCATION_DIAGNOSIS_2026_11_13.md |
| FusedLocation | isSupported/initialize/release/getLastKnownLocation/registerFusedLocationCallback/unregisterFusedLocationCallback | 오래되거나 잘못된 위치를 외부 조회에 사용하지 않음 |
| FusedLocation | addProximityAlert/removeProximityAlert/showLocationAccessBlockedPopup | SDK 차단 안내는 권한 허용 기능이 아님 |
| NaviHelper | initialize/addListener/removeListener/release | 초기화 완료 콜백 이후 조회 |
| NaviHelper | getDestinationInfo/getWaypointInfo/getRouteStateInfo/getCurrentLocationInfo/getTBTInfo | 실제 콜백만 수신으로 기록 |
| NaviHelper | requestRoute/cancelRoute/changeRouteOption/requestReRoute | 각각 사용자 확인 필요 |
| NaviHelper | addWaypoint/removeWaypoint/requestRouteWithWaypoints/requestNavigationScreen | 기존 목적지 보존 제안, 경유지 최대 2개, 신규 경로는 변경 경고 |

Vehicle 배포 예외가 난독화되어 VehicleException a()/ErrorCode mapper는 이 버전에 한정.
AAR public signature와 컴파일/Android lint로 메서드 및 요구 권한을 대조.
Safety registerTelltales에 일반 CAR_SAFETY만으로 접근할 수 있다는 기존 추정을 lint에서 수정함.
PRIVATE_CAR_SAFETY를 선언하거나 권한 검사를 우회하지 않음.

## 설정 / 개인정보

의존성: Vehicle 2.3.2 / vehicle-data 1.1.11 / FusedLocation 1.1.3 / fused-location-data 0.0.3 /
NaviHelper 3.0.5 / navi-data 3.0.4, 공식 Maven.
권한: CAR_INFO/CAR_ENERGY/CAR_CLIMATE/CAR_DRIVE/CAR_SAFETY/FUSED_LOCATION/NAVI_ROUTE/NAVI_ROUTE_SEARCH/NAVI_CUSTOM_MAP 및 INTERNET.
FUSED_LOCATION은 런타임 요청. 모든 오류를 Unsupported로 처리하지 않음.

외부 위치 전송은 사용자가 두 서비스에 명시 승인한 범위:
api.open-meteo.com, air-quality-api.open-meteo.com, overpass-api.de. 조회 버튼에도 전송 목적 표시.
API 키/외부 백엔드 없음. 무료/비상업 조건과 배포 라이선스는 별도 확인.
VIN/CRN, 위치 좌표, 외부 원문 응답/예외·비밀키는 진단 로그에 출력하지 않음.
장소 좌표는 사용자가 요청한 검색 결과 UI에 표시.
위치·장소·날씨·SDK 수신은 영구 저장하지 않음. 기존 관리 기록과 명시적 단위/거리 설정만 로컬 저장.
실시간 위치는 화면 종료/백그라운드에서 해제. SDK in-flight 외부 HTTP는 제한 시간까지 종료될 수 있으나 취소된 세션 응답은 반영하지 않음.
앱 재실행은 Real, 위치 추적 자동 시작 없음.

## 계산

청결도는 날짜/확인된 거리/모델 대기질 기반의 제품 추정, 실측 아님.
강수 예보는 관측 강수로 사용하지 않음. 관측 강수 API 자동 연결은 미구현.
roadWeight 인자로 도로환경 확장이 가능하나 해안/흙먼지 자동 분류는 미구현.
엔진오일 교체 주기는 사용자가 설명서로 확인한 주기와 교체 당시 km를 입력한 경우만 계산.
거리 단위를 추측하지 않으며 테스트 담당자가 정상 PC에서 확인한 단위 입력이 필요.
Demo 숫자를 실제 관리 거리 계산/기존 기록 저장에 쓰지 않음.
관리 기록 날짜 또는 최신 기록이 바뀌면 기존 ID+날짜 거리 연결은 무효.
오늘 관리 기록에만 현재 실제 수신 거리를 명시적으로 연결할 수 있음.

## 공식 자료

- https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/intro
- https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/FusedLocation/intro
- https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/FusedLocation/declaring-permissions
- https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/intro
- https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/declaring-permissions
- https://open-meteo.com/en/docs
- https://open-meteo.com/en/docs/air-quality-api
- https://wiki.openstreetmap.org/wiki/Overpass_API
