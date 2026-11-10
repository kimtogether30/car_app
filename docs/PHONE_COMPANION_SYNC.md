# 휴대폰 지도 검색과 차량 데이터 동기화

2026-11-10 갱신: 현재 관리 기능은 [정비·세차 검증 결과](CARE_VALIDATION_RESULTS.md)를 기준으로 한다. 아래의 기존 단방향·읽기 전용 설명은 이전 구현 기록이다. 현재 v2 경로는 차량 수집 자료에 휴대폰 ACK/설정·기록 버전 응답을 연결하며, 휴대폰이 관리 기준을 확정한다. 실제 Bluetooth 전송은 실물 장치가 없어 아직 검증하지 않았다.

## 현재 동작

- 휴대폰의 주유소·세차장·정비소 배너는 최근 관리 카드와 같은 폭을 사용합니다. 설명 문구 중 가장 긴 레이아웃의 높이를 화면 폭·글자 크기별로 한 번 계산해 종류를 바꿔도 높이가 변하지 않습니다. 애니메이션·폴링·추가 지도 SDK는 사용하지 않습니다.
- 배너 전체를 누르면 차량 앱과 같은 지도 앱 선택 창이 열립니다. 차량과 검색 URL scheme을 공유하는 네이버지도·카카오맵 중 설치·활성화되어 검색 실행이 가능한 앱만 표시합니다. 기본 지도 앱이 지정돼 있어도 베모링 선택 창을 먼저 엽니다. 앱이 없으면 설치 안내를 표시합니다. 차량 기본 Pleos 내비게이션은 휴대폰 목록에 넣지 않습니다.
- 차량→휴대폰 단방향으로 프로필, 세차·주유·엔진오일 기록, 관리 기준, 연료·배터리·청결도, 오일 교체 잔여 거리, 캐릭터·말풍선, 예시 데이터 여부를 전송합니다. 픽셀 화면 미러링이나 차량 제어 명령은 아닙니다.
- 휴대폰은 전체 수신·검증·로컬 저장에 성공한 데이터만 반영합니다. 연결 단절·부분 프레임·잘못된 데이터는 마지막 정상 데이터를 지우지 않습니다. 앱 재실행 시에도 캐시를 읽고 연결 중으로 오인하지 않도록 마지막 수신 시각과 단절 상태를 표시합니다.
- 차량 데이터 보기에서는 차량이 원본이므로 휴대폰에서 수정하지 않습니다. `데이터 연결 → 휴대폰 기록 보기`로 전환하면 기존 휴대폰 전용 기록과 프로필을 계속 편집할 수 있습니다. 기존 데이터를 원격 스냅샷으로 덮어쓰거나 서로 다른 차량의 기록을 합치지 않습니다.

## 연결 방법

1. 양쪽 시스템 블루투스 설정에서 차량과 휴대폰을 먼저 페어링합니다.
2. 업데이트한 차량 앱의 `내 차 → 휴대폰 연결 → 블루투스로 휴대폰 연결`을 열고, 주변 기기 권한을 허용한 뒤 해당 휴대폰을 선택합니다.
3. 휴대폰의 `홈 → 데이터 연결 → 블루투스 차량 연결`에서 주변 기기 권한을 허용하고 해당 차량을 선택합니다.
4. 차량 앱 화면을 열어두면 화면 데이터가 변할 때 전송합니다. 휴대폰은 첫 정상 스냅샷을 받아 저장한 뒤에만 데이터 연결됨으로 표시합니다.
5. 연결이 끊기면 마지막 데이터를 그대로 표시합니다. 사용자가 시작한 연결 서비스가 실행 중이면 선택한 기기의 Bluetooth ACL 재연결 이벤트로 한 번 재접속합니다. 서비스 종료·권한 해제·차량 앱 재시작 등으로 자동 연결되지 않을 때는 연결 화면에서 기기를 다시 선택합니다.
6. 연결 화면 또는 연결 알림의 `연결 종료`로 서비스를 끝낼 수 있습니다. 캐시는 삭제하지 않습니다.

일반 오디오 Bluetooth 연결만으로 앱 데이터를 읽을 수는 없습니다. 양쪽 앱이 같은 보안 RFCOMM 서비스 UUID를 사용해야 합니다. 주변 기기 목록은 페어링된 기기만 사용하고 검색·스캔·위치 권한은 요청하지 않습니다. 서버는 사용자가 선택한 페어링 상대의 연결만 허용합니다. 프로필·기록을 외부 서버에 업로드하지 않습니다.

## 구현과 자원 사용

`companion/src/main/kotlin`은 두 Android 앱이 함께 컴파일하는 공유 Kotlin 소스입니다. 추가 런타임 라이브러리나 Graphify 의존성은 없습니다. SDK 버전과 Pleos AVD 설정은 변경하지 않았습니다.

