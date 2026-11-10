# 정비·세차 기준 구현 및 검증 기록

2026-11-10 (한국 시간). 정책 원본: origin/main `18ec540`의 인계 문서 5개.
로컬 작업을 강제 초기화하거나 전체 main을 병합하지 않고 문서를 받아 기존 차량 앱과 `phone/` 휴대폰 앱에 적용했다. 인계 문서의 `mobile-app/`과 이 PC의 기존 `phone/`을 혼동하지 않는다.

**전체 실사용 연동 완료가 아니다.** 실제 SDK 응답, 입력을 주입한 계산/Android 테스트, 실물 Bluetooth 전송을 구분한다. 아래 제한을 해결하기 전 자동 세차 감지와 미검증 정비 경고 알림은 활성화하지 않는다.

## 2026-11-10 추가 지시 반영: 시설별 기준과 확인 배너

- 업로드된 시설별 조건을 유지한다. 컨베이어식은 N+실제 이동→D/출차, 정차식은 확인된 정차 체류180초→차량 출차이다. **P 기반 정차식에 N 조건을 강제하지 않는다.** 주유소 전체 영역/POI·단순 주유·P/N만으로는 세차 구역·실제 정차 조건을 채웠다고 보지 않는다.
- 조건을 충족한 추정은 `observeWashVisit`에서 즉시 기록/청결도에 반영한다. 실제 물세차 여부의 애매함을 추가 보류 조건으로 두지 않는다. 이어서 앱 안 **‘세차한 것 같아!’ / ‘세차 안 했어요’** 배너를 표시한다. 휴대폰 시스템 알림에도 같은 액션을 연결했다.
- ‘세차 안 했어요’는 해당 visitId를 취소하고 이전 유효 기준으로 재계산한다. 첫 세차였다면 null이다. ‘닫기’/무응답은 자동 반영을 유지한다. 배너 닫기 상태와 취소 흔적은 재시작 후에도 유지한다.
- 오인식 취소는 설정 버전·메시지 순서와 독립적인 취소 흔적으로 저장한다. 설정이 충돌하거나 취소가 늦게/먼저 도착해도 동일 방문의 재전송으로 부활시키지 않는다. 알림 액션의 PendingIntent는 차량ID+visitId로 구별해 다른 차량을 취소하지 않는다.
- 장시간 위치 미확인 동안 예전 체류가 계속 유지되는 오류, 실제 이동으로 끊긴 정차 시간을 합쳐180초로 만드는 오류를 막는 회귀 테스트를 추가했다. 이는 업로드 조건을 충족하지 못한 입력의 처리이며, 조건 충족 후 추정을 보류하는 것과 다르다.
- 향후 실물 Bluetooth 점검은 [단일 일괄 검증 목록](BLUETOOTH_VALIDATION_CHECKLIST.md)의 BT-01~36을 사용한다. 프로젝트 AGENTS에도 연결했다.
- Pleos에서 시험 공급자의 배너 표시/세차 안 했어요/닫기를 직접 검증했다. 캡처: `build/diagnostics/care-warning/confirmation-banner.png`. 실제 SDK가 세차를 감지했다는 증거가 아니다.

## 실수신 결과

환경: 기존 Pleos Connect v2.1.0 / API 34 / 공식 AVD / 4096MB / 2560×1440, Cold Boot. Vehicle 2.3.2, FusedLocation 1.1.3. SDK·AVD·Gradle worker 설정 변경 없음. 차량 사용자 10, 데이터 유지 설치 사용.

