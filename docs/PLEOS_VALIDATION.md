# 검증 기록 — 2026-11-08

프로젝트 루트의 feature 브랜치 작업
브랜치: feature/pleos-sdk-integration
기준: origin/main 1d49158 Kotlin/Compose

## 이번 작업 결과

- :app:assembleDebug 통과.
- :app:testDebugUnitTest 통과: 15 tests / 0 failures / 0 errors.
  ExampleUnitTest 1 + VehicleStateTest 6 + IntegrationTest 8.
- :app:assembleDebugAndroidTest 통과. 실제 장치 실행 결과가 아님.
- :app:lintDebug 통과: 0 errors / 28 warnings.
  기존 라이브러리 lint API 호환 경고, 의존성 버전 권고, 기존 미사용 리소스 등의 경고 포함.
  lifecycle custom check 일부는 기존 라이브러리의 lint API 불일치로 건너뜀.
- git diff --check 통과.
- 로그: build/final-verification.log
- 보고서: app/build/reports/tests/testDebugUnitTest/index.html / app/build/reports/lint-results-debug.html

단위 테스트는 실제 SDK 응답을 대체하지 않음.
0/null/오류/권한/미지원/늦은 콜백, Demo 격리, 단위 확인 조건,
거리/오염 추정, 외부 JSON/빈 결과/HTTP 경계 실패를 가짜 Gateway/주입 transport로 검증.

추가 lint에서 기존 registerTelltales/unregisterTelltales가 PRIVATE_CAR_SAFETY를 요구하는 오류 발견.
특권 권한을 추가하지 않고 해당 listener 호출을 제거. public 조회를 foreground 30초 간격으로 갱신.

## 외부 HTTP 확인 (SDK 위치가 아닌 명시적 테스트 좌표 37.5,127)

- Open-Meteo forecast: 실제 응답, temperature_2m 존재, precipitation_sum 2개.
- Open-Meteo CAMS: 실제 응답, pm10/dust 필드 존재.
- Overpass POST 첫 시도 HTTP 406. GET으로 실제 응답, fuel 질의 elements 260개, remark 없음.
- 앱은 확인된 GET 방식 사용. 이 HTTP 확인은 Windows 호스트에서 수행.
- Android 앱의 실제 위치 -> 외부 API 전체 경로는 미검증.
- 공공 OSM 자료는 영업 여부/실시간 충전 가용성을 보장하지 않음.
- 날씨/대기질은 모델 추정/예보이며 관측 강수량이나 현장 오염 센서가 아님.

## 실제 Pleos SDK 수신

Vehicle / FusedLocation / NaviHelper 모두 미검증.
이번 요청에 따라 현재 PC의 AVD 실행/수정/실수신 테스트를 하지 않음.
이전 조사에서 Android 부팅은 완료됐으나 eth0 IPv4 없음, DDS participant 실패 및 native service SIGABRT.
SOC100/주행거리0의 source=default 값은 SDK 성공이 아니었음.
이전 원본 진단: build/vehicle-diagnostics/RESULT.md (ignore된 로컬 자료, 소스 ZIP/Git에 포함 안 됨).

정상 PC용 RealSdkReceptionTest:
- Vehicle은 45초 내 연료/SOC/누적 거리 중 실제 Available 숫자가 없으면 실패.
- FusedLocation은 30초 내 최신·유효 위치가 없으면 실패.
- NaviHelper는 30초 내 실제 RouteState 콜백이 없으면 실패.
코드/계측 APK 컴파일만 완료. 실행·통과를 주장하지 않음.
길 안내 변경/영역 진입·이탈/권한 거부/생명주기 장치 검증은 OTHER_PC_TESTING.md 절차 필요.

## 보존 / 전달

- 기존 Data.kt/Setup.kt/VehicleCatalog.kt/PleosVehicle.kt 및 차량 기록 저장 구조 보존.
- 원본 공동 작업 폴더, main, GitHub 변경 없음.
- feature 브랜치의 commit/push 및 PR 검토 대상. main 병합은 별도 승인 전 금지.
- FusedLocation/NaviHelper는 이번 요청 범위에 따라 새로 구현. TTS/STT는 추가하지 않음.
- 실행: [OTHER_PC_TESTING.md](OTHER_PC_TESTING.md)
- 전체 구현/미지원 표와 변경 파일: [SDK_FEATURE_MATRIX.md](SDK_FEATURE_MATRIX.md)
- 소스 ZIP: tools/New-PortableSource.ps1. GitHub에서 feature 브랜치를 clone하는 절차는 실행 가이드 참조.

## PR 제출 전 재검증

- 최신 origin/main fetch: 1d49158. feature의 기준과 동일하여 merge/rebase 충돌 없음.
- 최신 main 기준 assembleDebug/testDebugUnitTest/assembleDebugAndroidTest/lintDebug 재실행: 통과.
- 단위 테스트 15개, 실패/오류 0. lint 오류 0, 경고 28.
- 재검증 로그: build/pr-verification.log (Git 제외).
- 기존 Data.kt/Setup.kt/VehicleCatalog.kt/PleosVehicle.kt 변경 없음, 삭제 파일 없음.
- 홈의 중복 청결도 추정 문구를 제거하고 공통 관리 추정 카드만 사용.
- 문서의 PC별 절대 경로 제거, 로컬/환경/키/생성 파일 ignore 규칙 보강.
- stage한 26개 파일에서 토큰/개인키/자격증명 URL/PC 절대 경로 패턴 및 금지 파일명 미검출.
- 기존 공동 작업 폴더의 미커밋 작업은 이번 feature 커밋에 포함하지 않음.
- 실수신 및 계측/UI 실행 검증은 여전히 미검증. main 병합/자동 병합은 수행하지 않음.