# VEMORING

현대자동차 앱 개발 공모전을 위해 Flutter 앱에서 Kotlin / Jetpack Compose로 이전한 Android 프로젝트입니다. Pleos Vehicle SDK 2.3.2를 사용합니다.

## 개발 환경 및 실행

1. 저장소를 clone하고 Android Studio에서 저장소 루트 폴더를 엽니다.
2. SDK Manager에서 프로젝트가 요구하는 Android SDK API 37을 설치하고 Gradle Sync를 실행합니다. Java 도구 체인은 Gradle 설정에 따라 준비됩니다.
3. Android SDK 경로가 들어 있는 `local.properties`는 각자 환경에서 생성합니다. 다른 사람의 파일을 복사하거나 Git에 올리지 않습니다.
4. 차량 연동을 확인할 때는 Pleos Connect 에뮬레이터를 설치하고 실행 기기로 선택합니다. 현재 검증 환경은 Pleos Connect 2.1.0 / API 34입니다.
5. 실행 구성을 `app`으로 선택하고 Run을 누릅니다.

Windows에서 디버그 APK 빌드:

```powershell
.\gradlew.bat :app:assembleDebug
```

APK는 `app/build/outputs/apk/debug/app-debug.apk`에 생성됩니다.

## 함께 작업할 때

- 저장소: https://github.com/kimtogether30/car_app
- Android 전환 브랜치: `migration/native-android`. `main`에 반영되기 전에는 아래 명령으로 받습니다.

```powershell
git clone --branch migration/native-android https://github.com/kimtogether30/car_app.git Vemoring
```

- 기존 Flutter 작업 폴더를 보관하고, 전환된 저장소는 새 폴더에 clone해서 여는 것을 권장합니다. 로컬 작업이 있으면 먼저 커밋하거나 백업합니다.
- Flutter SDK와 `flutter run` 대신 Android Studio와 Gradle을 사용합니다.
- 개인별 작업 브랜치에서 수정하고 Pull Request로 합칩니다.
- SDK 경로, 빌드 결과, 서명 키는 공유하지 않습니다.
- 차량 프로필과 관리 기록은 기기별로 저장되며 Git으로 옮겨지지 않습니다.

이전 범위와 검증 내역은 [MIGRATION.md](MIGRATION.md)를 참고하세요.