| 항목 | 실제 관측 | 입증 범위와 제한 |
| --- | --- | --- |
| 기어 | `GEAR_PARK` getter/콜백 | 현재 P 수신. 콘솔 P→N→D 및 지연 측정은 미실행. 설치 SDK에서 deprecated인 공개 API 사용; 다른 API로 임의 대체하지 않음 |
| 위치 | 처음 PermissionDenied, 선언된 일반 FUSED_LOCATION 권한 허용 후 Available | 원본 생성 시각과 수신 시각 확보. 원본 좌표는 보고서에 보관하지 않음 |
| 위치 정확도 | 모델에 `confidence` 제공, 미터 단위 수평 정확도 getter 없음 | confidence를 10m 이하 정확도로 변환하지 않음. 수집 요청 주기와 실제 콜백 주기는 별개이며 시험 프로필 검증 미완료 |
| 누적거리 | `0.0` getter 및 반복 콜백 | 단위 미확인. 실제 이동 변화·컨베이어 이동 검출을 입증하지 않음. 단위 확인 없는 값은 계산 장부로 넣지 않음 |
| 트립 거리 | Unsupported / ERROR_NOT_SUPPORTED_PROPERTY | 사용 불가로 처리 |
| 엔진오일량·브레이크액·변속기 오일·워셔액 | 모두 Unsupported / ERROR_NOT_SUPPORTED_PROPERTY | 이 에뮬레이터의 실제 액량 수신 성공이 아님 |
| Telltales/오일 압력 | PermissionDenied | CAR_SAFETY 일반 권한은 granted였으나 조회 실패. 특권 권한 추가·우회 없음 |
| Bluetooth | 가상 어댑터 ON | 실물 상대 없음. 어댑터 존재를 앱 메시지 전송 성공으로 취급하지 않음. 공식 AVD 동글 미지원 제한도 별도 적용 |

로컬 원본: `build/diagnostics/care-warning/sdk-evidence.json`, `sdk-location-granted.json`. 해당 파일은 Git 제외. 보고서 시간은 한국 날짜 기준이고, 이벤트의 절대 시각은 장치가 보고한 값을 그대로 남겼다.