- `MirrorBluetoothService`: 보안 RFCOMM 서버/클라이언트, `connectedDevice` 포그라운드 서비스. `accept`/`read`는 IO dispatcher에서 블로킹하며 변경 이벤트만 전송합니다. 초기 연결 15초 제한, 단일 상대·단일 소켓, 명시적 종료, 시스템 연결 이벤트로 재접속합니다. 무한 재시도·주기적 폴링·하트비트는 없습니다.
- `MirrorSnapshot`/`MirrorFrames`: schema 1, 설치별 차량 ID와 단조 증가 revision, 1 MiB 이하 길이 프레임. 모든 필드를 검증한 후 적용하고 동일 차량의 중복/과거 revision은 무시합니다. 차량 안전 제어 API는 사용하지 않습니다.
- `MirrorBus`/`MirrorCacheStore`: JSON 처리 및 동기식 성공 확인 저장은 IO dispatcher에서 수행합니다. snapshot과 수신 시각을 한 번의 SharedPreferences commit으로 기록합니다. 링크 상태와 마지막 데이터는 분리합니다.
- `VehicleMirror.kt`: 현재 차량 UI가 사용하는 값만 추출합니다. 관련 없는 차량 신호 갱신이나 단순 재구성에는 다시 직렬화하지 않고, 실제 표시 값의 변경에 맞춰 직렬화를 IO dispatcher에서 수행합니다.

차량 쪽 화면 데이터 생성은 현재 차량 앱 화면의 상태 수집 수명에 따릅니다. 차량 앱이 종료되거나 OS가 프로세스를 종료하면 새로운 차량 UI 데이터를 만들 수 없습니다. 휴대폰은 마지막 수신 시각을 계속 표시합니다. 차량 앱 종료 후에도 차량 SDK를 계속 읽는 기능이나 휴대폰→차량 양방향 편집은 이번 범위에 포함하지 않았습니다.

## 검증과 제한

2026-11-09 공식 문서를 확인했습니다.

- [Android Bluetooth 연결](https://developer.android.com/develop/connectivity/bluetooth/connect-bluetooth-devices): 양쪽 앱의 서버·클라이언트 구현 및 같은 UUID 필요.
- [Bluetooth 권한](https://developer.android.com/develop/connectivity/bluetooth/bt-permissions): 페어링 기기 통신에는 Android 12+ `BLUETOOTH_CONNECT` 런타임 권한 사용.
- [포그라운드 서비스 유형](https://developer.android.com/develop/background-work/services/fgs/service-types#connected-device): `connectedDevice` 유형과 관련 권한 사용.
- [Pleos 에뮬레이터 공식 문서](https://document.pleos.ai/en/docs/connect/guide/getting-started/application-development/configure-pleos-connect-emulator): 해당 버전은 Bluetooth 동글 연동 미지원. 실제 Pleos 차량의 앱용 RFCOMM 접근 가능 여부와 실물 두 기기 사이 통신은 아직 검증하지 못했습니다. 에뮬레이터 연결 성공을 실차 연결 성공으로 간주하지 않습니다.
- [네이버 지도 URL scheme](https://guide.ncloud-docs.com/docs/maps-url-scheme), [카카오맵 URL scheme](https://apis.map.kakao.com/ios_v2/docs/getting-started/urlscheme/): 검색어 전달 규칙 참고. 설치된 실 지도 앱에서 실제 검색 결과가 뜨는 최종 외부 앱 연동은 실기기에서 추가 확인해야 합니다.

공유 프로토콜 테스트는 한국어 프로필·기록, unknown/null 상태, 캐릭터, 중복/역순 프레임, 부분·과대·잘못된 프레임에서 마지막 데이터 유지, 재로드를 확인합니다. 차량 publisher 테스트는 실제 UI 모델과 전송 값이 일치하는지 확인합니다. 휴대폰 기기 테스트는 배너 폭·높이와 큰 글씨, 선택 카테고리와 지도 인텐트, 실제 저장소 재로드를 확인합니다.

기기 테스트는 `com.example.vemoring.phone.verification` 전용 앱에서 실행합니다. 테스트 도구가 종료 시 앱을 제거하므로 기본 앱 ID로 기기 테스트를 실행하지 않습니다. 기본 `com.example.vemoring.phone` 데이터는 테스트 전후 별도 대조합니다.

이번 최초 기기 테스트에서 기본 앱이 자동 제거되는 문제가 발생해 마지막 확인본의 휴대폰 프로필·기록을 복원했습니다. 이후 verification 앱으로 분리한 테스트 전후에 기본 앱의 저장 파일이 동일한 것을 대조했습니다. 로컬 검증 스크린샷과 기기 데이터 백업은 `.tmp/`에 두고 Git 및 소스 패키징 대상에서 제외합니다.

```powershell
.\gradlew.bat :phone:assembleDebug :app:assembleDebug :phone:testDebugUnitTest :app:testDebugUnitTest :phone:lintDebug :app:lintDebug :phone:connectedVerificationAndroidTest --offline
```
