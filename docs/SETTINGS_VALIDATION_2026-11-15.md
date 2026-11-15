# 설정 OFF/ON 동작 검증 — 2026-11-15

## 결과

차량용 4개, 휴대폰용 3개 instrumentation 검증 통과. 이번 범위에서 설정에 따른 기능 차단·재개 오류는 발견하지 못했다.

| 대상 | 실제 검증 | 결과 |
| --- | --- | --- |
| 차량 관리 알림 | 내 차의 실제 스위치 OFF/ON, GarageStore 저장, outgoing 차량 프레임의 notifications=false 확인 | 통과 |
| 차량 아이콘 갱신 | 실제 스위치 저장, 다른 앱이 보이는 동안 연료 부족 시험 상태 주입, OFF에서 정상 아이콘 유지, ON에서 연료 아이콘으로 변경 | 통과 |
| 차량 수신과 아이콘 독립성 | 아이콘 OFF, 수신 ON 상태에서 다른 앱으로 이동한 뒤 실제 Pleos SDK SOC 갱신과 배경 프레임 발행 확인 | 통과 |
| 차량 백그라운드 수신 | 실제 스위치 OFF에서 서비스 종료, ON에서 재시작. 숨겨진 상태에서 수신 OFF 후 공유 SDK 소유자 해제와 위치 연결 종료 확인 | 통과 |
| 차량 상태 아이콘 | 실제 내비게이션 화면에서 6개 상태와 정상 복귀, 런처 항목 1개, 앱을 앞으로 띄우지 않고 변경 | 통과 |
| 휴대폰 관리 알림 | 실제 내 차 스위치 OFF → 저장된 설정으로 알림 평가 → Android activeNotifications에 새 알림 없음. 상태·완료·확인 배너 13종 Disabled 확인 | 통과 |
| 휴대폰 설정 유지·재개 | Activity 재생성 후 OFF 유지, 실제 스위치 ON 후 Android 알림 게시와 Delivered 기록 확인 | 통과 |
| 차량 설정 수신 | 차량 형식의 OFF 프로필과 ON 변경 요청을 휴대폰 수신 경로로 전달, 실제 알림 차단·재개 확인 | 통과 |
| 권한 차단 | 알림 OFF 및 권한 거부 시 성공으로 기록하지 않고 각각 Disabled/PermissionBlocked 이력 유지 | 통과 |

## 환경과 범위

- 차량: 공식 Pleos Connect v2.1.0/API 34 AVD, 기존 프로필·4 GB RAM, Cold Boot. 휴대폰 AVD와 동시에 실행하지 않음.
- 휴대폰: Vemoring_Phone_API_34 AVD. com.example.vemoring.phone.verification에서만 시험 차량과 시스템 알림 사용. 실제 휴대폰 앱의 차고·기록은 수정하지 않음.
- 차량 UI 검증은 기존 차고를 덮어쓰지 않고 설정만 조작한 뒤 원래 설정을 복원. 휴대폰 시험 차량은 고유 ID로 만들고 정리.
- 아이콘 상태 주입은 debug 시험 입력이다. 수신 유지 검사의 SOC는 Pleos SDK/VHAL의 실제 에뮬레이터 콜백이다. 실차 센서·물리 Bluetooth 성공으로 해석하지 않는다.
- 차량 OFF/ON 요청은 실제 앱 수신·평가·Android 알림 경로를 사용하되 무선 전송 구간은 시험하지 않았다.
- 수신 OFF는 **백그라운드 수신**을 끄는 설정이다. 앱을 보고 있을 때의 정상 화면용 SDK 연결까지 끄는 설정이 아니다.
- 알림 OFF 검증은 **새 알림 게시 차단**이다. 기존 알림을 모두 삭제하거나 알림 이력을 지우는 동작을 의미하지 않는다.

## 재실행 및 증거

차량 UI와 다른 앱 전환 검사는 별도 instrumentation 세션으로 실행한다.

1. com.example.vemoring.SettingsBehaviorTest — 1개 통과, 11.668초.
2. com.example.vemoring.BackgroundIconMonitorTest — 3개 통과, 23.012초.
3. com.example.vemoring.phone.SettingsBehaviorTest 및 BannerNotificationSimulationTest#disabledAndBlockedDeliveriesRemainInHistoryWithoutSuccessClaims — 3개 통과, 6.149초.

원본 결과: `.tmp/settings-verification/vehicle-ui-test.txt`, `vehicle-background-test.txt`, `phone-test.txt`.

휴대폰 runner는 `com.example.vemoring.phone.VerificationRunner`를 사용한다. 종료 후 검증 패키지는 다시 disabled-user로 돌린다.

## 검증 중 확인한 환경 문제

휴대폰 AVD Cold Boot에서는 netd가 libnetd_resolv.so를 찾지 못하고 zygote가 반복 재시작해 Android package/activity 서비스가 준비되지 않았다. 앱 실행 이전의 AVD 시스템 오류이다. 데이터 초기화 없이 기존 저장 부팅 상태로 실행해 위 검증을 완료했다. Cold Boot 시스템 문제 자체를 수정한 것으로 보고하지 않는다. Pleos는 이 문제 없이 Cold Boot 검증을 완료했다.

제품의 기능 동작 변경은 없으며, 실제 스위치를 안정적으로 선택할 수 있도록 세 설정의 Compose testTag와 회귀 검증을 추가했다.
