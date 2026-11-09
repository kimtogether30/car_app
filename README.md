# VEMORING

차량용 앱과 Android 휴대폰용 앱을 함께 관리하는 Kotlin / Jetpack Compose 저장소입니다.
차량용 앱은 Flutter에서 이전했으며 Pleos Vehicle SDK 2.3.2를 사용합니다.

## 앱별 작업 위치

| 구분 | 폴더 / Gradle 모듈 | 실행 환경 |
| --- | --- | --- |
| 차량용 앱 | [app/](app/README.md) / `:app` | Pleos Connect |
| 휴대폰용 앱 | [mobile-app/](mobile-app/README.md) / `:mobile-app` | 일반 Android 휴대폰 또는 에뮬레이터 (API 28 이상) |

집 컴퓨터와 노트북, 공동 작업자 모두 같은 저장소를 clone/pull합니다.
Android Studio에서는 저장소 루트를 열고 작업할 앱의 실행 구성을 선택합니다.
기존 `app/`은 차량용으로 유지하며 휴대폰 화면은 `mobile-app/`에 작성합니다.
두 앱은 서로 다른 applicationId로 별도 설치됩니다.
휴대폰 앱은 현재 시작 화면만 제공하며 실제 차량 데이터 연동은 아직 구현되지 않았습니다.

## 정비·세차 기준 인계

친구 PC에서 구현·시뮬레이션할 때는 [정비·세차 기준 인계 문서](docs/CARE_CRITERIA_HANDOFF.md)부터 읽습니다.
세차 계산·자동 감지·수동 관리기록 반영, 정비 경고 대상, 실제 SDK 수신 및 Bluetooth 검증 절차를 정리했습니다.
해당 자료는 합의된 구현 기준이며, 문서 추가만으로 앱에 기능이 적용되거나 실수신 검증이 완료된 것은 아닙니다.

```powershell
# 작업 전 최신 변경 사항 받기 (로컬 작업은 먼저 커밋)
git pull --ff-only
# 차량용 앱 빌드
.\gradlew.bat :app:assembleDebug
# 휴대폰용 앱만 빌드 (Pleos 에뮬레이터 실행 불필요)
.\gradlew.bat :mobile-app:assembleDebug
```

앱의 데이터 모델이나 통신 규칙을 바꿀 때는 상대 앱에 미치는 영향도 함께 검토합니다.
Git은 소스 코드를 공유하며 두 기기의 사용자 데이터를 동기화하지 않습니다.

## 개발 환경 및 실행

1. 저장소를 clone하고 Android Studio에서 저장소 루트 폴더를 엽니다.
2. SDK Manager에서 프로젝트가 요구하는 Android SDK API 37을 설치하고 Gradle Sync를 실행합니다. Java 도구 체인은 Gradle 설정에 따라 준비됩니다.
3. Android SDK 경로가 들어 있는 `local.properties`는 각자 환경에서 생성합니다. 다른 사람의 파일을 복사하거나 Git에 올리지 않습니다.
4. 차량 연동을 확인할 때는 Pleos Connect 에뮬레이터를 설치하고 실행 기기로 선택합니다. 현재 검증 환경은 Pleos Connect 2.1.0 / API 34입니다.
5. 차량용은 `app`, 휴대폰용은 `mobile-app` 실행 구성을 선택하고 Run을 누릅니다.

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
