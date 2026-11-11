# 7일 예보 조회 검증 결과

2026-11-11 · 구현/인계: [WEATHER_INTEGRATION_HANDOFF.md](WEATHER_INTEGRATION_HANDOFF.md).

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
