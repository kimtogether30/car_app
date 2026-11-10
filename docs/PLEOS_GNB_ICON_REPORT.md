# Pleos GNB 상태 아이콘 갱신 조사

2026-11-09 업데이트 / Pleos Connect 2.1.0 / API 34 / 2560×1440 / RAM 4 GB.

## 확인된 현상

- 차량 데이터 수신과 상태 판정은 앱을 벗어난 뒤에도 동작한다.
- PackageManager에서 상태별 activity-alias를 전환하면 앱 목록은 새 아이콘을 표시한다.
- GNB 최근 앱 영역은 이전 파란 아이콘을 유지한다.
- API 33의 TaskDescription.Builder.setIcon(int)도 시험했다. dumpsys activity에
  face_angry 리소스 ID가 적용된 것을 확인했지만 GNB는 파란색을 유지했다.
- 이 결과는 GNB 표시가 공개 TaskDescription 변경을 그대로 따르지 않는다는
  근거이며, GNB 내부 저장 방식이나 모든 실제 차량의 동작을 단정하는 근거는 아니다.
- 고정 슬롯 등록에 성공했다. 편집 중 빈 슬롯의 위치가 변하며, 이전 제스처의
  목적지가 슬롯 사이 간격이었다. 에뮬레이터 창 마우스 입력으로 실제 빈 슬롯의
  중심에 놓고 완료를 눌렀다. 앱 목록 버튼 왼쪽에 VEMORING이 유지되고 최근 앱
  영역의 중복 항목은 사라지는 것으로 고정 등록을 확인했다.
- 이전 시험에서 이동한 전화 바로가기도 고정 영역에 복원했다. 현재 중앙 순서는
  차량 설정 → 내비게이션 → 전화 → VEMORING → 앱 목록이다.
- 고정 후 연료 부족(노랑)과 두 가지 문제(빨강)를 시험했다. 앱 목록은 각각
  노랑/빨강으로 바뀌었지만, 고정 슬롯은 파랑을 유지했다. 따라서 이 에뮬레이터에서는
  최근 앱뿐 아니라 실제 고정 슬롯에서도 상태 아이콘 갱신이 되지 않는 것이 확인됐다.
- 상태 전환으로 기존 LauncherNormal이 비활성화된 동안 파란 고정 아이콘을 눌러도
  앱이 열리지 않았다. 고정 항목이 이전 alias를 계속 참조하는 현상과 일치한다.
  내부 저장 방식은 미확인이다. 테스트 override는 해제했다.
- 실제 데이터와 LauncherNormal 복구 후 같은 고정 아이콘을 누르면 VEMORING
  홈 화면이 열리는 것을 확인했다. 고정 배치는 유지한다.

## 근거 자료

- `build/diagnostics/gnb-task-description-probe.png`: 빨간 상태가 전달된 뒤에도
  내비게이션과 함께 표시되는 GNB 아이콘은 파란색이다.
- `build/diagnostics/icon-monitor-list.png`: 앱 목록은 상태 아이콘을 반영한다.
- `build/diagnostics/gnb-pinned-normal.png`: VEMORING과 전화의 실제 고정 배치.
- `build/diagnostics/gnb-pinned-fuel.png`: 앱 목록 노랑, 고정 슬롯 파랑.
- `build/diagnostics/gnb-pinned-angry.png`: 앱 목록 빨강, 고정 슬롯 파랑.
- TaskDescription 실험 코드는 효과가 없어 제거했다. 제스처 진단도 일반 회귀
  테스트에서 사용자 런처 배치를 바꾸지 않도록 제거했다.
- 현재 HOME은 ai.umos.homescreen/.HomeActivity, 하단 창은
  com.android.systemui의 BottomCarSystemBar로 확인했다.
- 시스템 UI APK 읽기는 기기 권한으로 차단됐다. 권한 상승이나 시스템 변경은 하지 않았다.

## 공식 문서와 미확인 사항

- GNB 편집/최근 앱 구분:
  https://document.pleos.ai/en/docs/connect/guide/docs-design/components/gnb
- TaskDescription 아이콘 API:
  https://developer.android.com/reference/android/app/ActivityManager.TaskDescription.Builder#setIcon(int)
- 확인한 공개 문서에서는 일반 앱이 기존 GNB 항목의 아이콘을 실시간으로
  다시 그리게 하는 API를 찾지 못했다. API가 존재하지 않는다고 확정한 것은 아니다.

## Pleos 기술 문의용 초안 (미발송)

### 권한 실물 조사 추가 (2026-11-09)

