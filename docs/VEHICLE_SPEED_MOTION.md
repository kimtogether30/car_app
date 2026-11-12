# 차속 수신과 정차·주차 판단

2026-11-12. 차량 앱 `app/`에 읽기 전용 AAOS 차속을 연결했다. 기존 누적거리 기반 세차 차감/평균 거리 정책은 변경하지 않는다.

## 수신 경로

- `CarSpeedSource` → `CarPropertyManager.PERF_VEHICLE_SPEED` 현재값 + 1Hz 구독. 내부 값은 signed m/s, 진단 표시는 km/h(×3.6). 후진 음수도 이동이다. 크루즈 목표 속도/GPS 속도를 차속으로 대신하지 않는다.
- `android.car` optional library를 선언해 비-AAOS에서 해당 라이브러리가 없다고 앱 전체가 실패하지 않게 한다. 차량 전용 클래스는 automotive 기기에서만 로드한다. 일반 휴대폰/미지원/권한 없음은 명시적 상태를 반환한다.
- `android.car.permission.CAR_SPEED` 필요. 차량 수신 정보에서 ‘차량 속도 접근 허용’ 버튼으로 요청할 수 있다. 백그라운드에서 권한 창을 자동으로 띄우지 않는다. 기존 수집 소유권/연결 생명주기와 함께 시작·새로고침·종료한다.
- Pleos Vehicle 2.3.2의 기존 기어 조회·구독 유지. 실제 raw 열거값 `GEAR_PARK/REVERSE/NEUTRAL/DRIVE`를 P/R/N/D로 정규화한다. 원본 rawState는 보존한다.
- 기존 30초 경고 조회 시 기어도 재확인한다. 새 차속용 폴링 루프는 없다. Binder 조회/등록/해제는 전용 IO 스레드, 콜백 전달만 main에서 처리한다.

## 판단

| 입력 | 상태 | stationary |
|---|---|---|
| 유효한 최신 속도 절댓값 >0 | Moving | false |
| 속도 =0, 최신 P 기어 | Parked | true |
| 속도 =0, D/R/N/미확인 기어 | Stopped | true |
| 속도 오류/미수신/미지원/권한 없음/오래됨/Demo | Unknown | null |

- 초기 운영값: 차속 5초, 기어 35초 이내. 공식 정확도 보장값이 아니다. 기어가 오래되면 0 차속의 정차는 유지하되 P 주차로 확정하지 않는다.
- 차속 이벤트의 monotonic timestamp를 검사한다. 동일/이전 시각 캐시 응답을 새 수신으로 세지 않는다. timestamp 0/미래/5초 초과도 유효 차속으로 사용하지 않는다. 업데이트가 끊기면 5초 기한에 NoData를 게시해 기존 0을 계속 정차로 남기지 않는다.
- 이 값은 현재 정차 상태이며 ‘10초 정차 확인’, ‘3분 세차장 체류’, ‘세차 완료’를 의미하지 않는다. 세차장 구역/체류/출차 조건은 별도로 유지한다. 컨베이어 N/이동/D 근거를 차속만으로 대체하지 않는다.
- `VehicleSnapshot.motion()` / `confirmedStationary()`가 앱의 공통 판정 진입점이다. 차량 수신 정보에 현재 판정을 표시하고 기존 BT care payload의 `motion`에 판정·차속/기어 시각·출처를 포함한다. 수신자는 시각을 다시 확인해야 하며 저장된 마지막 판정을 계속 현재 상태로 취급하면 안 된다.
- 백그라운드 관리 게시 경로는 단순 차속 숫자 갱신을 제외하고 상태 변화/다른 관리 신호 변화에만 처리한다. 매 1Hz마다 관리 장부 저장이나 BT 전체 재게시를 하지 않는다. 따라서 휴대폰 payload는 실시간 계기판 스트리밍이 아닌 관리 상태 표본이다.

## 검증

