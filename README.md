# VEMORING

**차량 캐릭터 '베모링'이 주유·충전, 세차, 엔진오일 교체 시기를 표정과 아이콘으로 알려 주는 Pleos 차량용 관리 앱과 휴대폰 동반 앱입니다.**

## 제출 정보 (현대자동차그룹 차량용 앱 개발 공모전)

### APK 위치

| 앱 | 파일 | 패키지 | 설치 대상 |
| --- | --- | --- | --- |
| 차량용 앱 | [apk/VEMORING_VEMORING-Vehicle_release.apk](apk/VEMORING_VEMORING-Vehicle_release.apk) | `com.example.vemoring` | Pleos Connect 에뮬레이터 |
| 휴대폰용 앱 | [apk/VEMORING_VEMORING-Phone_release.apk](apk/VEMORING_VEMORING-Phone_release.apk) | `com.example.vemoring.phone` | Android 9(API 28) 이상 휴대폰·태블릿 |

두 파일 모두 실행 가능한 release 빌드입니다. 공모전 배포용으로, 저장소 clone만으로 다시 빌드할 수 있도록 Android 기본 debug 키로 서명했습니다.

### 주요 기능

- **홈**: 캐릭터와 차량 정보, 남은 연료·배터리와 청결도, 최근 관리일, 이번 주 날씨와 세차 추천일, 근처 주유소·세차장·정비소 바로가기를 보여 줍니다.
- **캐릭터 상태**: 연료 부족은 노랑, 세차 필요는 갈색, 엔진오일 교체 시기는 보라, 두 가지가 겹치면 빨강, 세 가지가 겹치면 차콜 캐릭터로 바뀝니다. 앱 목록 아이콘과 Pleos 하단 고정바·최근 항목 아이콘도 같은 상태로 바뀝니다.
- **관리 기록**: 주유·충전, 세차, 엔진오일 교체 기록을 달력으로 관리합니다. 세차장·정비소 방문이 확인되면 기록을 먼저 반영하고 "세차한 것 같아!" 같은 확인 알림을 띄웁니다. 아니라고 답하면 반영을 취소합니다.
- **엔진오일**: 현대·기아·제네시스 차종별 가혹 조건 교체 주기표를 기준으로 교체 시기를 계산합니다.
- **여러 차량**: 차량을 여러 대 등록할 수 있습니다. 차량 정보를 받는 차는 명시적으로 확인해야만 바뀝니다.
- **휴대폰 동반 앱**: Bluetooth로 차량 앱과 연결해 상태 알림을 받고 기록을 편집합니다. 휴대폰을 재부팅하거나 앱을 업데이트해도 다시 연결됩니다.

### 실행 방법

1. Pleos Connect 에뮬레이터를 **Cold Boot**로 시작합니다. 공식 문서에 따르면 Quick Boot에서는 VHAL을 사용할 수 없습니다.
2. 차량용 APK를 adb로 설치합니다.
   ```powershell
   adb install -r apk\VEMORING_VEMORING-Vehicle_release.apk
   ```
   - 일반 `adb install`은 Pleos의 운전자 계정(user 10)과 시스템 계정(user 0)에 함께 설치합니다.
   - 고정바·최근 항목의 상태 아이콘은 Pleos SystemUI가 시스템 계정 정보로 그림을 찾기 때문에 이 설치 방식이 필요합니다. 추가 adb 명령은 필요 없습니다.
   - `--user` 옵션으로 운전자 계정에만 설치하면 고정바 아이콘이 기본 파란 캐릭터로 표시됩니다. 앱 목록 아이콘과 앱 기능은 정상 동작합니다.
3. 앱 목록(격자 아이콘)에서 **VEMORING**을 실행합니다.
   - 처음 실행하기 전에는 상태별 아이콘 여러 개가 보일 수 있습니다. 앱을 실행하면 바로 하나로 정리됩니다.
4. 차량 등록 순서(제조사·모델 → 연료 → 최근 관리일 → 닉네임)를 마칩니다. 마지막 화면의 `고정바 추가 방법`에서 고정바에 앱을 추가하는 방법을 볼 수 있습니다.
5. 휴대폰 앱은 휴대폰에 설치합니다.
   ```powershell
   adb install -r apk\VEMORING_VEMORING-Phone_release.apk
   ```
   휴대폰 앱의 `차량 앱에서 가져오기`와 차량 앱의 `데이터 연결`에서 Bluetooth로 두 앱을 연결합니다. 두 기기가 페어링되어 있어야 합니다.