공식 GNB 문서와 권한 가이드, 실제 AVD의 `pm list permissions -f`,
AppLibrary/SystemUI/HomeScreen/UI Service의 PackageManager 덤프를 대조했다.
권한 부여, 시스템 설정 변경, 비공개 서비스 호출은 하지 않았다.

| 실제 선언된 권한 | 보호 수준 | 이번 조사에서 확인된 한계 |
| --- | --- | --- |
| ai.umos.applibrary.permission.WATCH_EDIT_MODE | signature | 편집 관련 이름의 내부 권한이며, 아이콘 교체 API임을 확인한 것은 아님 |
| ai.umos.applibrary.permission.CONTROL_GNB_HVAC | signature | GNB 공조 관련 이름의 권한. 앱 아이콘 교체 기능 근거 없음 |
| android.permission.CUSTOMIZE_SYSTEM_UI | signature\|privileged | 일반 사용자 승인으로 받을 수 있는 위험 권한이 아님. 고정 아이콘 갱신을 허용하는 API인지는 미확인 |
| ai.umos.homescreen.permission.CONTROL_WINDOW | signature\|privileged | 창 제어 관련 이름이며 GNB 고정 항목 교체 기능 근거 없음 |
| com.android.launcher.permission.INSTALL_SHORTCUT | normal | 존재하지만 Pleos GNB 고정 슬롯을 교체한다는 근거 없음 |

`signature`는 권한 선언 앱과 동일한 인증서 등 시스템 조건으로 부여되며
사용자 동의 창으로 취득할 수 없다. `signature|privileged` 역시 일반 위험 권한의
허용 버튼과 다르다. 일반 사이드로드/마켓 앱을 사용자 승인만으로 시스템 특권 앱으로
만들 수는 없다.

정상 권한인 MANAGE_TASK, ENFORCE_APP_ORIENTATION도 존재하지만, 이 이름만으로
고정 아이콘 교체를 허용한다고 해석하지 않았다. 저장된 SDK AAR 9개, 클래스 1085개를
이름 기준으로 검색했으며 GNB/Launcher/Shortcut/SystemUI/HomeScreen 제어 클래스를
찾지 못했다. 발견된 Favorite 클래스들은 내비게이션의 집/직장 즐겨찾기 관련이었다.
이 검사는 설치된 SDK의 범위에 한정되며 모든 비공개/향후 SDK의 부재 증명은 아니다.

결론: 현재 공개 문서·설치 SDK·AVD에서 **이용자 동의로 받을 수 있는 GNB 앱 항목
자동 교체/아이콘 갱신 전용 권한은 확인되지 않았다.** 관련 시스템 권한의 존재와
일반 앱이 사용할 수 있는 고정 바 교체 API의 존재는 구분해야 한다.

근거 파일 (개발 진단용, Git 미포함):
`build/diagnostics/pleos-declared-permissions.txt`,
`pleos-systemui-package.txt`, `pleos-applibrary-package.txt`,
`pleos-homescreen-package.txt`, `pleos-ui-service-package.txt`.

권한 보호 수준 공식 설명:
https://developer.android.com/guide/topics/manifest/permission-element#plevel

### 문의 본문

일반 차량 앱이 연료/관리 상태에 따라 GNB에 표시되는 자신의 앱 아이콘을
갱신하려 합니다. activity-alias 전환은 App Library에 반영되지만 GNB 최근 앱
아이콘 및 실제 고정 슬롯에는 반영되지 않습니다. 고정 슬롯에 LauncherNormal을
등록한 뒤 LauncherFuel/LauncherAngry로 전환하면 App Library는 노랑/빨강이지만
고정 슬롯은 파랑입니다. 기존 alias를 비활성화하면 고정 바로가기로 앱도 열리지
않습니다. TaskDescription.setIcon도 GNB에 반영되지 않습니다.

1. GNB 기존 아이콘 갱신을 요청하는 공개 API 또는 지원 SDK가 있나요?
2. 일반 앱에서 해당 기능이 지원되나요? 별도 권한/심사가 필요한가요?
3. 실행 컴포넌트를 변경하지 않고 기존 고정 슬롯의 아이콘만 갱신하는 지원 방법이 있나요?
4. 에뮬레이터 2.1.0에서 알려진 제한이며 향후 버전에서 수정되는 사항인가요?

응답 또는 새 공식 SDK가 없으면 현재 상태를 해결 완료로 표시하지 않는다.
시스템 UI 재시작, 런처 데이터 초기화, 반복 재설치, 자동 UI 조작을 제품 해결책으로
사용하지 않는다. 기존 차량 정보/관리 기록은 유지한다.
