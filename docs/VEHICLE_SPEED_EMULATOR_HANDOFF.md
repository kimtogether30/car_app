# 친구에게: 차속·기어 수신 검증

작성일: 2026-11-12. 구현 커밋: `7c5a035`. 이 문서는 **이미 구현한 코드의 검증 안내**다. 실제 수신 성공을 뜻하지 않는다.

## 할 일

1. 이 문서와 `CarSpeedIntegrationTest.kt`가 들어 있는 브랜치를 받은 뒤 프로젝트 루트에서 아래 명령을 실행한다.
2. 공식 Pleos Connect v2.1.0 / API 34 에뮬레이터를 **Cold Boot**한다. 공식 프로필의 RAM 4GB와 해상도를 유지한다. Quick Boot, 데이터 초기화는 하지 않는다.
3. Android Studio에서 필요한 SDK와 JDK를 설정한 다음 빌드한다. 다른 에뮬레이터·실기기는 연결을 끊거나 아래 `-s`에 정확한 기기 ID를 쓴다.

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest --console=plain
adb devices -l
# emulator-5554는 예시. 위에서 확인한 공식 Pleos 기기 ID로 바꾼다.
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 install --no-streaming -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s emulator-5554 shell pm grant com.example.vemoring android.car.permission.CAR_SPEED
adb -s emulator-5554 shell am instrument -w -e class com.example.vemoring.CarSpeedIntegrationTest com.example.vemoring.test/androidx.test.runner.AndroidJUnitRunner
```

각 명령의 성공을 확인한 다음 다음 명령을 실행한다. `pm grant`가 거부되면 통과로 기록하지 말고 앱의 ‘차량 속도 접근 허용’으로 권한 부여가 가능한지 확인한다. 서명 불일치 시 앱 삭제 대신 기존 설치의 서명·빌드 출처를 먼저 확인한다. `-r`은 기존 사용자 데이터를 보존하기 위한 옵션이다.

## 무엇을 확인하나

자동 계측 테스트가 `OK (2 tests)`로 끝나는지 확인한다. 한쪽만 통과하거나 일부가 생략되면 전체 통과가 아니다.

| 검사 | 실제 확인하는 것 |
|---|---|
| speedAndGearArriveThroughProductionGateway | 실제 차량 게이트웨이에서 유효 차속(m/s)·Pleos 기어를 받고 현재 상태를 계산하는지 |
| sourceCloseStopsPublishing | 차속 수신을 종료한 뒤 콜백 게시가 멈추는지 |

이 검사는 차량 신호를 읽기만 한다. 속도·기어를 조작하거나 테스트 값을 차량에 쓰지 않는다. 따라서 정지 상태에서 통과해도 이동·후진 수신까지 확인한 것은 아니다.

## 화면에서 추가 확인

베모링의 차량 수신 정보 화면에서 **현재 차량 속도, 기어, 이동 상태**를 확인한다. 에뮬레이터의 공식 지원 도구로 신호를 변경할 수 있는 경우에만 다음을 실제 수신으로 검사한다. 해당 도구가 없거나 신호 변경이 안 되면 ‘미검증’으로 남긴다. GPS 위치 이동은 차량 차속 입력과 다르다.

| 받은 값 | 기대 결과 |
|---|---|
| 최신 차속 0 + 최신 P | 주차(P) |
| 최신 차속 0 + D/N/R | 정차 |
| 최신 차속 0 이외, 후진 음수 포함 | 이동 중 |
| 차속 권한 거부·미지원·미수신·오류 | 확인 불가 |
| 마지막 유효 차속 이후 새 유효 표본이 5초 넘게 없음 | 확인 불가 |
| 차속 0, P 기어 표본만 35초 초과 | 정차로 표시하되 주차(P)로 확정하지 않음 |

5초/35초는 현재 코드의 시험 기준이며 SDK의 갱신 주기 보장값이 아니다. 같은 숫자라도 새 측정 시각이면 새 표본이다. 앱 화면을 닫는 것만으로 수신 중단을 만들 수는 없다(백그라운드 수집이 유지될 수 있음). 수신 중단을 실제로 만들지 못했다면 오래된 값 처리는 단위 테스트 통과와 실수신 미검증을 구분한다.

단위 테스트 `VehicleMotionTest`는 위 판정과 잘못된 시각·값을 검증하지만, 실제 SDK 수신 증거가 아니다. 차속 판정이 통과해도 세차 구역 체류·세차 완료 감지나 실물 Bluetooth 전송이 검증되는 것은 아니다.

## 막히면

- APK 설치가 끝나지 않거나 기기가 offline이면 기기 상태·남은 공간·Cold Boot 여부부터 확인한다. 무기한 기다리거나 설치를 동시에 반복하지 않는다.
- 기존 PC에서는 본 앱 설치는 성공했으나 테스트 APK 설치가 멈춰 **계측을 실행하지 못했다**. 현재 실수신 검증 상태는 미완료다.
- 권한/미지원/NoData/오류는 0km/h로 대신하지 않는다. 실패한 명령과 원문 오류를 결과에 남긴다.
- `cmd car_service`가 user build에서 차단되면 root나 비공개 API로 우회하지 않는다.

## 결과 남기기

같은 폴더의 `VEHICLE_SPEED_VALIDATION_RESULTS.csv`를 채워 전달한다. 상태는 통과/실패/미검증 중 하나를 쓴다. 에뮬레이터·빌드 커밋·실행 시각과 실제 출력/로그 경로를 근거 칸에 적는다. 단위 테스트와 실제 수신 검사를 따로 기록한다.

구현 상세: [VEHICLE_SPEED_MOTION.md](VEHICLE_SPEED_MOTION.md). 물리 차량·Bluetooth 추가 검사: [BLUETOOTH_VALIDATION_CHECKLIST.md](BLUETOOTH_VALIDATION_CHECKLIST.md)의 BT-50.