### 테스트 환경

| 항목 | 내용 |
| --- | --- |
| Pleos Connect 에뮬레이터 | **Pleos Connect v2.1.0 / Android 14 (API 34)**, 공식 기기 프로필 |
| 화면 규격 | **2560 × 1440 px, 160 dpi**, 가로 화면 |
| 에뮬레이터 메모리 | 4 GB RAM, Cold Boot |
| 휴대폰 테스트 | Android 14 AVD(1080 × 2400, 420 dpi), Galaxy Tab S7(Android 13), Galaxy S24 |
| Bluetooth 실기기 테스트 | Galaxy S24(휴대폰 역할) ↔ Galaxy Tab S7(차량 앱 역할) |
| 개발 도구 | Android Studio, Kotlin, Jetpack Compose, Gradle 9 wrapper, compileSdk 37, minSdk 28 |
| Pleos SDK | Vehicle 2.3.2, FusedLocation 1.1.3, NaviHelper 3.0.5 |

### 필요한 설정

- **차량 앱 권한**: 차량 정보(연료·속도·기어·누적거리), 위치(FusedLocation), 알림, 근처 기기(Bluetooth) 권한을 허용합니다. 거부한 데이터는 '정보 없음'으로 표시하며 예시 값으로 채우지 않습니다.
- **인터넷 연결**: 날씨는 Open-Meteo에서 받습니다. 연결에 실패하면 날씨 칸을 비워 두고 자동으로 다시 시도합니다.
- **휴대폰 앱 권한**: 알림, 근처 기기(Bluetooth), 위치(날씨 지역 확인)를 허용합니다.
- **에뮬레이터 차량 신호**: 연료·속도·기어 같은 값은 Pleos 에뮬레이터의 차량 신호 조절 기능으로 바꿔 볼 수 있습니다.

### 직접 빌드

```powershell
.\gradlew.bat :app:assembleRelease :phone:assembleRelease
```

결과 파일은 `app/build/outputs/apk/release/app-release.apk`와 `phone/build/outputs/apk/release/phone-release.apk`입니다. Android SDK API 37이 필요합니다. Pleos SDK는 공개 저장소(`nexus-playground.pleos.ai`)에서 인증 없이 받습니다.

### 알려진 한계

- 실제 차량, 실제 시동 OFF/ON, 실제 세차장·정비소 방문 판정은 검증하지 않았습니다. 이 부분은 에뮬레이터 신호와 테스트 데이터로만 확인했습니다.
- 하단 고정바 아이콘 변경은 Pleos Connect 2.1.0 에뮬레이터의 GNB 제공자와 호환되도록 만든 기능이며, 공개 SDK 기능이 아닙니다. 다른 Pleos 버전이나 실차에서의 동작은 보장하지 않습니다.

---

## 개발 문서

차량용 앱과 Android 휴대폰용 앱을 함께 관리하는 Kotlin / Jetpack Compose 저장소입니다.
차량용 앱은 Flutter에서 이전했으며 Pleos Vehicle SDK 2.3.2를 사용합니다.

## 앱별 작업 위치

| 구분 | 폴더 / Gradle 모듈 | 실행 환경 |
| --- | --- | --- |
| 차량용 앱 | [app/](app/README.md) / `:app` | Pleos Connect |
| 휴대폰용 앱 | [phone/](docs/PHONE_COMPANION_SYNC.md) / `:phone` | 일반 Android 휴대폰 또는 에뮬레이터 (API 28 이상) |

집 컴퓨터와 노트북, 공동 작업자 모두 같은 저장소를 clone/pull합니다.
Android Studio에서는 저장소 루트를 열고 작업할 앱의 실행 구성을 선택합니다.
기존 `app/`은 차량용으로 유지하며 휴대폰 화면은 `phone/`에 작성합니다.
두 앱은 서로 다른 applicationId로 별도 설치됩니다.
휴대폰 앱은 Bluetooth로 차량 앱 데이터를 받아 상태 알림과 알림 내역을 제공합니다. 실기기 Bluetooth 검증 항목은 [일괄 목록](docs/BLUETOOTH_VALIDATION_CHECKLIST.md)에 남아 있습니다.

