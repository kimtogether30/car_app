# Pleos 하단 캐릭터 아이콘

## 제출용 release APK: adb 준비 없이 동작 (2026-11-15)

아래 '에뮬레이터 설치 조건'의 `install-existing`/`run-as` 준비는 더 이상 필요하지 않다.

**확인한 사실 (Pleos 2.1.0)**
- 같은 GNB 행을 FUEL alias로 바꿨을 때, 시스템 계정(user 0)에서 그 alias가 꺼져 있으면 파란 기본 아이콘이 표시됐다. 켜면 노란 아이콘이 표시됐다.
- `string_icon` 열은 무시된다.
- shell은 다른 앱의 컴포넌트 상태를 바꿀 수 없다(SecurityException).
- user 0에 설치된 앱은 한 번도 실행되지 않아 `stopped` 상태다. 따라서 user 0에서 부팅 수신기도 실행되지 않는다.

**변경**
- manifest의 여섯 `Launcher*` alias 기본값을 모두 `enabled="true"`로 바꿨다. 실행되지 않는 user 0에는 여섯 아이콘이 모두 남는다.
- 실제로 앱을 실행하는 계정에서는 `MainActivity` 시작 시 `MascotLauncher.ensureSingleEntry`가 항목을 하나만 남긴다. 명시적으로 켜진 상태가 있으면 그 상태를, 새 설치면 NORMAL을 남긴다.
- 이 정리는 등록 여부와 아이콘 갱신 설정에 관계없이 실행된다. 이미 하나뿐이면 아무것도 바꾸지 않는다.

**검증**
- 별도 패키지(`.freshcheck`)로 만든 release를 일반 `adb install`로 새로 설치했다.
- user 0과 user 10에 모두 설치됐다. 첫 실행 뒤 user 10의 앱 목록 항목은 1개, user 0은 6개였다.
- 해당 GNB 행을 OIL alias로 바꾸자 adb 준비 없이 보라 아이콘이 표시됐다.
- 테스트 패키지와 GNB 행은 삭제했다.
- `MascotLauncherTests` 3건(새 `firstStartCollapsesDefaultEnabledAliasesToOneEntry` 포함)이 Tab S7에서 통과했다.

**남은 제약**
- `--user 10`처럼 운전자 계정에만 설치하면 user 0 메타데이터가 없어 기본 아이콘이 표시된다.
- 앱을 처음 실행하기 전에는 운전자 계정 앱 목록에도 상태별 아이콘이 여러 개 보인다.

**함께 발견한 문제**
- release의 R8 `packageScope` 최적화 빌드는 시작 시 `IllegalAccessError`(androidx.startup)로 종료됐다. 그래서 release 최적화를 끄고, 최적화하지 않은 빌드를 Pleos에서 실행해 확인했다.

## 첫 등록의 선택형 고정바 안내 (2026-11-15 사용자 요청)

차량 등록 마지막 요약 화면에 `고정바 추가 방법`을 제공한다. 앱 목록(격자) 열기 → VEMORING 길게 누르기 → 편집 가능한 고정바 칸으로 드래그 순서이며, 실제 추가는 사용자가 시스템 UI에서 수행한다. 앱이 고정 위치를 자동 변경하지 않는다. 안내의 `확인`/`나중에 할게요`는 창을 닫으며, 추가 여부와 관계없이 `VEMORING 시작하기`로 등록을 마칠 수 있다. 아이콘 갱신 설정 위치도 함께 안내한다.

