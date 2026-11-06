# VEMORING

**Vehicle + Emotion + Ring** — 차량의 감정을 운전자와 연결합니다.

차량 상태를 표정 아이콘과 알림 메시지로 직관적으로 전달하고, 주유·세차·정비가 필요한 시점을 알려주는 차량 관리 앱입니다.

## Pleos Vehicle SDK 연동

### 확인된 공식 버전

- Vehicle SDK: `2.3.2`
- Vehicle Service: `2.3.21`
- Android minimum SDK/API: 28 이상
- Pleos Emulator 권장 플랫폼: Android API 34 Automotive/AIVD
- 공식 Maven 저장소: `https://nexus-playground.pleos.ai/repository/maven-releases/`

Android Gradle에서 Maven 저장소와 SDK 의존성이 설정되어 있습니다. EV 배터리 잔량 조회 API는
`getEvBatteryStateOfCharge()`이며 반환값은 SDK 문서상 `Int`이며 0~100 범위의 상태 충전율로 사용합니다.
운전석 문 검증 API는 공식 예제의 `DoorArea(DoorValue.ROW_1_LEFT)`와
`getCurrentDoorPosition()`입니다.

### 두 PC에서 실행하기

1. Android Studio, Flutter SDK, Android SDK API 34 및 Android API 28+ SDK platform-tools를 설치합니다.
2. `SDK Manager > SDK Update Sites`에 개발자에게 제공된 Pleos Connect Emulator system image URL을 추가합니다.
3. API 34 Pleos Connect system image로 Automotive AVD를 만들고 cold boot 후 실행합니다.
4. 저장소 루트에서 `git switch feature/pleos-vehicle-sdk`를 체크아웃합니다.
5. 각 PC의 Android Studio에서 `File > Sync Project with Gradle Files`를 실행합니다.
6. Pleos AVD를 선택하고 다음 명령으로 실행합니다.

```powershell
flutter pub get
flutter run
flutter analyze
cd android
.\\gradlew.bat :app:assembleDebug
```

`android/local.properties`에는 각 PC의 개인 `flutter.sdk` 경로만 작성하고 Git에 커밋하지 않습니다.
Pleos emulator URL, 서명 정보 및 SDK 인증키가 있다면 개인 로컬 설정에만 보관합니다.

### 권한 및 데이터 상태

- `pleos.car.permission.CAR_ENERGY`: EV 배터리 데이터 조회
- `pleos.car.permission.CAR_DOORS`: 운전석 문 상태 검증

앱은 SDK 미수신, 미지원 항목, 실제 0%를 구분합니다. 화면의 배터리 값 옆에는
`Pleos 실데이터`, `예시 데이터`, `SDK 항목 미지원`, `Pleos 미수신` 출처가 표시됩니다.
일반 Android, iOS, 웹, Windows 빌드에서는 Pleos 전용 채널을 호출하지 않아 앱이 충돌하지 않습니다.

현재 세션에는 Pleos Emulator가 없으므로 실차 또는 Emulator 데이터 수신은 확인되지 않았습니다.
Emulator에서 실행할 때 배터리가 미지원이면 운전석 문 조회로 연결 검증을 먼저 수행할 수 있습니다.
