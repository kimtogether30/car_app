# 경고등 SDK와 휴대폰 현위치 진단 (2026-11-13)

## 경고등: 앱 사전 호출 수정, SDK 내부 권한 차단은 미해결

- 공식 Vehicle SDK 2.3.2 AAR의 `Safety.checkTelltalesCapabilities`는 `PRIVATE_CAR_SAFETY`를 요구한다. 앱이 이 호출을 먼저 해서 일반 권한으로 제공되는 것처럼 선언된 getter까지 도달하지 못하던 경로를 제거했다.
- `Safety.getTelltalesInfo` 인터페이스의 `androidx.annotation.RequiresPermission`은 `CAR_SAFETY`지만, 같은 AAR의 `SafetyImpl.getTelltalesInfo`에는 런타임 `RequiresCustomPermission(permissions=["pleos.car.permission.PRIVATE_CAR_SAFETY"])`가 붙어 있다. 인터페이스와 내부 구현의 권한 불일치다.
- 공식 Pleos Connect v2.1.0 / API 34 / RAM 4096 MB / Cold Boot, Vehicle Service 2.3.22에서 `CAR_SAFETY` granted(0)를 확인하고 **getter만 직접 호출해도** `SecurityException: getTelltalesInfo need Permission`이 반환됐다.
- `TelltaleSdkDiagnosticTest`는 이 결과를 `files/telltale-diagnostic.json`에 기록하는 진단 검사다. 테스트의 정상 종료는 콜백/증거 기록 완료이며, 경고등 수신 성공이 아니다.
- 공식 Maven 버전 목록(2026-11-13 조회)은 2.0.3, 2.3.2이며 latest/release 모두 2.3.2다. 상위 수정 버전은 확인되지 않았다. SDK 다운그레이드, 특권 권한 추가, 권한 검사 우회, 가짜 경고 수신은 하지 않았다.
- 앱의 실제 getter 실패에는 `getTelltalesInfo: PermissionDenied`처럼 실패 단계가 남는다. SDK가 거부한 값을 Available/정상으로 만들지 않는다.

재현:

```powershell
adb -s <Pleos> shell am instrument -w -e class com.example.vemoring.TelltaleSdkDiagnosticTest com.example.vemoring.test/androidx.test.runner.AndroidJUnitRunner
adb -s <Pleos> shell run-as com.example.vemoring --user <현재사용자> cat files/telltale-diagnostic.json
```

Pleos 문의에 필요한 최소 내용: 위 SDK 인터페이스/구현의 권한 차이, SDK 2.3.2 / 서비스 2.3.22 / 공식 AVD 2.1.0, 일반 권한 granted 상태, getter 단독 호출 오류. 공개 getter 권한 수정 또는 일반 앱에 허용된 공식 대체 API가 필요하다. 외부 문의는 전송하지 않았다.