절차 근거: [공식 GNB 편집 안내](https://document.pleos.ai/en/docs/connect/guide/docs-design/components/gnb). 기존의 온보딩 초대 제거 정책은 이번 사용자 요청으로 대체한다. Pleos에서 안내 열기/건너뛰기/등록 완료와 한글 닉네임·기존 기록 보존의 `SetupReadabilityTests` 2건 통과(23.956초), 실제 안내 캡처 확인. 로컬 증거: `.tmp/final-check-2026-11-15/pin-onboarding-test.txt`, `pin-onboarding-guide.png`.

## 가져온 해결 방법 보존 (2026-11-15)

해결 커밋: `f7575316271e844406c84f7d95852958b5591adf` (`Sync Pleos GNB icon with current mascot state`). 재발 시 `MascotLauncher.sync` → `PleosGnbIcon.sync` 연결과 MainActivity의 실제 수신 차량 상태 전달을 먼저 확인한다. 런처 alias만 전환하면 고정바/최근 항목에 저장된 이전 component는 남으므로, 기존 GNB 행의 id와 intent component도 같은 Launcher alias로 갱신해야 한다. 고정 위치/다른 앱/Intent extras를 보존하고 고정되지 않은 동일 실행 기록만 중복 정리한다.

아래 시스템 계정 메타데이터 준비 조건도 해결 방법의 일부다. 일반 삼성 태블릿의 최근 앱 화면은 Pleos GNB가 아니므로 패드 검사 통과를 Pleos 고정바 검증으로 대체하지 않는다. 아래의 기존 Pleos 검증 기록은 해당 커밋 작성 당시 결과이며, 후속 패드 점검 결과와 구분한다.

이번 PC 재검증에서도 코드 설치만으로는 provider 주소 검사에 통과하면서 실제 고정바 그림은 파랑으로 남았다. 문서의 `install-existing --user 0` 및 시스템 계정 여섯 alias 활성화를 적용하고 차량 앱 프로세스를 종료한 뒤 다시 검사하자 NORMAL/FUEL/WASH/OIL/ANGRY/EXHAUSTED의 큰 캐릭터·고정 슬롯·최근 항목 그림이 모두 일치했다. 따라서 주소 검사만으로 해결 판정을 내리지 말고 실제 캡처를 확인해야 한다. 최종 `PleosGnbIconTests` 1건 통과(17.443초). 현재 계정의 실제 차량 앱 런처는 1개이며 고정 슬롯 터치로 MainActivity가 열림을 확인했다. 로컬 캡처: `.tmp/final-check-2026-11-15/pleos-final-*.png`. 세부 결과와 시험 한계는 [최종 점검 보고서](FINAL_FUNCTION_CHECK_2026-11-15.md)에 기록한다.

대상: Pleos Connect v2.1.0 / API 34. 공개 SDK 기능이 아니라 이 버전의 exported GNB provider에 대한 호환 처리다. 다른 Pleos 버전과 실차에서의 동작은 보장하지 않는다.

## 구현

- 큰 캐릭터와 동일한 `MascotMood`를 사용한다. 정상 파랑 / 연료 노랑 / 세차 갈색 / 엔진오일 교체 시기 보라 / 두 조건 빨강 / 세 조건 차콜이다.
- 앱 목록은 기존 `Launcher*` 별칭 중 하나만 활성화한다.
- 하단도 현재 활성화한 `Launcher*` 별칭으로 맞춘다. 앱 목록과 하단에서 동일한 실행 컴포넌트를 사용하므로 재부팅 시 별도 항목이 추가되지 않는다. 모두 기존 MainActivity를 연다.
- `PleosGnbIcon`은 현재 계정의 `content://com.android.systemui.gnb/gnb-mixed`에서 베모링의 정확한 별칭과 실행 주소가 일치하는 기존 행만 갱신한다. `id`와 Intent component를 바꾸되 배치는 보존한다. 과거 별칭 변경으로 남은 **어느 영역에도 고정되지 않은 중복 실행 기록**은 동일한 실행 내용별로 가장 최근 행 하나로 합친다. 서로 다른 extras/실행 내용, 고정된 항목, 다른 앱은 합치거나 삭제하지 않는다. 유지하는 행의 최근 사용 시각과 활성 상태는 그대로 둔다. 새 바로가기는 만들지 않는다. 이전 로컬 실험의 `Gnb*` 주소는 기존 `Launcher*`로 이관한다.
- 변경된 컴포넌트를 받은 GNB가 아이콘을 다시 읽는다. 같은 컴포넌트로 그림만 바꾸는 방법은 GNB의 갱신 생략 조건 때문에 사용하지 않는다.
- 디스크/IPC 작업은 IO 작업자에서 상태 변경 시에만 수행한다. 주기적 조회, 루트, 시스템 APK 교체, VHAL 변경은 없다. Provider가 없거나 접근이 거부되면 기존 앱 목록 아이콘 처리만 유지한다.
- 실제 등록 완료 차량 중 수신 차량의 실제 화면 상태만 전경 갱신에 사용한다. 다른 저장 차량 조회나 미리보기는 실제 companion 데이터로 발행하지 않는다. 아이콘 설정과 백그라운드 수신 설정은 별개다.

## 에뮬레이터 설치 조건

이 이미지의 SystemUI는 현재 운전자 계정에서 GNB 구성을 읽지만 activity icon 조회에는 시스템 계정의 PackageManager를 사용한다. 운전자 계정(user10)에만 APK를 설치하면 주소 갱신은 성공해도 activity icon 조회가 실패하고 기본 파란 application icon으로 대체된다.

따라서 개발용 AVD에는 **실제 차량 앱**의 메타데이터가 시스템 계정에도 등록되어 있고, 시스템 계정에서 여섯 별칭의 아이콘을 조회할 수 있어야 한다. 기존 user10 설치와 데이터를 유지한 채 debug APK에 대해 다음 명령을 실행한다.

```powershell
C:\friend\platform-tools\adb.exe -s emulator-5554 shell cmd package install-existing --user 0 com.example.vemoring
$aliases = 'LauncherNormal','LauncherFuel','LauncherWash','LauncherOil','LauncherAngry','LauncherExhausted'
foreach ($alias in $aliases) {
    C:\friend\platform-tools\adb.exe -s emulator-5554 shell run-as com.example.vemoring --user 0 /system/bin/pm enable --user 0 "com.example.vemoring/.$alias"
}
```

이것은 debug AVD 준비 단계이며 앱에서 계정 간 권한을 우회해 실행하지 않는다. 운전자(user10)의 활성 런처는 계속 하나만 유지한다. 시스템 계정은 헤드리스 계정으로 아이콘 메타데이터 조회에만 사용한다. 휴대폰 앱/verification 앱을 Pleos에 설치하는 단계가 아니다. Release APK는 run-as를 사용할 수 없으므로 이 준비 방법을 상용 설치 방법으로 제시하면 안 된다.

실차/App Market 설치가 시스템 계정에 메타데이터를 제공하는지는 미검증이다. 따라서 공모전 자료에서 이 기능을 공개 SDK로 보장되는 실차 기능으로 설명하면 안 된다.

## 검증

`PleosGnbIconTests`는 일반 앱 UID에서 provider 갱신을 실행하고, 기존 바로가기의 배치와 다른 앱 행을 비교한다. 차량 등록/VHAL/정비 기록을 쓰지 않고, 테스트 종료 시 기존 실행 주소와 런처 상태를 복원한다. 표시용 상태는 debug 미리보기에서만 사용한다. 화면 캡처 실패는 데이터 검증과 구분한다.

2026-11-15 검증: Pleos에서 NORMAL/FUEL/WASH/OIL/ANGRY/EXHAUSTED 여섯 상태 모두 큰 캐릭터와 하단 아이콘이 일치하는 실제 캡처를 확인했다. 최종 중복 수정본에서 `PleosGnbIconTests` 1건과 기존 `MascotLauncherTests` 2건, 총 3건 통과(94.196초). 앱 목록의 활성 실행 항목 1개, 상태 변경 후 앱 재실행 시 작업 1개 유지, 중복 최근 기록 정리, 기존 하단 배치 및 다른 앱 행 보존을 확인했다. 정비/차량 기록을 생성하지 않았다. 테스트용 조건이며 실차 수신 검증은 아니다.

중간 빌드의 Cold Boot에서 별도 `Gnb*` 실행 컴포넌트와 앱 목록 항목이 각각 최근 실행 기록으로 남아 중복 표시되는 문제를 발견했다. 최종 구현은 하단 실행 주소를 앱 목록과 같은 `Launcher*`로 통일하고 과거 중복 기록을 정리한다. SDK 연결이 없어도 화면에 표시되는 기간 기반 정비 상태를 전경 아이콘에 반영하도록 연결했으며 SDK 데이터의 가용 상태 자체는 변경하지 않는다.

최종 APK SHA-256: `a6e62f3f2ce7e7d936a244a89ad67c5b2db71e6a13f915e08c2d2517890c6ed9`.
`sync` 후 공식 프로필의 Cold Boot를 수행하고 설치 파일 해시 일치를 확인했다. 재부팅 후와 하단 실제 터치 후 모두 베모링 GNB 행은 `_id=53 / LauncherNormal` 한 개로 유지되었고, 실제 앱 화면이 `LauncherNormal`로 열렸다. 최종 비교 캡처는 로컬 `.tmp/gnb-six-states-verified.png`에 저장했다.

공식 GNB의 편집 영역/최근 앱 설명: https://document.pleos.ai/en/docs/connect/guide/docs-design/components/gnb

공모전 개발 환경 및 SDK 범위: https://appdevchallenge.auto-hmg.io/faq
