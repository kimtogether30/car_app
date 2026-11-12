# 시설 주변 방문 기반 세차 추정

2026-11-12 사용자 합의 반영. **정밀한 세차 구역 경계와 컨베이어 N→D 패턴을 필수로 요구하던 방식 대신**, 지도 등록 세차 시설 주변에서 관측된 체류와 차량 출차를 사용한다. 주유소 복합 시설도 같은 추정·취소 방식으로 처리한다. 실제 물세척을 검출하는 기능이 아니다.

## 실제 코드 연결

1. 기존 `SharedVehicleConnection`의 Pleos `PleosLocationGateway` 연속 콜백을 공유한다. 별도의 세차 전용 SDK 연결을 늘리지 않는다.
2. `VehicleWashRuntime`은 조회 중인 차가 아니라 `GarageStore.receiving()`의 수신 차량에만 위치를 연결한다. 수신 설정이 꺼져 있거나 예시 위치이면 자동 방문을 만들지 않는다.
3. 기존 `HttpExternalDataRepository.places(Wash)`가 OpenStreetMap Overpass의 `amenity=car_wash`를 조회한다. 이름이 없어도 이 태그가 있으면 ‘세차장’으로 표시한다. 세차장 정보가 없는 주유소를 임의로 추가하지 않는다.
4. 조회 성공 결과는 실행 중 재사용한다. 마지막 조회 위치에서 5km 넘게 이동하거나 15분이 지나면 다음 위치 관측 때 재조회하고, 실패 시 재시도 간격은 최소 1분이다. 매 위치 콜백마다 인터넷에 요청하지 않는다. 기존 조회 범위 10km·최대 가까운 20개 제한은 유지한다.
5. 실제 차량 위치와 시설 중심 좌표를 비교해 `FacilityWashStore`에 방문 표본을 저장한다. `washVisits`가 기존 care Bluetooth payload에 포함된다. 정차 중 누적거리가 같아도 새 위치 방문 표본이 있으면 게시한다.
6. 휴대폰 `CareExchange.receive()`가 방문을 처리한다. 동일 방문의 휴대폰 보완 표본이 있으면 측정 시각 순서로 병합하고 출차를 판단한다. 기록의 최종 확정은 기존 구조대로 휴대폰 권한 상태(CareAuthority)가 담당하며 차량에 응답으로 동기화한다.
7. 자격을 충족하면 기존 `acceptDetectedWash`와 알림 경로를 사용한다. 반드시 `EstimatedVisit`로 저장하므로 독립·복합 시설 모두 **‘세차한 것 같아!’ / ‘세차 안 했어요’**를 사용한다. 기존 방문 ID 중복 방지·취소 tombstone·점수 재계산을 유지한다.

차량에서 관측을 시작하고 저장하지만, 휴대폰에 방문 정보가 전달되기 전까지 휴대폰 기록/알림 적용은 완료되지 않는다. 단절을 시동 종료로 단정하지 않는다.

## 시험 임계값

다음 수치는 SDK가 보장하는 정확도나 공식 세차 소요시간이 아니다. 실제 수신 간격과 시설 좌표 오차를 보고 조정할 **초기 시험값**이다.