공식 근거:
- [Vehicle SDK 구조·기준 버전](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/intro)
- [API별 권한 확인 지침](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle-permission)
- [공식 Maven 버전 목록](https://nexus-playground.pleos.ai/repository/maven-releases/ai/pleos/playground/Vehicle/maven-metadata.xml)

## 2026-11-14 재조사 및 해결 경로

- 공식 Maven metadata를 다시 직접 조회했다. latest/release는 여전히 2.3.2이고 공개 버전은 2.0.3/2.3.2다. 수정 버전이 배포됐다는 근거는 없으므로 현재 SDK를 임의 변경하지 않는다.
- ‘Vehicle Service로 조회하면 된다’는 별도 우회 경로가 아니다. 현재 `vehicle.getSafety().getTelltalesInfo`가 SDK를 통해 해당 서비스를 조회하는 경로이며, getter 단독 진단에서도 거절됐다. 실차 확보만으로 같은 권한 문제가 해결된다고 단정할 수 없다.
- 일반 Android `CarPropertyManager`도 현재 확인한 대체 수단이 아니다. 공식 `ENGINE_OIL_LEVEL`은 오일 **양**이며 압력 경고와 다르고, `CAR_ENGINE_DETAILED` signature/privileged 권한도 요구한다. 임의 vendor ID나 오일 양을 압력 경고로 치환하지 않는다. [공식 VehiclePropertyIds](https://developer.android.com/reference/android/car/VehiclePropertyIds#ENGINE_OIL_LEVEL).
- 실행 가능한 해결 순서: (1) 기존 단독 getter 재현 보고서로 Pleos에 인터페이스/구현 권한 불일치 확인 요청, (2) 일반 앱에 허용된 API 또는 수정 SDK/Vehicle Service/AVD 조합을 제공받음, (3) 공식 Cold Boot AVD에서 권한 granted와 Virtual Console의 경고 ON/OFF를 비교하여 실제 콜백·앱 캐릭터 변경 재검사. 임의 특권 부여나 SDK 검사를 제거한 결과를 배포 검증으로 쓰지 않는다.
- 문의 초안(아직 외부 전송하지 않음): “Vehicle SDK 2.3.2 / Vehicle Service 2.3.22 / Connect AVD 2.1.0에서 CAR_SAFETY granted 상태로 Safety.getTelltalesInfo만 직접 호출해도 SecurityException(getTelltalesInfo need Permission)이 발생합니다. 인터페이스 RequiresPermission은 CAR_SAFETY이나 SafetyImpl의 RequiresCustomPermission은 PRIVATE_CAR_SAFETY로 확인됩니다. 일반 공모전 앱에서 사용할 공식 getter/권한 조합 또는 수정 SDK·서비스·AVD를 안내해 주세요.”
- SDK 입력 이후의 캐릭터/알림/실제 무선 전송은 격리된 표본으로 검사할 수 있고 실제 무선 검사는 이미 수행했다. 이 결과는 거절된 SDK 입력 자체의 성공을 증명하지 않는다. 절전·거리·재부팅 추가 계획과 결과는 `BLUETOOTH_VALIDATION_CHECKLIST.md`에만 기록한다.

## 2026-11-14 엔진오일량 직접 SDK 검증

- 사용자 요청으로 휴대폰 AVD를 종료한 뒤 공식 Pleos Connect v2.1.0/API34/RAM4096MB 프로필을 Cold Boot했다. SDK 2.3.2, Vehicle Service 2.3.22, 현재 사용자10에서 `EngineOilSdkDiagnosticTest`를 실행했다. 기존 앱은 `install -r`로 업데이트했고 실제 차고/정비 기록은 쓰지 않았다. 사용자 작업 중인 디자인 파일 삭제는 이 작업에 포함하지 않았다.
- CAR_SAFETY는 `checkSelfPermission=0` 및 package dump의 granted=true였다. 이 권한은 일반 런타임 grant 대상이 아니어서 `pm grant` 시도는 changeable permission type이 아니라는 오류였으며, 기존 자동 부여 상태를 확인해 진행했다. PRIVATE 권한 부여나 SDK 권한 우회는 하지 않았다.
- `checkEngineOilLevelStateCapabilities`: `Unsupported / ERROR_NOT_SUPPORTED_PROPERTY`.
- `getEngineOilLevelState` 직접 호출: `IllegalArgumentException: property ID is not in carPropertyConfig list, and so it is not supported: ENGINE_OIL_LEVEL`. SDK 오류 래퍼는 현재 Error로 분류하지만 원문은 해당 차량 property 미지원이다. getter를 capability 결과와 무관하게 직접 호출해도 같았다.
- `registerEngineOilLevelState`: 메서드는 반환됐지만 관측 중 정상/부족 값 콜백은 없었다. 등록 메서드 반환을 실제 값 수신으로 판정하지 않는다. 대조 호출 `hasLowWasherFluidWarning`도 property `0x2120071e`가 config 목록에 없다는 오류였다.
- 부팅 직후의 일시적인 초기화 문제와 구별하려고 약 2분 뒤 같은 진단을 재실행했으나 결과는 동일했다. 두 실행 모두 진단 자체는 `OK (1 test)`지만 **정상 값 수신/부족→정상 감지는 통과하지 않았다.** 각 실행 종료 때 listener 해제와 Vehicle.release를 수행했다.
- 같은 환경에서 `TelltaleSdkDiagnosticTest`를 대조 실행한 결과는 `PermissionDenied: getTelltalesInfo need Permission`이었다. 따라서 현재 관측한 차단 원인은 **오일량: AVD의 ENGINE_OIL_LEVEL 미지원 / 경고등 목록: 권한 거절**로 다르다. 별도 API라는 사실만으로 오일량 실수신이 가능하다고 단정하면 안 된다.
- 현재 AVD에서는 ‘보충/교체 후 오일량 정상 복귀’를 검증할 입력이 없다. 해당 property를 제공하는 공식 시험 환경 또는 실차에서 LOW→NORMAL의 실제 getter/listener와 반복 정상 상태를 확인해야 한다. 그래도 오일량 정상 복귀와 교체 완료/교체 주기 초기화는 구별한다.
- 증거: `build/diagnostics/engine-oil/initial-result.json`, `warm-result.json`, `telltale-comparison-result.json` 및 각 계측 로그. 앱 debug/계측 빌드는 통과했다(`build.txt`). 진단 외 production 동작 변경은 없다.

## 휴대폰 현위치: 요청 수명·오래된 응답 처리 보완

- 이전 선택적 검사는 실제 화면을 띄우지 않고 foreground 전용 위치 어댑터를 호출했다. 이제 Compose 화면을 연 뒤 요청한다.
- 원인 재현: 화면을 띄워도 재부팅 없이 두 번째 위치 검사가 실패했다. `dumpsys location`에서 COARSE 요청이 LOW_POWER/600000ms로 바뀌며 마지막 GPS 표본이 그대로 남는 것을 확인했다. Android 14 `LocationProviderManager`도 `MIN_COARSE_INTERVAL_MS = 10 * 60 * 1000` 및 COARSE 요청의 LOW_POWER 강제를 명시한다. 앱의 60초 거부 기준과 OS의 10분 갱신 제한이 충돌했다. 단순히 에뮬레이터 고장으로 단정하지 않는다.
- **휴대폰의 지역 날씨에만** `phoneRegion`과 최대 10분 기준을 추가했다. 1분을 넘긴 위치는 ‘최근 확인 위치 · HH:mm’으로 실제 측정 시각을 표시한다. `TravelPoint.fresh`, 기존 `region`, 차량 위치/세차/주행 증거의 60초 등 기준은 바꾸지 않았다.
- 기존 구현은 모든 공급자의 일회성 결과가 null/오래된 값이면 즉시 null로 끝났다. 이제 허용 범위 내 마지막 위치를 우선 확인하고, 일회성 실패 뒤에도 **기존 20초 예산 안에서만** 새 위치 콜백을 기다린다.
- 콜백과 요청 상태는 Main dispatcher에서 처리한다. 성공/취소/시간 초과 시 일회성 요청과 임시 구독을 모두 해제한다. 새 서비스/무한 재시도/위치 권한 확대는 없다.
- 10분을 넘는 위치, 미래 측정 시각, 잘못된 좌표는 날씨 위치로 사용하지 않는다. 과거 위치의 측정 시각을 현재 시각으로 바꾸지 않는다. 이 위치는 날씨용이며 차량 세차·기어·차속 증거가 아니다.
- OS/공급자가 실제 위치를 주지 않는 경우는 계속 실패로 표시한다. 모든 장시간 GPS 장애가 해결됐다고 보장하지 않는다.
- [Android 14 공식 위치 공급자 코드](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android14-release/services/core/java/com/android/server/location/provider/LocationProviderManager.java)

## Claude 미커밋 변경 검토

- verification 패키지에서만 런처 alias를 실행 전 복구/종료 후 숨기는 runner와 관련 문서 변경을 보존했다. 검사 종료 뒤 패키지 비활성화 규칙도 유지한다.
- 정비 시뮬레이션 알림/장부 정리를 `@After`로 옮겨 단언 실패 때도 실행하도록 보강했다.
- 차량 프레임 재생 검사는 인자를 명시할 때만 실행하며 verification 저장소에서만 동작한다. 시각을 옮긴 재생 결과는 실제 SDK/무선 수신 성공이 아니다.
- 알림음 정책은 이번 변경에서 수정하지 않았다.

## 검증

- 최종 두 앱 단위 검사 127개: 125 통과, 기존 선택적 제외 2, 실패 0. 휴대폰 날씨의 10분 경계/미래 거부/차량 최신성 유지 회귀 검사 포함.
- 두 앱 APK 및 진단/verification APK 빌드 통과.
- Pleos getter 단독 진단: 콜백/보고서 기록 완료, 결과 PermissionDenied. 실시간 경고 캐릭터 전환은 차단 상태.
- 최초 `:app:lintDebug`는 기존 공유 폰 위치 코드의 MissingPermission 2건으로 실패했다. 공유 코드가 위치 권한 없는 차량 모듈에도 포함되는 구조를 확인하고, 실제 위치 권한 검사/예외 처리가 있는 두 진입점에만 해당 lint를 억제했다. 세차 위치 서비스는 새 방문 대상 수신 시에도 FINE 권한을 다시 검사한다. 차량 매니페스트에 폰 위치 권한을 추가하지 않았다. 최종 `:app:lintDebug :phone:lintVerification` 통과 (기존 경고는 남음).
- 실시간 외부 날씨 API 검사는 자동 승인 검토가 위치 외부 전송 가능성을 이유로 거절해 실행하지 않았다. 로컬 위치 수신 검사는 별도로 실행한다.
- 최종 차량/폰 debug, 폰 verification/계측 APK 빌드 및 두 앱 단위 검사 통과. 로그: `build/diagnostics/sdk-location-final-checks.txt`.
- 원인 수정 전: 첫 전체 실행 `OK (48 tests)` 뒤 단독 재검사는 20초 후 실패. 새 좌표 주입만으로도 해결되지 않았다. 이 실패를 남겼고 재부팅으로 숨기지 않았다.
- 원인 수정 후: 같은 AVD를 재부팅하지 않고 가동 508초 상태에서 현위치 단독 검사 `OK (1 test)`, 1.653초. COARSE만 허용, FINE 거부 상태 유지.
- 새 UI 회귀 검사 포함 전체 로컬 계측: `OK (49 tests)`, 실패 0. 실시간 API/시연/외부 프레임 재생은 명시적으로 켜지 않아 제외. 로그: `build/diagnostics/approximate-location-full.txt`.
- 같은 AVD를 계속 켠 가동 751초 상태에서 위치 단독 재검사 `OK (1 test)`, 6.153초. 실제 GPS 마지막 표본은 가동 231초로 유지돼, 새 GPS 수신이 아니라 최근 10분 예보 위치 정책으로 처리된 결과다. 검사 종료 후 GPS/fused provider 요청 OFF 확인.
- `forecast-approximate.png` 실제 검증 앱 스크린샷에서 ‘최근 확인 위치 · 16:17’ 및 예보 표시 확인. 시각 표시 회귀는 저장한 예보/명시적 공개 시험 좌표를 사용하며 외부 날씨 조회 없음.
- 최종 APK 재설치(-r) 뒤 위치/예보 UI/정비 알림 정리 재검사 `OK (6 tests)` (실시간 API 선택 검사는 제외), 잔여 verification 시스템 알림 0. verification 패키지는 다시 disabled-user 처리, 사용자 데이터 보존. 실행 중인 AVD는 폰 하나만 남겼다.