- `VehicleMotionTest`: 0+P/다른 기어, SDK 기어 raw 이름, 전진/후진/아주 낮은 비영 속도, 오류/Demo/null/NaN/오래됨/미래 시각, 오래된 P, monotonic 시각과 m/s 보존 검사.
- `CarSpeedIntegrationTest`: 공식 Pleos 자동차 에뮬레이터에서 **실제 Car Service 및 Pleos SDK 콜백을 읽기만** 한다. 속도+기어 수신과 close 후 콜백 중단 확인. 사용자 차량 데이터 저장/제어 호출은 없다. CAR_SPEED는 시험 대상에 허용해야 한다.
- 에뮬레이터의 `cmd car_service help`는 user build에서 차단되어 있었고 이를 root/비공개 API로 우회하지 않는다. 이동/후진/오류 입력의 판정은 단위 테스트로, 에뮬레이터 수신은 실제 읽기 테스트로 구분한다.
- 물리 차량/무선 BT 왕복은 별도 검증. `BLUETOOTH_VALIDATION_CHECKLIST.md` BT-50 참고. 에뮬레이터 수신 성공은 실차 지원 보장이 아니다.

공식 근거:
- https://document.pleos.ai/en/docs/pleos-only/vehicle-app-developer-guide/vehicle-app-faq — 계기판 차속은 PERF_VEHICLE_SPEED로 조회.
- https://developer.android.com/reference/android/car/VehiclePropertyIds#PERF_VEHICLE_SPEED — signed m/s.
- https://developer.android.com/reference/android/car/hardware/property/CarPropertyManager — 조회/구독/해제.

최종 검사 결과와 미검증 범위는 아래 후속 결과 절에 기록한다.

## 이번 실행 결과

- 차량 앱 단위 테스트 38개(새 판정 테스트 5개 포함) 실패/오류/생략 0. 휴대폰 기존 단위 테스트 44개 결과도 실패/오류/생략 0 확인(변경 없는 항목은 Gradle UP-TO-DATE 재사용).
- 최종 코드 `:app:testDebugUnitTest :phone:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest` 성공. 실제 APK에 `android.car` optional uses-library와 CAR_SPEED 권한이 합쳐진 것을 확인.
- 공식 `Pleos_Connect_v2.1.0` API34, 기존 4GB 프로필을 해상도/메모리 변경 없이 Cold Boot. 차량 앱 APK는 `adb install -r`로 **업데이트 설치 성공**, 데이터 삭제 없음.
- **실제 차속/기어 콜백 검사는 미완료.** 검사 APK 스트리밍 설치가 장시간 완료되지 않아 해당 설치 클라이언트만 중단하고 표준 `--no-streaming` 방식으로 재시도했다. 파일 전송은 성공했으나 패키지 설치 완료/계측 실행 응답을 얻지 못했고 화면 캡처도 지연됐다. 실수신/권한 허용/계측 통과로 기록하지 않는다. 검증용 에뮬레이터는 종료하며 AVD/데이터는 삭제하지 않았다.
- `cmd car_service help`는 user build에서 SecurityException으로 차단됐고 root/비공개 제어로 우회하지 않았다. 물리 차량·BT는 여전히 BT-50 미검증.

기기 상태가 정상인 공식 에뮬레이터에서 아래 검사를 이어서 수행한다. 앱 데이터는 `-r`로 보존하고 서명 불일치 시 제거/초기화 대신 먼저 확인한다.

```powershell
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install --no-streaming -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell pm grant com.example.vemoring android.car.permission.CAR_SPEED
adb shell am instrument -w -e class com.example.vemoring.CarSpeedIntegrationTest com.example.vemoring.test/androidx.test.runner.AndroidJUnitRunner
```

이 검사는 에뮬레이터 속도/기어를 읽고 close 후 콜백 중단을 확인할 뿐, 움직이는 실차의 정확도·모든 차종 지원·세차 완료를 검증하지 않는다.
