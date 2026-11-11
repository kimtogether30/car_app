# 최적화·오류 수정 검토

기준 커밋: `2a51636`(2026-11-10). 최근 최적화 `0ba4d40`, 휴대폰/관리기록 변경 `90cf848`, 새 연료 정책 문서를 확인했다. 사용자가 미정이라고 한 **정비 완료 판정은 변경하지 않았다.** 연료 정책도 이번 요청에서는 검토만 하고 새 동작을 적용하지 않았다.

## 수정한 결함

1. **아이콘 전환 후 기존 알림 탭 실패.** `PhoneLauncher`가 예전 launcher alias를 비활성화하는데 기존 `BannerNotifications`의 PendingIntent가 그 alias를 가리켰다. verification 앱에서 정상 아이콘으로 알림 게시→주유 아이콘 전환→기존 알림 실행 시 `PendingIntent.CanceledException`을 재현했다(`build/diagnostics/banner/alias-before.log`). 새 `notificationActivityIntent`는 alias의 실제 targetActivity를 확인해 앱 본체에 연결하며 applicationId의 verification 접미사로 클래스명을 추측하지 않는다. 연결 서비스의 알림 진입점도 같은 함수를 사용한다. 아이콘 전환 도중 기존 alias가 꺼져도 target을 읽을 수 있도록 MATCH_DISABLED_COMPONENTS를 사용한다. 수정 후 알림 진입+아이콘 검사는3개 통과(`alias-after.log`).
2. **차량 연결 화면에서 연료 알림 기준 저장 누락.** 휴대폰의 차량 설정 저장 경로가 washAlert는 복사했으나 fuelAlert를 복사하지 않았다. 기존 설정 기능의 저장 필드만 보완했다. 실제 MainActivity의 내 차 화면 슬라이더로20%→25% 변경 후 차량별 CareAuthority에25가 저장되고 washAlert30이 유지되는 검사 통과(`fuel-setting-review.log`). 이는 새로운 HEV 재알림 정책 구현이 아니다.
3. **런처 코드의 lint 오류3개.** `desired()` 지역 함수가 반환한 Int에 대해 Android lint가 유효한 enabled-state 상수임을 추론하지 못해 WrongConstant 오류3개로 검사에 실패했다. 실제 Android 아이콘 검사는 통과했던 부분이며, 잘못된 숫자가 사용됐다는 뜻은 아니다. 호출 지점에 동일한 ENABLED/DISABLED 상수를 직접 표현해 정적 검사 오류를 보완했다. 검사 억제나 baseline 추가는 하지 않았다.

## 새 연료 기준 확인

[HEV_FUEL_ALERT_POLICY.md](HEV_FUEL_ALERT_POLICY.md)는 문서만 반영된 상태이며 현재 동작과 충돌하는 부분을 명시한다.

- 일반 HEV는 정수 연료 잔량만 사용. HEV SOC와 LOW_FUEL 경고 대체 경로 제외. EV는 배터리 기준 별도.
- 유효0~100 정수를 그대로 표시·전달. 미수신/오류는0이나 정상으로 바꾸지 않음.
- 사용자 임계값 T 이하에서 알림. 캐릭터는 T 초과 시 해제하되 알림 재무장은 별개.
- T+10%p 이상을 별도 연료 SDK 조회 성공3회, 인정 응답 간격30초 이상/전체60초 이상 확인한 뒤 재무장. 구독 콜백·Bluetooth 중복·재전송을 조회 성공으로 세지 않음.
- 미완료 회복은 단절/오류/하향 수신/임계값 변경 시 중단. 세션·요청ID와 완료 상태 복원 필요. 무한 조회 금지.
- PHEV·수소차는 새 선택 목록에서 제외하되 기존 기록은 보존.

현재 `CareCondition`과 `BannerNotifications`에는 HEV SOC/LOW_FUEL 경로가 남아 있고, 별도 연료 조회 기반 회복 상태기도 없다. 새 정책이 구현됐다고 보고하지 않는다.

