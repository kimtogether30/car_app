# 차속·정차 검증 기록 (2026-11-12)

대상: main `26e4305` (`git pull --ff-only`: Already up to date).
공식 Pleos Connect v2.1.0 / API34, Cold Boot, 공식 프로필 RAM4096MB/2560×1440 유지.
실행 사용자10. 기존 차량·휴대폰 앱 삭제 및 데이터 초기화 없음. 에뮬레이터는 차량 한 대만 실행.

## 코드 및 단위 검증

- `VehicleMotionTest`: 5개 통과. 최신 0+P=Parked, 0+D/N/R=Stopped, 양/음수 및 ±0.001m/s=Moving. 오류·누락·미지원·5초 초과 차속=Unknown. 오래된 P(35초 초과)는 주차 근거가 아님.
- 차속 구독은 AAOS PERF_VEHICLE_SPEED / SENSOR_RATE_NORMAL. 실제 간격 보장값이 아님. 이전과 같은 값이어도 측정 시각이 새로우면 갱신되며 중복 시각은 버림.
- 기어는 Pleos 이벤트 구독과 30초 getter 경로. 두 번째 부팅에서 차속 갱신 간격은 측정했으나 기어 변경 주기 및 0 주변 비영 흔들림은 미검증.
- 표시 속도는 km/h 소수점 한 자리, 판정은 원시 m/s 정확한 0 기준. 따라서 작은 비영 값은 화면상 0.0 km/h + 이동 중일 수 있음. 임의의 데드밴드는 추가하지 않음.
- `:app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest`: 성공.
- 공유 세차 테스트는 별도 companion 모듈이 아니라 phone sourceSets에 포함됨. `:phone:testDebugUnitTest` 성공. `CarePolicyTest` 18개, `FacilityWashPolicyTest` 6개 통과.

## 최종 실제 수신 결과

빌드 및 Gradle 데몬 종료 후 기존 AVD를 다시 Cold Boot하니 Car Service 정상 연결. 동일 계측 재실행 결과 `OK (2 tests)` (25.761초).
실제 PERF_VEHICLE_SPEED=0m/s 및 Pleos GEAR_PARK 수신, motion=Parked 확인. sourceCloseStopsPublishing도 통과.
12.1초 동안 유효 차속 표본83개, 측정시각 차이99~202ms(평균147.56ms), 원시값은 모두0m/s. 같은 값의 새 표본 수신을 확인했으며 이 짧은 구간의 수치를 SDK 주기 보장으로 해석하지 않음.

공식 콘솔은 계속 DDS bridge_ready=false. 공식 UI가 사용하는 PUT /vehicle/gear?gear=Neutral은 `emulator_inactive` 오류로 거부됨. D/N/R, 비영/후진, 0 근접 비영 흔들림, 유효 수신 후 5초 중단 및 P 표본만35초 중단은 실제 검증하지 못함. 초기 기어 수신 전 잠시 Stopped였던 것은 D/N/R 수신 검증이 아님.

근거: retry-instrumentation.log, retry-signals.log, retry-carservice.log, sample-statistics.json, console-gear-attempt.log.

## 실제 수신: 첫 Cold Boot (아래 실패는 재부팅 후 해소)

계측 APK 설치 성공. 현재 사용자10에 CAR_SPEED 권한을 부여한 뒤 `am instrument --user 10` 실행.
`Tests run: 2, Failures: 2`. 실제 차속 Disconnected(연결 시간 초과), 기어 Error(응답 시간 초과). 이 상태의 motion은 Unknown이며 0/정차로 대체되지 않았음.
`sourceCloseStopsPublishing`은 유효 값 수신 사전조건에서 실패했으므로 종료 후 콜백 검증을 통과한 것이 아님.

CarServiceHelper에 `carwatchdog killed com.android.car` / `CarService crash` 기록. `service check car_service`: not found.
공식 Virtual Console HTTP 연결은 성공했지만 초기 상태는 gear.bridge_ready=false, controls.dds_ready.all=false. 콘솔의 기본 speed_kph=0은 실제 차량 수신 증거가 아님.

## 세차 연결과 정책 차이

차속·정차 결과는 `VehicleCareRuntime`의 motion payload에 연결돼 있음. 운영 `VehicleWashRuntime`은 위치 방문 경로이며 motion만으로 세차를 만들지 않음.

요청의 컨베이어 구역 체류+N 실제 이동+D 출차, 정차식 구역 정차 체류+출차, 주유 구역 배제는 기존 `WashVisitDetector` 명시 근거/테스트 경로에 존재하며 관련 단위 테스트가 통과함. 실제 SDK→구역 근거 어댑터→해당 detector 운영 연결을 검증한 것은 아님.

현재 운영 경로는 `FACILITY_WASH_IMPLEMENTATION.md` 및 프로젝트 지침의 2026-11-12 정책: 지도 세차시설 반경 내 관측 체류180초+차량 출차로 EstimatedVisit을 만들며 취소 가능하게 적용. N→D나 정밀 구역을 필수로 하지 않음. 따라서 복합 주유/세차시설에서 3분 대기 후 출차한 경우를 완전히 배제하지 못함. 요청의 엄격한 주유·대기 배제 보장은 현재 운영 정책과 다름. 이번 검증에서 정책을 임의로 전환하지 않음.

불규칙 간격·긴 공백·캐시 중복·경계 흔들림·폰 단독 출차 방지·차량 복귀는 단위 검증 통과. 실제 위치/차속 기반 세차 완료 및 무선 Bluetooth는 미검증. 물리 검증은 기존 `BLUETOOTH_VALIDATION_CHECKLIST.md` BT-50/BT-52에 모아 수행.

## 근거

`validation/vehicle-speed-2026-11-12/`의 빌드/계측/신호/Car Service 로그, 콘솔 초기 상태, JUnit XML.
게스트 logcat 시각과 호스트 KST 시각은 다를 수 있으므로 동일 시각으로 해석하지 않음.

공식 문서 재확인:
- https://document.pleos.ai/en/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator
- https://document.pleos.ai/en/docs/connect/guide/getting-started/application-development/configure-pleos-connect-emulator
- https://document.pleos.ai/en/docs/connect/guide/getting-started/application-development/use-vehicle-emulator
- https://document.pleos.ai/en/docs/pleos-only/app-test-guide/virtual-console-guide
- https://appdevchallenge.auto-hmg.io/faq

공식 문서는 Cold Boot 필수(Quick Boot에서 VHAL 사용 불가), API28 이상/Gradle8 이상/Java·Kotlin, Connect2.1.0/API34 및 포트8088 Virtual Console 연결을 안내함. SDK/프로필/런타임 의존성 변경 없음.