| 항목 | 현재 값과 의미 |
|---|---|
| 진입 | 시설 중심에서 50m 이내의 차량 위치로 방문 시작 |
| 시설 겹침 | 50m 안에 여러 후보가 있고 가까운 두 후보 거리 차이가 20m 미만이면 신규 방문 보류 |
| 진입 안정성 | 안쪽 위치의 유효 인접 구간 합이 10초 이상 |
| 체류 | 안쪽 위치끼리 연결되는 실제 시간 합이 180초 이상. 진입 확인 시간도 포함 |
| 위치 유효성 | 좌표 범위 정상, 표본 측정→해당 기기 수신 지연 0~15초 |
| 표본 간격 | 인접 표본 간격이 30초 이하면 실제 차이를 더하고, 초과 공백은 더하지 않음 |
| 경계 완충 | 50~100m 표본 구간은 체류로 더하지 않으며 출차도 확정하지 않음 |
| 출차 | 차량 위치가 100m 이상 밖에서 30초 이상 이어져야 함. 출차 표본 사이 30초 초과 공백이면 출차 확인을 다시 시작 |
| 휴대폰 보완 시작 | 해당 방문의 마지막 차량 표본 뒤 20초 이상 지난 휴대폰 표본부터 사용 |
| 휴대폰 정확도 | 수평 정확도가 실제 제공되고 50m 이내인 표본만 사용 |
| 방문 유효 기간 | 최초 차량 표본부터 2시간. 최대 4개 방문·방문당 1,500개 표본 |
| 기록 빈도 | 차량·휴대폰 각각 측정 시각 기준 최소 5초 간격으로 저장. 5초마다 새 값이 온다고 가정하지 않음 |

같은 좌표라도 새로운 측정 시각이면 실제 새 표본으로 사용할 수 있다. 같은 측정 시각의 캐시 응답을 새 표본으로 늘리지 않는다. 긴 공백 전후에 확인된 체류 구간은 보존하되 공백 자체는 0초다. 3분은 빈 시간을 제외한 **확인된 구간들의 누적 시간**이다.

`confidence`를 ‘오차 몇 m’로 변환하지 않는다. 현재 Pleos 어댑터는 미터 단위 수평 정확도를 확보하지 못했으므로 차량 표본에 이를 지어내지 않는다. 위치 오차로 인한 오탐 가능성은 남으며 정정 기능으로 보완한다.

## 시동 종료·휴대폰 보완

- 차량 SDK 실행/수신이 실제로 가능한 동안만 관측한다. 앱 화면 종료와 SDK 종료는 별개이며 기존 공유 수집 소유권을 유지한다. 강제 상시 실행·시동 종료 후 수신을 보장하지 않는다.
- 휴대폰의 ‘데이터 연결’에서 **‘세차 위치 보완 켜기’**를 누른다. 정확한 위치 권한이 필요하다. 앱이 보이는 상태에서 위치 포그라운드 서비스를 시작하고, 별도의 실행 알림과 끄기 동작을 제공한다. 백그라운드 위치 권한을 몰래 요청하거나 부팅 때 서비스를 자동 시작하지 않는다.
- 서비스가 준비돼 있어도 차량에서 시작한 유효 방문이 없으면 GPS를 구독하지 않는다. 유효 방문 동안에만 Android GPS 표본을 받아 저장하며, 차량 표본이 계속 최신이면 휴대폰 표본으로 중복 체류를 더하지 않는다.
- 휴대폰 위치만으로 방문 시작·세차 완료를 만들지 않는다. 걸어서 시설을 벗어난 것은 차량 출차가 아니다. 재연결 후 실제 차량 바깥 표본이 확인돼야 반영한다.
- 차량과 휴대폰 모두 표본이 없으면 체류 시간을 채우지 않는다. 이후 충분한 유효 체류와 출차가 확보되지 않으면 자동 반영하지 않는다.
- 프로세스가 종료돼도 방문 표본은 차량별 로컬 저장소에 남는다. 휴대폰 서비스는 자동 재시작을 보장하지 않으므로 꺼졌다면 앱을 열어 다시 켜야 한다. 마지막 수신 차량의 유효 방문은 저장소에서 복구한다.

## 위치 전송·저장

사용자가 2026-11-12 명시적으로 승인한 기존 조회 서버 `https://overpass-api.de/api/interpreter`에 주변 검색용 차량 좌표를 보낸다. 시설 목록의 누락·중심 좌표 오차·서버 장애는 가능하며 전국 시설 감지 성공을 보장하지 않는다. 좌표를 앱 로그에 남기지 않는다.

