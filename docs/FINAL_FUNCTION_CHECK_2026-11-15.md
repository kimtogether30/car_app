# 차량·휴대폰 앱 마무리 점검 — 2026-11-15

대상 소스: `f7575316271e844406c84f7d95852958b5591adf`. 사용자가 pull한 아이콘 수정본을 빌드해 점검했다. 제품 코드는 수정하지 않았으며 발견 사항을 아래에 구분했다. 아이콘 해결 방법은 [보존 문서](PLEOS_GNB_DYNAMIC_ICON.md)에 보충했다.

## 환경과 보존

- 사용자 확인에 따라 **Tab S7(SM-T875N)에서 차량 앱 먼저, 휴대폰 앱 다음** 순서로 점검했다. Galaxy S24 검사는 아니다.
- Bluetooth는 사용자 요청으로 제외했다. 프레임/저장소 fixture 검사는 무선 검사가 아니다.
- 패드에서 확인할 수 없는 기능은 이 PC의 Pleos Connect 2.1.0 / API 34에서 추가 검사했다. 기존 공식 프로필, 2560×1440, 4GB RAM, Cold Boot를 유지했다. 휴대폰 에뮬레이터는 실행하지 않았다.
- 실제 앱은 `adb install -r`로 갱신했다. 실제 앱 데이터 삭제/초기화/테스트 차량 삽입은 하지 않았다. 변경·취소·삭제·합성 신호 검사는 별도 `.verification` 앱이나 격리 저장소를 사용했다.
- 패드의 두 검증 앱과 Pleos user10의 기존 휴대폰 검증 앱은 검사 후 비활성화했다. 데이터는 보존했다.
- SDK/에뮬레이터 설정과 제출 조건을 변경하지 않았다. 공식 [개발 환경](https://document.pleos.ai/en/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator), [Cold Boot/VHAL 안내](https://document.pleos.ai/en/docs/connect/guide/getting-started/application-development/configure-pleos-connect-emulator), [공모전 FAQ](https://appdevchallenge.auto-hmg.io/faq)를 재확인했다.

## 실행 결과

| 실행 | 통과 | 실패 | 건너뜀 | 해석 |
|---|---:|---:|---:|---|
| 차량 JVM 테스트 | 39 | 0 | 0 | 최신 차량 소스 로직 |
| 휴대폰·공통 JVM 테스트 | 110 | 0 | 0 | 이번 실행에서 재실행, 오일·세차·거리·날씨·원장 정책 포함 |
| 패드 차량 계측 1차 | 38 | 8 | 1 | GNB provider 없음은 skip, SDK 실수신/무선 전용 검사는 제외 |
| 패드 차량 다중 차량·오일·레이아웃 재검사 | 5 | 2 | 0 | 다중 차량 UI는 재검사 통과, 화면 전제 실패 2건 재현 |
| 패드 휴대폰 계측 1차 | 56 | 4 | 2 | 실제 날씨 API 검사 활성화. 위치 주입/파일 프레임 replay opt-in 2건 skip |
| 패드 휴대폰 실패 4건 재검사 | 2 | 2 | 0 | 알림 2건 재검사 통과. 아이콘 기대값/날씨 시간 초과 재현 |
| Pleos SDK·백그라운드·런처 | 11 | 1 | 0 | 실제 수신 통과, 인위적 alias 전환 후 재실행 테스트 실패 |
| Pleos GNB 최종 | 1 | 0 | 0 | 시스템 계정 준비 후 여섯 상태 캡처도 직접 확인 |

각 행은 별도 실행이며 중복 테스트가 포함되어 합계를 고유 검사 수로 해석하면 안 된다. 환경 보고용 테스트의 통과는 해당 기능 지원을 뜻하지 않는다. 특히 점화 상태 보고 테스트는 `Unsupported`를 기록하고 정상 종료한다.

차량/휴대폰 debug·verification APK 및 계측 APK 빌드는 모두 성공했다. Gradle worker 제한(2)과 메모리 제한을 유지했다.

## 기능별 확인

| 기능 | 이번 확인 |
|---|---|
| 등록·온보딩 | 자동/부분 감지·수동 선택 유지 검사 통과. 한글 닉네임 저장 후 홈 전환·기존 기록 보존 검사 통과. 일부 이전 UI 시나리오는 아래 실패 참조 |
| 여러 차량 | 같은 모델·번호판 없음의 등록 분리, 기존 ID/기록 유지, 조회와 수신 대상 분리, 수신 대상 변경 취소 확인. 패드 UI 전환은 단독 재검사 통과 |
| 차량 정보 수정 | 번호판 제거·기존 날짜 유지·안정된 차량 ID·다른 차량 독립성 검사 통과 |
| 관리 기록·달력 | 저장/재열기·미래 날짜 거부·기록 종류/개수·삭제 확인·삭제 tombstone/역순 replay 보호 검사 통과 |
| 세차 | 추정 선적용·무응답 유지·부정 취소·동일 방문 재전송 차단·구형 반경 근거 차단·동일 위치 재수신 추정 fixture 검사 통과. 실제 세차장 방문 검사는 아님 |
| 엔진오일 | ENGINE_OIL 구분·직접 기록·가혹조건 주기·전기차 제외·초기/실제 거리 중복 방지·30분 수신 공백·출차/다음 정차·취소 복원 방지 검사 통과. 실제 정비소/시동 검사는 아님 |
| 연료 | 임계값 설정·주유 episode/출렁임 중복 방지·주유 완료 알림 로직 검사 통과. Pleos 연료/SOC/누적거리 실제 숫자 수신 검사 통과. 실제 주유 검사는 아님 |
| 홈·접근성 | 두 앱의 공유 홈 구조, 큰 글자/좁은 화면 주변 검색 도달·기록 입력·키보드 동작 검사 통과. 차량 1차 화면은 캐릭터 영역이 커서 상태·날씨는 스크롤 필요 |
| 알림·배너 | 새 이미지/밝음·어두움·차단/비활성 이력·오일/세차 부정 액션·기록 열기 검사 통과. 알림 2건 1차 실패 후 재검사 통과는 안정성 문제로 별도 기록 |
| 아이콘 | 휴대폰 여섯 alias 직접 전환은 통과. 실제 정책 기반 배경 전환 기대값 테스트는 아래 참조. Pleos는 준비 후 여섯 상태의 고정바·최근 아이콘 실제 일치 확인 |
| 주변 검색 | 세 카테고리·선택한 지도앱/검색어 전달·미설치 앱 숨김 UI 검사 통과. 실제 외부 지도에서 검색 완료·길 안내까지는 검증하지 않음 |
| 날씨 | 캐시 지역/시각·위치 없음/오래된 위치·재시도·날씨 원장 로직 검사 통과. 실제 공용 날씨 API 수신은 패드에서 두 번 시간 초과 |
| SDK·수신 | Pleos 실제 Vehicle/FusedLocation/NaviHelper 콜백, 속도 m/s·기어, source close, 화면 밖 수신/아이콘 설정 독립 검사 통과. 동적 주행/경로 변경/실차 센서 검사는 아님 |

실제 패드 차량 앱에서 홈 상/하단·관리 기록·내 차 설정을 열어 확인했다. 휴대폰 실제 앱은 차량 미등록 상태였으며 중앙 진입 화면·등록 폼·취소 동작을 확인하고 미등록 상태를 유지했다. 등록된 휴대폰 화면/기록/설정/알림은 격리 계측 앱에서 검사했다.

## 남은 오류·수정 후보

### 1. 실제 날씨 API 수신 실패 — 재현됨

`ForecastPanelTest.actualForecastCacheAndManualRefreshWorkOnAndroid`: 1차와 별도 재검사 모두 `SocketTimeoutException: Read timed out`. `WeeklyWeatherRepository`의 실제 HTTPS 요청에서 실패해 7일 예보/수동 갱신의 실수신 성공을 확인하지 못했다. API 서버·패드 네트워크·환경 중 원인은 아직 확정하지 않았다. 캐시/오류 경로 테스트 통과를 실제 수신 성공으로 대체하지 않는다.

### 2. 차량 가입 완료 화면의 낡은 안내 — 소스 확인

`app/src/main/java/com/example/vemoring/Setup.kt`의 완료 요약에 “알림 발송과 날씨는 아직 예시 기능입니다.”가 남아 있다. 현재 실제 알림/날씨 구현과 맞지 않아 사용자에게 잘못된 범위를 안내한다. 문구 수정 필요. 이번에는 제품 코드를 수정하지 않았다.

### 3. 자동 검증의 전제·안정성 문제 — 제품 오류와 구분 필요

| 실패 검사 | 관찰 및 판단 |
|---|---|
| 차량 ExampleInstrumentedTest | applicationId를 `com.example.vemoring`으로 고정해서 `.verification`에서 실패. 테스트 전제 문제 |
| 차량 MascotLauncherTests 두 건(패드) | `${context.packageName}.Launcher*`를 사용하지만 verification manifest의 클래스는 `com.example.vemoring.Launcher*`. alias 생성 코드도 같은 전제를 사용. 실제 앱 ID에서는 이 주소 문제가 없으나 검증 앱의 아이콘 경로는 잘못됨 |
| 차량 MascotLauncherTests 재실행(Pleos) | 실제 package에서는 여섯 alias/실행 항목 1개 검사는 통과. 인위적 상태 전환 후 재실행은 LauncherExhausted `ActivityNotFoundException` 1건. 실제 수신/백그라운드 동기화가 테스트 override를 바꾸는 경쟁 가능성. 실제 사용자 재실행 오류로 단정하지 않음. 고정바 정상 터치는 별도 성공 |
| 차량 GarageUiTests | 닉네임 입력 노드를 찾지 못해 초기 단계 실패. 이후 단계에 이미 제거한 “나중에 할게요”도 남아 있어 최신 온보딩에 맞게 갱신 필요 |
| 차량 SetupReadabilityTests.allSetupStepsRemainReachableWithVehicleTypography | 닉네임 단계에서 노드 검색 실패. 스크롤을 포함하는 별도 한글 등록 완료 검사는 통과. 모든 등록이 막히는 것으로 단정하지 않음 |
| 차량 EngineOilUiTest.homeUsesOilCharacterWithoutPercentageCard | 스크롤하지 않고 “남은 연료” 표시를 요구해 실패, 별도 재검사 재현. 실제 패드에서는 스크롤 후 연료/청결도에 도달함. 오일 퍼센트 카드는 없음 |
| 차량 GarageLayoutTests.vehicleLayoutCentersCharacterAboveStatusWithoutProfileEditor | `status.top > character.bottom` 전제 실패 재현. 고정 폭 1240dp에 대한 semantics 좌표/스크롤 전제를 검토해야 함. 실제 패드 캡처에서는 순서 유지·스크롤 도달 가능 |
| 차량 GarageFleetUiTest.switchingViewedCarsDoesNotSwitchTheReceivingCar | 1차 `No compose hierarchies found`, 별도 3건 전체 재검사에서는 통과. 화면 교체 대기 보강 필요 |
| 휴대폰 CareFlowTest의 알림 두 건 | 1차 `activeNotifications.first`에서 NoSuchElementException. 두 건 모두 단독 재검사 통과. 비동기 알림 등록 완료 대기 없이 조회하는 테스트 안정성 문제 후보. 다중 차량 액션 분리 자체의 항상 실패는 확인되지 않음 |
| 휴대폰 PhoneLauncherTest.receivedVehicleStateUpdatesIconInBackground | `signals.oilDue=true`를 넣으면 Angry가 된다는 기대가 두 번 실패(Fuel 유지). 현재 currentPhoneMood는 모델/기록의 EngineOilCatalog 계산 결과로 due를 정하며 테스트 모델도 “시험 차량”이다. 임의 oilDue flag를 무시하는 현재 정책과 오래된 fixture가 맞지 않음. 여섯 상태 직접 전환은 통과 |

GNB 준비 직후 한 번은 `Preview did not render ... NORMAL`로 실패했고, 앱 프로세스를 종료한 뒤 최종 재실행은 통과했다. 단일 provider 검사 통과만으로 화면 일치를 주장하지 않았다.

## Pleos 아이콘 재검증과 환경 한계

처음에는 최신 APK를 설치해도 고정바가 파랑으로 유지됐다. provider 행 id/intent 검사는 성공했지만 실제 그림은 달랐다. 친구의 문서대로 실제 차량 앱을 시스템 계정(user0)에 `install-existing`하고 여섯 alias 메타데이터를 활성화했다. 운전자(user10)의 실제 앱 활성 런처는 1개를 유지했다.

그 뒤 앱 프로세스를 종료해 최종 GNB 검사를 다시 실행했고 여섯 상태 모두 큰 캐릭터·기존 고정 슬롯·최근 항목 그림을 직접 확인했다. 고정 위치와 다른 앱 행 보존, 같은 실행의 고정되지 않은 중복 정리도 테스트 통과. 최종 고정바를 눌러 실제 MainActivity가 열렸다.

이는 **개발용 debug AVD 준비 조건을 포함한 해결**이다. 공개 SDK 계약이나 실차/App Market 설치 보장은 아니다. 이번 최종 준비 이후 재부팅 지속성은 새로 검사하지 않았으며 이전 친구 PC 결과와 구분한다. 캡처에 별도로 보이던 과거 휴대폰 검증 앱은 user10에서 비활성화했다. 실제 차량 앱의 고정 1개+최근 1개는 서로 다른 GNB 영역이며 중복 런처 설치가 아니다.

SDK 관찰 보고에는 GEAR/SOC/ODOMETER/LOCATION Available을 기록했다. TRIP_DISTANCE는 Unsupported, IGNITION_STATE는 `Unsupported:null:VHAL IGNITION_STATE 미지원`이었다. 위치 정확도는 null, 누적거리 단위는 미검증 그대로다. 이 수신으로 실제 OFF→ON·이동 거리·시설 경계를 검증했다고 해석하면 안 된다. 동일 장소 receiving-gap 정책은 별도 fixture로 검사했다.

Pleos crash 버퍼에는 기존 시스템 프로세스(SystemUI/carwatchdogd/GleoAI 등) 기록도 섞여 있었다. 검사 시작 전부터의 기록이 포함되어 이번 앱 때문에 발생한 새 crash로 집계하지 않았다. 패드 crash 버퍼에서는 이번 점검 중 VEMORING의 fatal crash 기록을 보지 못했다.

## 증거 위치

로컬 `.tmp/final-check-2026-11-15/`에 전체 계측 출력, 재검사 출력, SDK capability JSON, 패드 캡처와 Pleos 여섯 상태 캡처를 남겼다. 캡처에는 사용자 차량 표시가 포함될 수 있어 공개 저장소에 자동 추가하지 않았다.

- `vehicle-instrumentation.txt`, `vehicle-focused.txt`
- `phone-instrumentation.txt`, `phone-focused.txt`
- `pleos-sdk.txt`, `pleos-capabilities.json`
- `pleos-gnb.txt`(준비 전 주소 검사), `pleos-gnb-prepared.txt`(미리보기 실패), `pleos-gnb-final.txt`(최종 통과)
- `pleos-final-{NORMAL,FUEL,WASH,OIL,ANGRY,EXHAUSTED}.png`
- `vehicle-home-before.png`, `vehicle-home-scroll.png`, `vehicle-home-bottom.png`, `vehicle-records.png`, `vehicle-profile.png`, `phone-home.png`

이번 점검은 자동화된 기능 회귀와 위 실제 화면/SDK 확인 범위다. Bluetooth, 실제 차량/시설, 외부 지도 길 안내 완료, 실제 시동은 검증하지 않았으므로 “모든 실제 환경 통과”로 표시하지 않는다.

## 후속 수정과 재검사 (2026-11-15, Claude)

### 원인 확인
- **날씨 시간 초과:** 패드에서 `curl`로 앱과 같은 예보 주소를 10회 호출했다. TCP는 매번 약 0.3초에 연결됐지만 처음 5회는 TLS 핸드셰이크가 30초 동안 끝나지 않았고(약 2.5분 구간), 이후 5회와 추가 12회는 모두 HTTP 200·약 1초였다. 같은 시간 미세먼지 서버(152.53.84.73)와 패드 Wi-Fi(ping 손실 0%)는 정상이었다. 앱 오류가 아니라 해외 예보 서버 경로(51.81.167.44)의 일시적 TLS 정체다.
- **알림 누락:** 휴대폰 전체 계측 중 `NotificationService: Package enqueue rate is 5.6. Shedding ...`를 확인했다. 초당 약 5건을 넘으면 Android가 알림을 조용히 버리는데 `notify()`는 정상 반환해 내역에는 Delivered로 남는다. 실제 앱에서도 재연결 직후 여러 완료/상태 알림이 몰리면 같은 일이 생길 수 있다.

### 제품 수정
- 날씨: 요청당 읽기 제한 20초→12초(응답 약 2KB, TLS 정체 시 빨리 재시도). 실패 후 재조회 간격을 10분 고정에서 1→2→5→10분으로 변경(화면 표시 중에만). 기존 3회 재시도·마지막 예보 표시 유지.
- 알림: `NotificationPacer`로 배너와 연결 상태 알림 게시 간격을 최소 250ms로 제한(초당 4건 이하, 작업 스레드에서만 대기).
- 문구: 가입 완료의 “알림 발송과 날씨는 아직 예시 기능입니다.”, 두 앱 설정의 “예시 설정 · 실제 알림은 발송되지 않습니다.”를 실제 동작 설명으로 교체.
- 아이콘: 차량 런처 alias/MainActivity/GNB component 이름을 packageName이 아닌 manifest 클래스 namespace로 생성. 실제 앱 ID에서는 결과가 동일하고 `.verification`에서도 올바른 alias를 가리킨다.
- debug 전용 상태 미리보기 receiver/activity 두 개에 `android.permission.DUMP`를 요구해 다른 앱이 예시 상태를 주입할 수 없게 했다. adb(shell)와 같은 앱의 계측은 그대로 사용 가능(Pleos user10에서 `am start`/`am broadcast` 확인).
- 예시 데이터 점검: 차량 Demo 모드는 앱 시작마다 Real이고 진단 화면에서 주차 중 명시 선택해야 하며 저장되지 않는다. Demo 프레임은 휴대폰에서 기록·알림·아이콘·날씨에 쓰이지 않는다. 그 외 제품 경로에서 고정 예시 데이터는 발견되지 않았다.

### 테스트 수정
- 템플릿 `ExampleInstrumentedTest`(패키지 고정)와 `ExampleUnitTest`(2+2) 삭제.
- 화면 밖 요소를 스크롤 없이 누르던 등록/홈/레이아웃 검사에 `performScrollTo` 추가, 레이아웃 비교를 화면에 잘리지 않는 `positionInRoot`로 변경, 차량 전환/알림 조회에 대기 추가, 제거된 고정바 안내(“나중에 할게요”) 단계 삭제.
- PhoneLauncherTest: 수신 `oilDue` 플래그 대신 카탈로그 차종과 휴대폰의 엔진오일 기록으로 Fuel→Angry→Normal 확인.
- 차량 SDK 전용 검사(BackgroundIconMonitor/CarSpeedIntegration/RealSdkReception)는 automotive 기기가 아니면 skip. 패드에서 실패하며 내비 앱을 띄워 이후 화면 검사를 연쇄 실패시키던 문제를 없앴다. Pleos에서는 기존처럼 실제 데이터가 없으면 실패한다.

### 재검사 결과 (Tab S7)
| 실행 | 결과 |
|---|---|
| JVM 차량 / 휴대폰·공통 | 38 / 111 통과(휴대폰 2 skip은 기존 opt-in) |
| 차량 전체 계측 | 59 통과(SDK 전용은 automotive 아님으로 skip) |
| 휴대폰 전체 계측(`weatherLive=1`) | 63 통과. 실제 7일 예보 수신·수동 갱신 포함, 수정 후 Shedding 없음 |

검사 후 패드의 두 verification 앱을 user0에서 비활성화했고 잔여 알림 0. 패드 일반 앱과 Pleos 일반 앱은 `install -r`로 갱신했으며 데이터는 보존했다. Bluetooth·실차·실제 시동/정비소/세차장은 이번에도 검증하지 않았다.
