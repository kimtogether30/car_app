# 다른 Windows PC에서 실행 및 실수신 검증

현재 브랜치: feature/pleos-sdk-integration. 원본 공동 작업 폴더와 main은 변경하지 않았습니다.
SDK 코드는 feature 브랜치에서 PR 검토합니다. main에는 승인 전 병합하지 않습니다.
해당 feature 브랜치를 clone하거나, 아래 소스 ZIP으로 현재 작업을 전달하세요.

## 1. 소스 전달

현재 PC에서 실행:
~~~powershell
cd <프로젝트-폴더>
powershell.exe -NoProfile -ExecutionPolicy RemoteSigned -File .\tools\New-PortableSource.ps1
~~~
출력: build/handoff/VEMORING-source-날짜시각.zip. Git 추적 파일과 ignore되지 않은 새 파일을 포함합니다.
.git, local.properties, build, .gradle, IDE 로컬 설정, 키/환경 파일은 제외합니다.
이 ZIP은 독립 Android Studio 프로젝트이며 Git 저장소가 아닙니다. 다른 PC의 새 빈 폴더에 압축을 푸세요.
기존 공동 작업 폴더 위에 덮어쓰지 마세요.

GitHub의 feature 브랜치에서 실행:
~~~powershell
git clone --branch feature/pleos-sdk-integration https://github.com/kimtogether30/car_app.git Vemoring-pleos
cd Vemoring-pleos
~~~
main 병합과 자동 병합은 PR 검토 및 별도 승인 전 수행하지 않습니다.

## 2. 개발 도구

- Android Studio에서 프로젝트 폴더를 열고 Gradle Sync.
- 현재 설정: Gradle 9.6.0 / AGP 9.5.1 / Kotlin Compose plugin 2.2.10.
- Gradle daemon Java toolchain 25. Android Studio JBR 또는 JDK 25 설치.
- Android SDK Platform 37, build tools, platform-tools 설치. compile/target 37, min 28.
- 실행 장치는 Pleos API 34 / Connect 2.1.0 환경. 컴파일 SDK와 실행 API는 다릅니다.
- 정상 Pleos Vehicle/Navi/FusedLocation 서비스 및 공식 사양의 GPU 필요.
- Pleos 개발자 사이트의 SDK/에뮬레이터 사용 신청과 약관을 확인.
- Maven: https://nexus-playground.pleos.ai/repository/maven-releases/ (현재 환경에서 별도 자격증명 없이 해석됨).
- local.properties의 sdk.dir는 Android Studio가 해당 PC 경로로 생성. 다른 PC 파일을 복사하지 마세요.
- API 키 없음: Open-Meteo/CAMS, OpenStreetMap Overpass 공개 API 사용.
- 무료/비상업 사용 조건, 트래픽 제한, 출처 표시와 상업 서비스 계약은 배포 전에 재검토.

~~~powershell
# 프로젝트 루트에서 실행. 실제 설치 경로에 맞추세요.
$env:JAVA_HOME = Join-Path $env:ProgramFiles 'Android\Android Studio\jbr'
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest :app:lintDebug
~~~

APK: app/build/outputs/apk/debug/app-debug.apk
JUnit: app/build/reports/tests/testDebugUnitTest/index.html
Lint: app/build/reports/lint-results-debug.html

## 3. 설치 / 기본 확인

기존 AVD를 삭제하거나 Wipe Data 하지 말고 Cold Boot Now로 실행하세요.
~~~powershell
$adb = Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe'
& $adb devices
& $adb shell getprop sys.boot_completed
& $adb shell ip -4 addr show eth0
& $adb shell dumpsys ethernet
& $adb install -r app/build/outputs/apk/debug/app-debug.apk
& $adb shell am start -n com.example.vemoring/.MainActivity
& $adb forward tcp:8088 tcp:8088
Start-Process 'http://localhost:8088/'
~~~
여러 장치가 연결되면 모든 adb 명령에 -s <장치ID>를 넣으세요.
DDS가 필요한 시스템 이미지에서 eth0 10.0.2.15, IpClient와 콘솔 bridge_ready=true를 확인합니다.
기본 SOC 100/주행거리 0, source=default, timestamp=0만으로 SDK 성공이라 판단하지 마세요.

앱에서 기존 차량/관리 기록을 확인하고 홈의 '주변 관리 장소 · 위치 · SDK 진단'으로 이동합니다.
'주차 상태에서 테스트 중' 체크 후 Real 선택.
차량 VIN/CRN은 수신 성공 상태만 표시하며 식별자 원문을 표시·기록하지 않습니다.
차량 이름·연식·파워트레인은 User Input입니다.

## 4. Vehicle

1. 콘솔에서 SOC를 37로 바꾸고 앱 SOC도 37로 수신되는지 비교.
2. 연료 0과 null/미수신/미지원/권한 거부를 구분. 0을 빈 값으로 취급하지 않는지 확인.
3. 누적 주행거리 수신 값은 진단에 원시값으로 표시. 단위는 콘솔/SDK 사양과 대조한 뒤 관리 설정에 명시.
4. HVAC 온도/AUTO/AC/송풍 방향, 영역별 capability 및 안전 경고 변화 확인.
5. 안전 경고가 일반 에너지 메시지보다 먼저 표시되는지 확인.
6. 관리 날짜 수정 시 연결했던 과거 주행거리 기준이 무효화되는지 확인.
7. 오늘 관리 기록에만 '현재 거리 연결' 사용. 과거 기록은 당시 실제 km를 수동 입력.
8. 당시 엔진오일 구현은 사용자가 설명서로 확인한 km만 입력했습니다. 새 목표 기준과 친구의 시험 항목은 [엔진오일 관리 정책](engine-oil/ENGINE_OIL_POLICY.md)을 사용합니다. 기준표·가입 전20km/일·30분 OFF→ON·최종 교체 확인은 문서로 합의된 상태이며 코드 적용·실수신 검증과 구분합니다.