방문 근거는 차량/휴대폰 로컬에 저장하고 기존 페어링된 Bluetooth 상대에게 전달한다. 새로운 분석 서버·Firebase는 추가하지 않는다. 원시 방문 근거는 2시간이 지나면 다음 저장소 접근 시 정리한다. 기기가 실행되지 않는 동안 정확히 2시간에 물리 삭제하는 타이머는 없다. 확정된 관리기록·취소 정보는 기존 보관 정책대로 유지한다. 차량 삭제 시 해당 방문 근거도 제거한다. 차량 앱의 방문 저장소와 송신 캐시는 클라우드 백업·기기 전송에서 제외하며, 휴대폰은 기존 백업 비활성 정책을 유지한다.

## 친구가 검증할 순서

1. 단위 테스트: `FacilityWashPolicyTest`의 불규칙 간격·긴 공백·중복 캐시·폰 단독 출차·차량 복귀·경계 흔들림 검사.
2. 휴대폰 검증 APK에서 `FacilityWashPipelineTest` 실행: 실제 receive→기록→점수→취소→재전송 경로 검사. 이 검사는 **주입 좌표**이며 GPS/차량 SDK/무선 BT 검증이 아니다.
3. 공식 Pleos 에뮬레이터 Cold Boot에서 위치 권한을 허용하고 실제 위치 콜백의 측정 시각·수신 간격을 확인. `CareSdkEvidenceTest`의 기록도 활용. 새 ‘세차 감지용 차량 위치 허용’ 버튼은 차량 수신 정보에 있다.
4. SDK 위치 이동이 지원되는 공식 도구로 등록 세차장 진입·체류·이탈을 확인한다. 위치를 실제로 바꿀 수 없다면 주입 좌표 검사와 실수신 검사를 따로 기록한다.
5. 휴대폰 위치 보완 켜기→권한 거부/허용→GPS 끄기/켜기→화면 숨김→서비스 끄기를 검사한다. 서비스 시작이 거부되면 앱을 연 상태에서 다시 켠다.
6. 에뮬레이터는 한 번에 하나씩 실행. 무선 Bluetooth와 실차 시동 종료는 별도로 BT-52 검증. 현재 에뮬레이터만으로 그 성공을 주장하지 않는다.

기존 정밀 구역 기반 `WashVisitDetector`는 테스트/별도 명시 근거 경로로 남겨 두며, 이번 실제 위치 경로의 필수 조건으로 사용하지 않는다. 기존 세차 점수 차감 계수·하루 평균 20km·날씨 정책은 바꾸지 않는다.

공식 참고:
- https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/FusedLocation/location-data-access
- https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/FusedLocation/declaring-permissions
- https://developer.android.com/develop/background-work/services/fgs/service-types#location

실행한 검사 결과와 남은 실수신 검증은 작업 완료 후 아래에 기록한다.

### 재현 명령

프로젝트 루트에서 빌드하고, 휴대폰 검증은 실제 앱과 별도 ID인 `com.example.vemoring.phone.verification`만 사용한다. `emulator-5554`는 실행 기기 ID로 바꾼다. 기존 앱을 삭제하거나 데이터를 초기화할 필요가 없다.

```powershell
.\gradlew.bat :phone:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :phone:assembleDebug :phone:assembleVerification :phone:assembleVerificationAndroidTest --console=plain
adb -s emulator-5554 install --no-streaming -r phone/build/outputs/apk/verification/phone-verification.apk
adb -s emulator-5554 install --no-streaming -r phone/build/outputs/apk/androidTest/verification/phone-verification-androidTest.apk
adb -s emulator-5554 shell am instrument -w -e class com.example.vemoring.phone.FacilityWashPipelineTest,com.example.vemoring.phone.PhoneWashAssistUiTest com.example.vemoring.phone.verification.test/androidx.test.runner.AndroidJUnitRunner
```

차량 에뮬레이터는 별도 사용자로 실행될 수 있다. 이번 공식 AVD의 현재 사용자는 `10`이었다. `adb shell am get-current-user`로 확인한 사용자에 권한·검사를 적용한다. 기본 사용자0에 부여하고 현재 사용자 권한까지 있다고 착각하면 안 된다.