## 정비·세차 기준 인계

친구 PC에서 구현·시뮬레이션할 때는 [정비·세차 기준 인계 문서](docs/CARE_CRITERIA_HANDOFF.md)부터 읽습니다.
세차 계산·자동 감지·수동 관리기록 반영, 정비 경고 대상, 실제 SDK 수신 및 Bluetooth 검증 절차를 정리했습니다.
최신 [세부 처리안](docs/CARE_IMPLEMENTATION_DETAILS.md)에는 위치 초기 시험값, 3분 정의, 기록 중복 처리, 휴대폰 기준 설정 동기화, 장기 계산 장부, 경고별 규칙표도 포함합니다.
2026-11-10 추가 [HEV 주유·충전 기준](docs/HEV_FUEL_ALERT_POLICY.md)에는 연료 정수값 전달, 사용자 임계값만의 판단, PHEV·수소차 제외, +10%p/30초 간격/3회 재알림 회복과 예외·캐릭터·시뮬레이션 기준을 정리했습니다. 이 추가 정책은 문서 전달 단계이며 코드 적용·검증 완료가 아닙니다.
해당 자료는 합의된 구현 기준이며, 문서 추가만으로 앱에 기능이 적용되거나 실수신 검증이 완료된 것은 아닙니다.

```powershell
# 작업 전 최신 변경 사항 받기 (로컬 작업은 먼저 커밋)
git pull --ff-only
# 차량용 앱 빌드
.\gradlew.bat :app:assembleDebug
# 휴대폰용 앱만 빌드 (Pleos 에뮬레이터 실행 불필요)
.\gradlew.bat :phone:assembleDebug
```

앱의 데이터 모델이나 통신 규칙을 바꿀 때는 상대 앱에 미치는 영향도 함께 검토합니다.
Git은 소스 코드를 공유하며 두 기기의 사용자 데이터를 동기화하지 않습니다.

## 개발 환경 및 실행

1. 저장소를 clone하고 Android Studio에서 저장소 루트 폴더를 엽니다.
2. SDK Manager에서 프로젝트가 요구하는 Android SDK API 37을 설치하고 Gradle Sync를 실행합니다. Java 도구 체인은 Gradle 설정에 따라 준비됩니다.
3. Android SDK 경로가 들어 있는 `local.properties`는 각자 환경에서 생성합니다. 다른 사람의 파일을 복사하거나 Git에 올리지 않습니다.
4. 차량 연동을 확인할 때는 Pleos Connect 에뮬레이터를 설치하고 실행 기기로 선택합니다. 현재 검증 환경은 Pleos Connect 2.1.0 / API 34입니다.
5. 차량용은 `app`, 휴대폰용은 `phone` 실행 구성을 선택하고 Run을 누릅니다.

Windows에서 디버그 APK 빌드:

```powershell
.\gradlew.bat :app:assembleDebug
```

APK는 `app/build/outputs/apk/debug/app-debug.apk`에 생성됩니다.

## 함께 작업할 때

- 저장소: https://github.com/kimtogether30/car_app
- 기본 브랜치는 `main`이며 Kotlin / Jetpack Compose 프로젝트입니다. 아래 명령으로 받습니다.

```powershell
git clone https://github.com/kimtogether30/car_app.git Vemoring
```

- 기존 Flutter 작업 폴더를 보관하고, 전환된 저장소는 새 폴더에 clone해서 여는 것을 권장합니다. 로컬 작업이 있으면 먼저 커밋하거나 백업합니다.
- Flutter SDK와 `flutter run` 대신 Android Studio와 Gradle을 사용합니다.
- 개인별 작업 브랜치에서 수정하고 Pull Request로 합칩니다.
- SDK 경로, 빌드 결과, 서명 키는 공유하지 않습니다.
- 차량 프로필과 관리 기록은 기기별로 저장되며 Git으로 옮겨지지 않습니다.

이전 범위와 검증 내역은 [MIGRATION.md](MIGRATION.md)를 참고하세요.

Pleos SDK 실행 및 다른 PC 검증: [OTHER_PC_TESTING.md](docs/OTHER_PC_TESTING.md). 기능별 구현/미검증: [SDK_FEATURE_MATRIX.md](docs/SDK_FEATURE_MATRIX.md).
