# 휴대폰용 앱

Android 전용 Kotlin / Jetpack Compose 앱입니다. Gradle 모듈은 `:mobile-app`입니다.
차량 앱과 구분되는 `com.example.vemoring.mobile` applicationId를 사용합니다.

## 실행

1. Android Studio에서 이 폴더가 아닌 저장소 루트를 엽니다.
2. 루트 README의 Java / Android SDK를 준비하고 Gradle Sync를 실행합니다.
3. 실행 구성을 `mobile-app`으로 선택하고 API 28 이상 Android 휴대폰 또는 일반 에뮬레이터에서 실행합니다.

루트에서 빌드: `.\gradlew.bat :mobile-app:assembleDebug`

APK: `mobile-app/build/outputs/apk/debug/mobile-app-debug.apk`

시작 코드: `src/main/java/com/example/vemoring/mobile/MainActivity.kt`

## 현재 범위

시작 화면과 연결 준비 안내만 구현했습니다. 로그인, 차량 페어링, 서버, 실제 데이터 동기화는 아직 없습니다.
Pleos SDK와 차량 전용 권한은 휴대폰 앱 의존성에 포함하지 않습니다.
루트 Gradle 설정을 공유하므로 최초 Sync에는 네트워크와 개발 도구 설치가 필요합니다.

차량 앱의 기기 내부 저장 데이터는 Git pull로 휴대폰에 전달되지 않습니다.
연동 구현 시 두 앱의 사용자/차량 식별 방식, 데이터 형식과 통신 방식을 함께 정합니다.
공유할 모델과 규칙이 확정되면 별도 공통 모듈을 추가합니다.
