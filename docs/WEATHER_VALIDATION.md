# 7일 예보 조회 검증 결과

2026-11-11 · 구현/인계: [WEATHER_INTEGRATION_HANDOFF.md](WEATHER_INTEGRATION_HANDOFF.md).

**최신 결과는 아래 ‘주행 날씨 연결 후속 검증’을 우선한다.** 최초 검증 이후 [DRIVING_WEATHER_IMPLEMENTATION.md](DRIVING_WEATHER_IMPLEMENTATION.md)의 실제 연결 코드를 추가했다. 과거 표의 ‘주행 날씨 미구현’은 최초 단계 상태다.

## 실행 결과

| 검사 | 결과 |
|---|---|
| 한국 자정/UTC 날짜 차이에서 오늘~+6일, 7개 날짜 | 단위 테스트 통과 |
| 0 강수량/0℃ 및 음수 기온, 눈 cm 보존 | 단위 테스트 통과 |
| null/필드 누락/범위 밖 확률 | 값 조작 없이 null, 테스트 통과 |
| 날짜 어긋남/일별 배열 길이 불일치 | 응답 거부, 테스트 통과 |
| 잘못된 좌표/통신 실패 | 잘못된 좌표 호출 차단, 가짜 날씨 대체 없음, 테스트 통과 |
| 실제 Open-Meteo HTTPS 조회 | **통과**. 공개 서울 시연 좌표로 기본 HTTP 구현 호출, 한국 오늘 포함 7일 및 기온 데이터 확인. 개인 위치 사용/원시 응답 저장 없음 |
| 휴대폰 JVM 단위 테스트 전체 | **36개 통과**, 실패/오류/생략 0. 날씨 테스트 6개(실제 API 선택 실행 1개 포함) |
| `:app:compileDebugKotlin` | **통과**. 기존 SDK deprecated/콜백 인자명 경고는 유지 |
| 실제 Android 기기/에뮬레이터의 날씨 화면 | 미실행, 화면 미구현 |
| 차량 SDK 위치·거리 → 실제 BT → 주행 날씨 보정 | 미구현/미검증. 이번 JVM HTTP 성공과 구별 |

## 재실행

일반 네트워크 없는 테스트(실제 API 검사는 생략):

```powershell
.\gradlew.bat :phone:testDebugUnitTest --tests com.vemoring.companion.WeeklyWeatherRepositoryTest
```

실제 API 포함(개인 위치 없이 고정 공개 시연 위치 사용):

```powershell
$env:VEMORING_WEATHER_LIVE = '1'
.\gradlew.bat :phone:testDebugUnitTest --tests com.vemoring.companion.WeeklyWeatherRepositoryTest --rerun-tasks
Remove-Item Env:VEMORING_WEATHER_LIVE
```

Android SDK/JDK/Gradle 설정은 기존 프로젝트 환경을 사용한다. 위 실제 API 검사는 외부 서비스/네트워크 상태에 따라 실패할 수 있다. 보고서는 `phone/build/test-results/testDebugUnitTest/` 및 `phone/build/reports/tests/testDebugUnitTest/`에 생성되며 Git에 포함하지 않는다.

## 친구가 이어서 확인할 것

- 두 앱에서 공통 조회 진입점 사용 가능. UI 연결 후 Android 인터넷/권한·조회 실패 처리를 확인.
- 주행 구간은 인계 문서의 5분/위치 60초/직선 5km 조건으로 짧은 구간부터 주입 시험.
- 강수 종료시각→시작시각 정규화, PM 시간 연결, 72시간 보완, 기본/추가 차감 중복 방지 검증.
- 무선은 BT-48을 포함한 기존 일괄 체크리스트에서 실행.
- 세차 날짜 추천/화면 갱신 정책은 요청대로 보류.

## 주행 날씨 연결 후속 검증(2026-11-11)

최종 코드로 실행:

| 검사 | 결과 |
|---|---|
| `:phone:testDebugUnitTest` | **44개 통과**, 실패/오류/생략 0 |
| `:app:testDebugUnitTest` | **33개 통과**, 실패/오류/생략 0. 차량 코드 컴파일 포함 |
| 실제 Open-Meteo 7일 예보 / 지난 시간 rain·showers·snowfall·PM 조회 | 선택 실행 네트워크 테스트 **2개 포함 통과**. 공개 서울 시연 좌표 사용, 개인 위치/원시 응답 저장 없음 |
| `DrivingWeatherStorageTest` | **2개 통과**. 잠정 0km→실제 1km 교체, 날씨 중복·역순 방지, 양방향 보완, 취소 기준 재계산, 차량 분리, 128개 초과 결과 전달 |
| 기존 `CareFlowTest` | **14개 통과**. 기록 취소·삭제·동기화·프레임 및 알림 회귀 |
| 실제 SDK/물리 BT/화면 숨김 수신 | **미검증**, BT-48/49에서 별도 수행 |