## 5. FusedLocation / 외부 API

1. Pleos 위치 권한 요청 후 허용. 거부하면 PermissionDenied.
2. 마지막 위치 조회: null이면 NoData, 오래된 위치는 사용하지 않음 (15분 이내 기준).
3. 실시간 체크 후 위치 변화 확인. 중지/화면 종료/백그라운드에서 구독·영역 알림 해제 확인.
4. 차량 개인정보 설정에서 위치 차단 후 Blocked와 차단 안내 팝업 확인.
5. 반경 입력 후 영역 등록. onRegistered 콜백을 받은 후 진입/이탈 로그 확인.
6. 'Open-Meteo에 위치 전송'을 눌러 날씨/대기질 수신 및 출처 확인.
7. 장소 검색 버튼은 위도/경도를 overpass-api.de에 전송함. 4종류 장소·좌표·직선거리 확인.
8. HTTP 실패/빈 결과/네트워크 차단 시 가짜 데이터가 나타나지 않는지 확인.
9. PM/dust는 CAMS 모델 자료로 황사 경보나 현장 측정이 아님. 강수는 예보이며 관측 강수로 사용하지 않음.
10. 실제 관측 강수, 해안/비포장도로 자동 판정은 미구현. 관련 데이터 계약/관측소 매핑 후 확장 필요.

## 6. NaviHelper

1. 진단 화면에서 내비 SDK 연결, 목적지/경유지/경로/회전 정보 조회 및 각 콜백 시각 확인.
2. 서비스/권한 오류 후 '내비 SDK 재연결'.
3. 검색 장소 선택 후 사용자 확인 전 목적지/경유지가 바뀌지 않는지 확인.
4. 기존 목적지와 경유지를 조회한 경우 경유지 추가 제안 사용. 2개면 추가 금지.
5. 새 목적지/후보 경유지 경로 요청은 기존 경로 변경 경고를 확인 후 요청.
6. 경로 옵션/재탐색/경유지 제거/목적지 취소도 각각 확인 후 요청.
7. Navigation 화면 열기 확인.
8. 로그에서 onRoutingCompleted 등 실제 이벤트를 확인. '요청 전송'만으로 성공 처리하지 말 것.
9. 도착 이벤트가 관리 기록/주유량/세차 완료일을 바꾸지 않는지 확인.
10. 앱 밖에서 경로가 동시에 변경되면 SDK가 트랜잭션/버전 비교 API를 제공하지 않으므로 조회 후 재확인.
   경유지 추가는 API가 보존하는 기존 목적지를 이용하며 기존 경유지를 덮어쓰는 요청을 만들지 않음.

## 7. 실제 수신 자동 테스트 (정상 장치에서만)

앱을 Real 모드로 두고 Pleos 위치 권한을 먼저 허용하세요.
~~~powershell
.\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.example.vemoring.RealSdkReceptionTest"
~~~
Vehicle: 연료/SOC/누적 거리 중 하나의 Available 숫자가 있어야 통과 (45초).
FusedLocation: 유효하고 최신인 위치 콜백이 있어야 통과 (30초, 로그에서 좌표 숨김).
Navi: 실제 RouteState 콜백이 있어야 통과 (30초).
통과는 각각의 읽기 수신만 검증하며 경로 설정/실차 운행 전체 성공을 뜻하지 않습니다.
이 PC에서는 이 테스트를 실행하지 않았습니다. 계측 APK 컴파일만 완료했습니다.

## 8. Real / Demo

주차 체크 후 Demo 선택: 명시적인 가상 연료/SOC/거리/날씨/장소. 실제 SDK/API 호출 없음.
Real 전환: Demo 수치·위치·날씨·장소·로그 초기화, 실제 어댑터 새 세션.
Demo 숫자로 실제 정비 계산을 하지 않으며 Demo는 기존 차량 기록을 변경하지 않습니다.
앱 재실행은 항상 Real. 위치 자동 추적 시작 없음.

## 휴대폰 에뮬레이터 한글 자판 (2026-11-12)

증상: 휴대폰 앱 입력란에서 한글은 입력되지만 화면 자판이 영문으로 보임. 앱 코드 문제가 아니다(차량·휴대폰 입력란 모두 `KoreanTextKeyboardOptions` ko-KR 힌트 사용).

- 휴대폰 AVD(`sdk_phone64_x86_64`)에는 한국어 자판이 있는 기본 키보드가 없다. Fcitx5는 한글을 조합하지만 키 표시가 영문이다.
- 한글 자판 입력기 ReteKey는 Android가 물리 키보드를 보고하면 화면 자판을 숨긴다(`keyboard != NOKEYS`). x86 에뮬레이터에는 `hw.keyboard=no`여도 PC용 `AT Translated Set 2 keyboard`가 항상 붙어 있어서 이 조건이 참이 된다. 실제 휴대폰에는 이 장치가 없다.
- 해결: 휴대폰 에뮬레이터 부팅 후 `tools\phone-hangul-keyboard.cmd` 실행. 가상 AT 키보드를 다음 부팅 전까지 분리하고 ReteKey를 기본 입력기로 지정한다. 디스크 설정은 바꾸지 않으며, 그동안 PC 키보드로 에뮬레이터에 직접 타이핑하는 기능은 꺼진다(터치 입력은 정상). Pleos에는 실행하지 않는다(스크립트가 휴대폰 AVD가 아니면 중단).
- 휴대폰 AVD `config.ini`는 `hw.keyboard=no`로 바뀌어 있다(이전 값 `yes`, 백업 `build/keyboard-check/phone-avd-keyboard-config.before.ini`).
