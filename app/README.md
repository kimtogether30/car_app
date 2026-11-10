# 차량용 앱

기존 Kotlin / Jetpack Compose 기반 Pleos 차량 앱입니다. Gradle 모듈은 `:app`입니다.
기존 실행 설정과 도구 호환성을 위해 `app/` 폴더 이름을 유지합니다.

- 시작 코드: `src/main/java/com/example/vemoring/MainActivity.kt`
- applicationId: `com.example.vemoring` (기존 설치 및 데이터 유지)
- 실행 기기: Pleos Connect 에뮬레이터 또는 지원 차량 환경
- 빌드: 루트에서 `.\gradlew.bat :app:assembleDebug`

휴대폰 화면은 `phone/`에서 개발합니다.
