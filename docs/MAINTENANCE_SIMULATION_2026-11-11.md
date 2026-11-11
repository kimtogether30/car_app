# 정비 기능 에뮬레이터 시뮬레이션 — 2026-11-11

휴대폰 및 차량 에뮬레이터를 모두 실행하고 최신 앱을 `install -r`로 업데이트했다. 프로필 초기화, 데이터 삭제, 사용자 기록 추가·삭제는 하지 않았다.

## 환경

- 차량: 기존 Pleos Connect v2.1.0 / API 34, 2560×1440, RAM 4096 MB, Cold Boot. 프로필 변경 없음.
- 휴대폰: 기존 Vemoring Phone API 34 AVD, RAM 2048 MB. 프로필 변경 없음.
- 빌드 후 Gradle 데몬을 종료해 메모리를 반환했다. 종료 시 두 에뮬레이터와 일반 앱은 실행 상태다.
- 공식 설정 확인: [설치 및 실행](https://document.pleos.ai/en/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator), [Cold Boot 및 Bluetooth 제한](https://document.pleos.ai/en/docs/connect/guide/getting-started/application-development/configure-pleos-connect-emulator).

## 확인 결과

| 검사 | 결과 |
| --- | --- |
| 차량 실제 정비 입력 화면에서 공기압·엔진오일 항목 선택 및 저장 콜백 | 통과 — 메모리 내 시험 기록, 실제 차고에 저장하지 않음 |
| 차량 기록 JSON 왕복 시 ID·정비 항목 유지 | 통과 |
| 휴대폰 실제 편집 화면에서 공기압·엔진오일 항목 선택 및 저장 | 통과 — 별도 `.verification` 앱 |
| 공기압 기록 알림 | ‘정비 기록을 저장했어!’ 게시 및 내역 확인 |
| 명시적 엔진오일 교체 기록 알림 | ‘엔진오일 교체 완료!’ 게시 및 내역 확인 |
| 같은 변경 재전송 시 알림 내역 중복 방지 | 통과 — 한 실행당 기록 2건/내역 2건 |
| 휴대폰 시험 저널 재개방 시 세부 항목 유지 | 통과 |
| 실제 Pleos 전체 화면 정비 입력창 | 직접 조작·캡처 확인, 선택 후 취소하여 실제 기록 변경 없음 |
| 일반 휴대폰 앱 재실행 및 차량 앱 실행 | 정상 화면 확인 |

최종 계측 테스트는 양쪽 각 1개가 통과했다. 휴대폰 검사는 두 번 실행했고 각 실행은 별도 차량 ID를 사용했다. 알림 캡처의 4개 항목은 두 실행의 2개 알림씩이며, 단일 실행 중복이 아니다. 시험 완료 후 검증 앱을 종료해 시험 시스템 알림을 정리했다.

## 관측된 문제와 범위

- 최초 동시 부팅·빌드·앱 실행 과정에서 일반 휴대폰 앱의 입력 응답 시간 초과가 1회 발생했다. 스택은 `HardwareRenderer.setStopped` / `RenderProxy::setStopped` 대기를 보였다. 앱 재실행 후 정상 표시됐고 최종 로그에는 추가 ANR이 없었다. 근본 원인을 해결했다고 단정하지 않는다.
- Pleos 부팅 중 시스템 UI 및 차량 시스템 서비스의 예외·재시작이 기록됐다. VEMORING의 차량 앱 예외는 수집한 이벤트 로그에서 발견되지 않았다. SDK/VHAL 정상 수신 검증으로 해석하지 않는다.
- Pleos의 `UiAutomation.takeScreenshot`은 null 오류를 냈고 Compose 노드 캡처는 빈 이미지를 만들었다. 따라서 테스트 내부의 차량 이미지는 시각 증거로 사용하지 않는다. 실제 일반 앱을 연 뒤 UI 구조를 읽고 확인된 항목만 선택하여 `adb screencap`으로 다시 캡처했다.
- 실제 Bluetooth 무선 및 차량 신호 전달은 검증하지 않았다. BT-50(원격 인계 통합 시 중복 번호 정리)은 기존 [Bluetooth 검증 목록](BLUETOOTH_VALIDATION_CHECKLIST.md)에 남겨 둔다.

## 로컬 증거

생성물은 Git에서 제외된 `build/diagnostics/maintenance-20261111/`에 있다.

- `vehicle-test-final.txt`, `phone-test-recheck.txt`: 최종 계측 테스트 결과.
- `vehicle-maintenance-live.png`, `vehicle-oil-live.png`: 실제 Pleos 정비 입력 화면.
- `phone-tire-recheck.png`, `phone-oil-recheck.png`: 휴대폰 정비 입력 화면.
- `phone-notifications-recheck.png`: 실제 시스템 알림.
- `vehicle-app-final.png`, `phone-app-final.png`: 일반 앱 실행 화면.
- `phone-anr-trace.txt`, `vehicle-final-events.txt`, `phone-final-events.txt`: 초기 문제와 최종 로그.
