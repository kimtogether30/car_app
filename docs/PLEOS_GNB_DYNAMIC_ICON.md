# Pleos 하단 캐릭터 아이콘

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