```powershell
adb -s emulator-5554 shell am get-current-user
# 아래 10은 확인한 현재 사용자 ID로 바꾼다.
adb -s emulator-5554 shell pm grant --user 10 com.example.vemoring pleos.car.permission.FUSED_LOCATION
adb -s emulator-5554 shell am instrument --user 10 -w -e class com.example.vemoring.CareSdkEvidenceTest com.example.vemoring.test/androidx.test.runner.AndroidJUnitRunner
adb -s emulator-5554 shell run-as com.example.vemoring --user 10 cat files/care-sdk-evidence.json
```

### 이번 실행 결과

- 차량 앱 단위 테스트 40개, 휴대폰 단위 테스트 59개 모두 실패/오류 0. 새 시설 방문 정책 검사 6개는 휴대폰 단위 테스트에 포함된다.
- 양쪽 앱 debug APK, 차량 계측 APK, 휴대폰 verification 앱/계측 APK 빌드 성공.
- 휴대폰 API37 에뮬레이터에서 **주입 좌표 기반 검사 2개 + 권한 안내 UI 접근성·화면 검사 1개, 최종 `OK (3 tests)`**. 실제 코드의 기록/점수/취소/재전송과 보완 표본 재개방·병합을 확인했다. 실 GPS·차량 SDK·Bluetooth 검증을 뜻하지 않는다.
- 최초 검사에서 테스트 프레임의 필수 `plate` 누락을 수정했다. Android37의 Espresso `InputManager.getInstance` 호환 문제는 의존성/OS 변경 없이 ActivityScenario·접근성 노드 순회로 검사했다. 에뮬레이터 System UI 응답 지연 창은 정상 복구 후 최종 검사에서 제거됐음을 스크린샷으로 확인했다. 검사를 생략하거나 실패를 통과로 바꾸지 않았다.
- 최종 휴대폰 화면 증거: 로컬 `build/diagnostics/facility-wash/wash-assist-control.png` (Git 제외). ui-ux-pro-max의 권한 전용 검색에는 검증된 일치 지침이 없어 일반 Android 권한 흐름·48dp 터치 영역·상태 문구·스크롤 원칙을 적용했다.
- 물리 Bluetooth/실차 시동 종료/실제 시설별 오탐률·반경 적합성은 미검증이며 BT-52에서 확인한다. 차량/휴대폰 모두 최신 코드로 업데이트해야 새 `washVisits` 연결을 사용할 수 있다.
- 공식 Pleos Connect v2.1.0 / API34 / 4GB AVD를 설정 변경 없이 Cold Boot하고, 기존 앱 데이터를 보존해 차량 앱·계측 APK 업데이트 설치 성공. `CareSdkEvidenceTest`를 현재 사용자10에서 실행해 `OK (1 test)`와 실제 위치 `Available` 콜백 11개를 확인했다. 측정 시각이 서로 달랐으며 최초 두 표본 간격은 약 3초, 이후 약 1~2초였다. 이번 관측은 갱신 주기 보장이 아니다. 수신 지연은 약 11~1,068ms, confidence=5, 미터 단위 accuracy 미제공이었다.
- 위치 상태만 담은 로컬 증거는 `build/diagnostics/facility-wash/pleos-location-evidence.json`에 저장했다. 좌표는 이 보고서에 넣지 않았다. 이 검사는 production 위치 어댑터의 SDK 수신을 확인하지만 실제 등록 시설 방문 전체·백그라운드/시동 종료 수신을 확인하지 않는다.
- 차량 앱 Activity 실행 요청은 성공했지만 공식 AVD 화면 캡처는 검정 화면으로 반환되어 차량 권한 버튼의 화면 검증은 완료하지 못했다. 이를 화면 통과로 기록하지 않는다. 휴대폰 권한 안내 화면은 별도 확인 완료. 검증에 사용한 휴대폰·차량 에뮬레이터는 각각 종료했고 AVD/사용자 데이터는 초기화하지 않았다.