총 JVM 77개 + Android 주입·저장 회귀 16개 통과. Android는 기존 `Medium_Phone_API_37.0`에 일반 앱이 아닌 `com.example.vemoring.phone.verification` 별도 패키지로 수행했다. 차량 에뮬레이터 프로필/사용자 앱 데이터는 변경하지 않았다.

과정에서 단위 테스트의 JSONObject 시각 직렬화 문제를 발견해 ISO 문자열로 수정했다. 에뮬레이터 첫 시도는 부팅 중 OFFLINE으로 실행되지 않았고, 다음 Gradle 연결 시험에서는 verification 앱의 알림 권한이 없어 기존 알림 검사 3개가 실패했다. 마지막에는 최신 verification APK를 설치하고 **해당 시험 패키지에만** POST_NOTIFICATIONS를 허용한 후 수동 instrumentation으로 16개 전부 통과했다. 이전 Gradle 연결 HTML은 당시 실패 결과이므로 최종 수동 실행 결과와 혼동하지 않는다.

최종 Android 실행:

```powershell
adb install -r -g phone/build/outputs/apk/verification/phone-verification.apk
adb install -r -g phone/build/outputs/apk/androidTest/verification/phone-verification-androidTest.apk
adb shell pm grant com.example.vemoring.phone.verification android.permission.POST_NOTIFICATIONS
adb shell am instrument -w -e class com.example.vemoring.phone.DrivingWeatherStorageTest,com.example.vemoring.phone.CareFlowTest com.example.vemoring.phone.verification.test/com.example.vemoring.phone.VerificationRunner
# OK (16 tests)
```

날씨 계산 테스트에는 실제 거리 기본 0.1%p/km, 비 총 0.3%p/km, 눈 총 0.5%p/km, PM 일별 상한 2%p, 미량 강수 시 먼지 차감 배제, 누락/72시간/시간 경계, 오래된 SDK 값을 새 수신으로 오인하지 않는 검사가 포함된다. 실제 비 노출이나 센서 정확도를 입증한 것은 아니다.

## 2026-11-14 실제 외부 API 재검사

사용자가 실시간 API 호출을 명시적으로 요청하여 `VEMORING_WEATHER_LIVE=1`로 기존 `WeeklyWeatherRepositoryTest`, `DrivingWeatherTest`만 실행했다. 두 클래스의 실제 앱 HTTP 구현을 사용하며, 고정 공개 서울 시험 좌표만 전송했다. 사용자 GPS/차량 위치 수집이나 앱 데이터 변경은 없다. Gradle worker 2개 등 기존 제한을 유지했다.

- **실제 예보 API: 실패.** `api.open-meteo.com`의 7일 예보 조회는 TLS handshake read timeout, 시간별 강수 조회도 실패해 완성된 HourWeather를 얻지 못했다. 앱 바깥 PowerShell HTTPS도 연결이 원격에서 끊겼으며 IPv4 curl도 connect timeout이었다. 따라서 예보의 현재 실시간 성공으로 판정하지 않는다.
- **실제 미세먼지 API: 통과.** `air-quality-api.open-meteo.com`은 HTTP 200, PM10/PM2.5 각 24개 표본을 반환했다. 추가로 완료된 UTC 2026-11-14 03:00을 epoch로 찾아 KST 12:00과 일치함을 확인했다. 응답 utc_offset_seconds=0, HTTP Date도 검사 시각과 일치했다. 해당 시간 PM10=44.9, PM2.5=43.2였다. 이는 공개 시험 지점의 모델 응답이며 사용자 현재 공기질이 아니다.
- **날짜/파싱/정책 로컬 회귀:** 총 14개 중 12 통과, 실제 외부 조회 2개 실패, 생략 0. 한국 날짜 7일/자정·UTC 경계, 누락/오류 처리 등은 통과했지만 이를 실시간 예보 호출 성공과 섞지 않는다.
- DNS의 예보 서버 IPv4는 OS와 Google 공개 DNS 조회 모두 51.81.167.44였고, IPv6 대체 주소는 얻지 못했다. 같은 PC에서 공식 문서 서버와 미세먼지 서버는 HTTP 200이었다. 현재 증거는 특정 예보 호스트로의 연결 실패이며, 공급자 전체 장애/PC 방화벽 원인 중 하나로 확정하지 않는다. 시스템 DNS/방화벽·인증서 검증·제공자·앱 timeout을 임의 변경하지 않았다.
- 앱 코드는 변경하지 않았다. 예보 정상화 판정에는 해당 호스트 연결이 가능한 별도 네트워크/기기 비교와 앱의 실제 예보 검사 재실행이 필요하다. 공개 좌표의 미세먼지 성공만으로 강수·7일 예보 전체를 통과 처리하지 않는다.

로그: `build/diagnostics/weather-live-2026-11-14.txt`, `weather-live-air-time-2026-11-14.json` 및 `phone/build/test-results/testDebugUnitTest/TEST-*Weather*.xml`. 과거 통과 기록은 과거 실행 결과로 보존하며 이번 실패로 덮어쓰지 않는다.
