# 검토 후 수정 검증 결과

2026-11-12~13. 변경 범위: `REVIEW_FIXES_2026_11_12.md`.

## 완료

- 차량/휴대폰 debug APK 컴파일·조립 통과.
- 두 앱의 단위 테스트 합계 105개: **103개 통과, 기존 제외 2개, 실패 0개**.
- 휴대폰 verification 앱에서 아래 계측 검사 통과. 실제 사용자 앱 데이터를 초기화하지 않았음.
  - HEV: 연료 80%·SOC 0%·LOW_FUEL=true에서 주유 배너 없음. 연료 미수신에서도 배너 없음. 연료가 설정값 20%에 도달하면 주유 배너.
  - 지원하지 않는 ABS 원본 경고는 eligible=true여도 게시하지 않음. 지원 액량 경고의 지속·해제·재발·오래된 수신 차단 회귀 검사 통과.
  - 수동 세차 기록의 14:30 완료 시각이 편집 화면에 표시되고 편집 가능. 스크린샷 확인: 입력 영역과 저장 버튼 표시 정상.
- 휴대폰 UI 첫 시도는 콜드 부팅 뒤 System UI 응답 지연 팝업 때문에 실패. 실제 스크린샷으로 원인을 확인하고 ‘Wait’ 후 재검사 통과. 앱 오류를 감추거나 테스트 기대값을 완화하지 않음.
- 공식 Pleos Connect v2.1.0/API 34/4GB/Cold Boot에서 `WashTimeInputUiTest` 재검사 통과. 14:30 입력칸과 저장 버튼 접근 가능. 실제 차량 에뮬레이터 스크린샷으로 입력칸·안내문·저장 버튼 표시 확인. 첫 시도는 Compose 화면 계층이 나타나지 않아 실패했고, 부팅 안정화 뒤 동일 테스트가 통과함.
- `PleosVehicleGateway.kt`, `VehicleCareRuntime.kt`는 이번 변경 전 내용과 동일. 작업 중 추가한 FuelRecoveryRuntime·queryFuel·fuelRecoveryToken·fuel-delivered는 최종 소스에 없음. **2번 회복 기준 구현은 포함하지 않음.**
- `git diff --check` 통과.

## 미검증/제한

- 위치 1/3/10초 재연결의 실제 차량 SDK 오류→복구, Bluetooth RFCOMM 재시도·수동 안내의 실제 무선 연결은 미검증. 재시도 간격과 최대 횟수의 순수 정책 테스트만 통과.
- 시동 종료 후 계속 수신·휴대폰 강제 종료·제조사 절전 정책을 보장하지 않음. BT-53에서 물리 장비를 사용해 검증.
- 4·5번은 사용자 요청으로 추가 개발하지 않았으므로 자동 세차 반영 도중 종료 및 장기 보관/재전송 제한이 남음.

## 다시 실행

단위 테스트/빌드:

```powershell
.\gradlew.bat :phone:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug :phone:assembleDebug
```

휴대폰 계측 클래스/메서드:

- `com.example.vemoring.phone.WashTimeInputUiTest`
- `com.example.vemoring.phone.BannerNotificationSimulationTest#hybridFuelOnlyAndUnsupportedWarningsDoNotPostEvenIfMarkedEligible`
- `com.example.vemoring.phone.BannerNotificationSimulationTest#stateTransitionsDeduplicateAndDoNotInventRepairsOnDisconnectOrNormal`

차량 계측 클래스: `com.example.vemoring.WashTimeInputUiTest`.

스크린샷은 Git 제외 경로 `build/diagnostics/review-fixes/phone-wash-time.png`, `vehicle-display.png`에 로컬 보관. 물리 Bluetooth 확인 목록은 `BLUETOOTH_VALIDATION_CHECKLIST.md` 사용.