공식 확인: [Cold Boot 및 Bluetooth 제한](https://document.pleos.ai/en/docs/connect/guide/getting-started/application-development/configure-pleos-connect-emulator), [Safety API](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/safety/entry), [Android Bluetooth 권한](https://developer.android.com/develop/connectivity/bluetooth/bt-permissions), [Open-Meteo 시간별 변수](https://open-meteo.com/en/docs).

## 구현된 경로

- `CarePolicy.kt`: 한국 날짜 경계, 수동 세차 다음 날 00시 시작, 미확인 구간 20km/일, 실제 거리로 해당 구간 대체, 확인된 0km 구간 처리. 세차일 모름은 null. 시간 분할 근거 없는 세차 경계 구간은 임의 배분하지 않는다.
- 같은 계산기를 차량 홈·관리 화면·휴대폰에서 사용한다. 종전 날짜+거리+현재 미세먼지 합산을 제거했다. 기존 oilRemaining 정보는 별도 관리 정보이며 보라색/정비 알림의 교체 수명 신호가 아니다.
- 실제 시각·위치가 있는 구간에만 시간별 날씨 적용. 눈/비/먼지 배타 처리, 먼지 높은 단계 하나, 하루 2%p 한도, 72시간 보정. 장부에는 한도 전 날씨·거리 자료를 보존한다. 현재 요약 날씨 API 값을 과거 전체 거리 차감에 사용하지 않는다.
- 비위치 장기 장부, 중복 구간 ID, 기록/visitId/취소 흔적 보존. 확인된 연속 0km 구간만 무손실 압축하며 경계별 계산 동등성 테스트를 둔다. 이동 구간을 임의 일별 총합으로 압축하지 않는다. 상세 좌표는 새 장부에 저장하지 않는다.
- 자동 감지 순수 상태기: 5초/10초/10m/10초/15초/30초/180초 시험 프로필 분리. 시설 유형·구역·실제 이동·기어·차량 출차 근거 요구. 휴대폰만 체류하거나 불명 구역이면 생성하지 않는다.
- 자동 기록 한 건·7일 취소·visitId 재수신 중복/부활 방지. 7일은 기록 반영 시점 기준이며 원래 완료 시각도 보존한다. 메모 수정은 완료 시각 유지, 명시적 수동 전환은 날짜 기준으로 재계산. 같은 날짜 수동 추가 시 기존 자동 기록 보완/별도 추가 선택. 취소 흔적은 화면에서 숨기되 장부에 유지한다.
- 휴대폰이 설정·기록 버전을 확정. 차량 변경은 원래 버전을 포함한 미전송 요청으로 보관. 오래된 요청은 휴대폰 기준 유지, 충돌 사본 보존. 같은 차량의 기존 휴대폰 자료를 기본30으로 덮지 않으며 다른 차량의 자료를 섞지 않는다.
- 보안 RFCOMM의 선택된 페어링 상대만 허용. v2 응답에 ACK·휴대폰 상태를 포함. 메시지 revision 중복/역순 무시, 스냅샷 디스크 보관, ACK 최대3회/5초 재전송. 거리 장부는 128건씩 전달하고 수신된 내용의 digest ACK 후 다음 묶음 전송. 전체 무제한 이력을 단일 프레임에 넣지 않는다.
- 휴대폰 세차 필요 알림: 표시 점수와 같은 반올림, 설정·OS 권한/채널 확인, 주기당 한 번. 워셔액은 유효한 최신 부족 신호만 알림. 미지원·오류·단절·Demo는 해제나 부족으로 바꾸지 않는다. 일반 정비 경고 전체 타입은 준비하되 차종별 규칙 미확인으로 자동 알림 보류.
- 한국 자정의 inexact AlarmManager 평가, 앱 시작·설정/권한 변경 시 재평가. 폴링/정확 알람 권한/새 라이브러리 없음. 절전 중 알림 시각은 지연될 수 있으며 정각 전달 보장은 하지 않는다.
- 파일/소켓/SharedPreferences I/O는 IO dispatcher에서 처리. 수정 트랜잭션은 공유 잠금으로 직렬화하고 같은 내용은 반복 저장하지 않는다.

## 검증 수준

| 실행 항목 | 결과 |
| --- | --- |
| 차량 JVM 테스트 | 30개, 실패0/오류0 |
| 휴대폰·공유 정책 JVM 테스트 | 26개, 실패0/오류0. 기존22개에 디자인 매핑3개·시설 확실성별 전체 패턴1개 추가 |
| Android CareFlowTest (시험 메시지) | 10개 통과. 기존6개 + 역순/오래된 설정과 오인식 취소, 배너 닫기, 실제 알림 액션의 차량 분리, P단 조건→즉시 반영 |
| Pleos WashConfirmationUiTest (시험 공급자) | 2개 통과. ‘세차 안 했어요’→null 복귀/재전송 방지, ‘닫기’→반영 유지 |
| 휴대폰 배너 최종 Android 검사 | 17개 통과(기존 CareFlow10 + 배너6 + 내역 UI1). 실제132개 게시, 질문 알림 액션, 완료 배너, 저장·중복·경고 재발·실제 시스템 알림 터치→선택 내역 읽음까지 확인 |
| Pleos SDK 증거 수집 + BackgroundIconMonitorTest | 2개 종료/통과. capability 성공 범위는 위 표와 구별 |
| 차량/휴대폰 APK | debug 빌드 통과, 기존 차량 앱에 `install -r` 적용 |
| Android lintDebug | 양쪽 오류0. 배너 연결 후 차량 경고52개, 휴대폰 경고14개. 동적 리소스 보존 설정 포함, 별도 lint 보고서에 유지 |
| 데이터 보존 | 테스트 전후 `vemoring_native.xml` SHA-256 동일 (`True`) |
| Pleos 화면 | 보라색 경고 캐릭터·계기판 확인 문구 직접 확인. 시험 표시를 유지하며 실제 소스로 복귀 |

최종 위치 콜백에서는 `confidence=5`, 생성 시각 기준 약1~2초 간격의 수신도 관측됐다. 이 수치를5m 정확도로 해석하지 않으며 위치 요청 주기5초가 검증됐다는 뜻도 아니다.

- JVM: 정책 예시86%, 실제30km/비10km의5%p 차감, 실제거리의 추정 대체, 미주행/미수신, 눈·비·먼지, 보정 마감, 기록 취소, 늦은 자동기록 취소 기간, 설정 충돌, 주유소/불명구역·GPS 흔들림·공백·180초·출차, 장부 압축 전후를 검증한다. 입력 주입이지 실차 검증이 아니다.
- Android: 별도 `com.example.vemoring.phone.verification` 앱과 테스트 전용 preference 이름으로 시험 프레임→저장→ACK→실제 Android 로컬 알림을 검증한다. 이 테스트는 Bluetooth 소켓을 사용하지 않는다. 기존 차량/휴대폰 프로필을 테스트 자료로 덮지 않는다.
- 실제 Pleos SDK: `CareSdkEvidenceTest`는 받은 값과 실패 상태를 기록하는 증거 수집 테스트다. 테스트가 끝났다는 사실을 전체 capability 성공으로 해석하지 않는다.
- 기존 BackgroundIconMonitorTest는 실제 SOC 재수신 및 시험 공급자의 여섯 상태 전환을 검증했다. 새 세차 자동 감지나 실제 경고 수신 성공과 별개다. 기존 GNB 고정 아이콘 갱신 문제는 이 변경으로 해결됐다고 주장하지 않는다.

## 아직 연결/검증되지 않은 기능

1. 실제 차량 앱→Bluetooth→실물 휴대폰→알림: 실물 장치가 없어 미검증. 설정 양방향 갱신과 ACK 재시도 역시 실제 전파·프로세스 종료 상황 검증이 필요하다.
2. 자동 세차 실사용 어댑터: 상태기는 구현했으나 실제 위치 정확도·세차 구역 polygon·정차/컨베이어 이동 근거를 확보하지 못해 연결 보류. 기존 OSM POI 중심점을 세차기 경계로 사용하지 않는다. 실제180초 세차 시험 미실행.
3. 휴대폰 위치의 백그라운드 보완 수집: 실제 어댑터/권한/FGS 흐름은 아직 미구현·미검증. 상태기는 출처 전환을 받을 수 있지만 이 기능이 작동한다고 표시하지 않는다.
4. 실제 이동 구간별 과거 시간별 날씨 조회·위치 매칭 수집: 아직 미연결. 계산·보관·후수신 입력은 구현했지만 기존 요약 API 응답을 자동으로 구간 날씨로 만들지 않는다.
5. 전체 정비 경고의 차종별 규칙/시동 자체 점검/ESC·EPB 의미/긴급 문구: 공식 의미·필수 전제·실수신 미확인. 자동 알림 보류. 액량API/워셔액 실수신도 이 AVD에서 미지원이다.
6. 물리 휴대폰 OS 알림 거부·꺼진 화면·재부팅/시간대 변경·장기간 저장량/성능·기어 P→N→D 콘솔 시나리오는 별도 검증 필요. 장기 구간 저장은 SharedPreferences 기반이며 장기간 이동 이력의 저장량/재계산 비용은 측정 전이다.

위 항목은 임계값을 낮추거나 가짜 정상값으로 대체해 통과시키지 않았다. `CARE_VALIDATION_PLAN.md` 원본 체크박스는 미실행 항목을 성공처럼 바꾸지 않았다.

## 제공 배너 연결 추가 검증

[배너 구현 상세](BANNER_NOTIFICATIONS.md). 제공 원본133개 중132개를66종 밝음/어두움 배너로 연결하고, 나머지 합성 이미지는 질문 UI 참고로 사용했다. 전용 셀프/명확히 분리된 세차 구역은 전체 패턴 충족 후 완료 배너, 애매한 적격 방문은 먼저 반영 후 질문 배너로 구분한다. 시설 구역·위치 정확도 실제 어댑터의 미연결 상태는 그대로 남아 있다.

최종 `build/diagnostics/banner/phone-final.log`: **OK (17 tests)**,53.533초. Android API34 휴대폰 AVD에서 격리된 verification 앱 사용. 66종×2테마 실제 NotificationManager 게시/제목·스타일·중복 확인, 내역132건 재개방·읽음 보존, 실제 질문 알림 액션의 취소/점수 복원, 완료 알림에 질문 액션 없음, 경고 지속/단절/정상/재발, 최초 기록 가져오기/메모 수정 제외, 실제 시스템 알림 터치→MainActivity→해당 내역 읽음 저장을 확인했다. 권한 거부 분기는 격리된 Context 입력으로 검사했으며 실물 휴대폰 권한·절전 검증을 대신하지 않는다.

`pleos-ui.log`: **OK (2 tests)**,8.309초. 실제 Pleos 화면에서 제공 참고 디자인에 맞춘 질문 컴포넌트의 취소/닫기 확인. 전후 `vemoring_native.xml` 해시 동일(`True`). 휴대폰과 Pleos AVD를 번갈아 실행해 동시에 두 에뮬레이터를 유지하지 않았다. SDK/AVD 사양·Gradle worker 설정 변경 없음.

화면 증거: `phone-history-light.png`, `phone-history-dark.png`, `phone-tap-history.png`, `phone-notification.png`, `pleos-question.png`. 모두 `build/diagnostics/banner/`의 Git 제외 로컬 산출물이다. 시뮬레이션 이벤트는 화면·내역에 표시했으며 실제 Bluetooth/실차 신호 성공으로 취급하지 않는다. 테스트 도중 접근성 검색 실패와 다크모드 이미지 불일치를 수정한 뒤 최종17개를 함께 재실행해 통과했다.

최종 차량/휴대폰 debug APK 빌드 및 양쪽 lint 통과: `final-build-lint.log`. 운영 휴대폰 앱도 `install -r` 갱신 후 `phone_ui_v1.xml` SHA-256이 전후 동일(`PhoneDataPreserved=True`)함을 확인했다. verification 앱의 시험 데이터와 운영 앱의 데이터는 분리된다.

추가 시각 회귀 검사: 차량 전송에 `fuelAt`/`batteryAt`/`lowFuelAt`/`vehicleWarningAt` 원본 수신 시각을 포함했다. 새 메시지 봉투가 오래된 캐시 값을 새 관측으로 갱신하지 않는다. 이후 변경된 `stateTransitionsDeduplicateAndDoNotInventRepairsOnDisconnectOrNormal`을 Pleos의 격리 verification 앱에서 다시 실행해 **OK (1 test)**,0.762초(`freshness-regression.log`). 원본 시각 null은 최신으로 만들지 않는다. 최종 차량 APK 갱신·MainActivity 복귀 후에도 `VehicleDataPreserved=True`.

시각 회귀 수정 후 휴대폰 운영 앱까지 최종 APK로 다시 `install -r` 갱신했으며 `FinalPhoneDataPreserved=True`. 휴대폰 AVD를 종료하고 Pleos 공식4GB 프로필을 Cold Boot로 복원했다. 두 운영 앱 모두 사용자 데이터를 지우지 않았다.

최종 복원 ADB는 `emulator-5556`(Cold Boot 후 포트 변경), 사용자10. Pleos 시스템 UI 서비스가 부팅 중 한 차례 재시작되어 첫 `am start -W`는 timeout이었다. 시스템 안정 후 차량 앱 프로세스만 다시 실행했고 최종 화면은 `pleos-final.png`, 데이터 보존은 `RestoredVehicleDataPreserved=True`로 확인했다. 이 시스템 부팅 문제를 앱 알림 검사 실패와 섞지 않는다.

실물 Bluetooth 관련 추가 점검은 BT-37~43을 [단일 목록](BLUETOOTH_VALIDATION_CHECKLIST.md)에 이어 기록했다. 기존36개와 합쳐43개이며, 아직 실물 장비로 실행하지 않았다.