## 정비 완료 미정 항목

`90cf848`은 기존 oil 기록의 표시명을 ‘정비’로 바꿨으며 wire 값 oil을 유지했다. 반면 `BannerNotifications.recordChanges`에는 수동 oil 기록→OIL_DONE(엔진오일 교체 완료) 경로가 남아 있다. 일반 정비와 엔진오일 교체의 의미가 같아진 것은 아니다. 사용자가 완료 기준을 아직 정하지 않았으므로 이 연결/판정은 이번에 변경하지 않고 남은 검토 항목으로 기록한다. 경고 정상화·단절로 정비 완료를 추정하는 새 규칙은 추가하지 않았다.

## Bluetooth 플랫폼·검증 범위

현재 코드는 Android Kotlin 앱과 secure RFCOMM 소켓이다. iOS 앱/같은 서비스용 iPhone 전송 경로는 없다. 아이폰은 Bluetooth 자체가 불가능한 것이 아니라 현재 APK와 RFCOMM 데이터 채널의 대체 시험 장치가 아닌 것이다. iPhone 지원에는 iOS 앱과 양쪽에서 호환되는 전송 경로(예: BLE GATT), 백그라운드·권한·메시지 분할·보안/ACK 재검증이 필요하다. Pleos의 BLE 서버/광고 등 실제 기능도 별도로 확인해야 한다. Apple의 External Accessory는 MFi 액세서리용이며 일반 RFCOMM 소켓을 그대로 교체하는 API로 취급하지 않는다.

- [Apple Core Bluetooth](https://developer.apple.com/documentation/corebluetooth)
- [Apple External Accessory](https://developer.apple.com/documentation/externalaccessory)
- [Pleos Cold Boot·Bluetooth 동글 제한](https://document.pleos.ai/en/docs/connect/guide/getting-started/application-development/configure-pleos-connect-emulator)

현재 연결된 장치는 API34 휴대폰 에뮬레이터다. 실물 Android 휴대폰이 생겨도 Pleos AVD의 동글 미지원 제한은 별개이며, 실제 차량 앱→무선→휴대폰 전체 경로를 이 에뮬레이터 검사만으로 통과시키지 않는다. [일괄 점검 목록](BLUETOOTH_VALIDATION_CHECKLIST.md)에 새 아이콘/알림 항목 BT-44를 추가했다.

현재 커밋의 JVM 검사: 차량30개·휴대폰28개, 실패/오류0. 차량·휴대폰 debug 및 verification 검사 APK 빌드 통과. SDK/AVD 사양·Gradle worker 설정은 변경하지 않았다. Android 결과는 `build/diagnostics/banner/review-android-final.log`에서 별도 확인한다. 실제 무선/아이폰 검증은 실행하지 않았다.

최종 Android 종합 검사 **21개 통과**,63.265초. 기존 알림·내역·세차 취소·동기화 검사와 새 아이콘/설정 검사를 함께 실행했다. 시스템 알림창 애니메이션 완료를 기다리고 singleTop Activity 재사용도 인정하도록 기존 알림 탭 검사를 보완했으며, 해당 차량/이벤트와 내역 읽음 저장 조건은 유지했다. 이후 lint 상수 표현 보완 후 아이콘·알림 검사3개를 다시 실행해 **통과**,4.997초(`review-launcher-final.log`).

최종 `review-final-build-lint.log`: 빌드·양쪽 lint 통과. lint 오류0(차량 경고52개, 휴대폰 경고20개는 별도 보고서 유지). 운영 휴대폰 APK는 `install -r`로 갱신했다. 설치 전 phone_ui_v1.xml은 없었으므로 데이터 해시 동일 주장은 하지 않으며, 삭제/초기화/제거 명령은 사용하지 않았다. 검사는 별도 verification 앱에서 수행했다.
