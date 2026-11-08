# 실행 지연 점검 (2026-11-08)

## 확인한 내용과 변경

- Pleos AVD: x86_64, WHPX 가속 정상. RAM 4096MB, CPU 3개, 화면 2560×1440, GPU auto.
- 공식 문서 확인 후 Cold Boot 설정을 유지하도록 복구했다. Pleos는 Quick Boot에서 VHAL을 사용할 수 없으므로 부팅 방식 변경을 성능 개선책으로 사용하지 않는다. RAM 4GB도 공식 설치 안내에 명시되어 있다.
- PC RAM 약 16GB, 점검 당시 가용 RAM 약 7.4GiB. 에뮬레이터가 연결되지 않은 시점의 수치이므로 실행 중 메모리 부족 여부는 별도 확인해야 한다.
- 앱 초기 데이터 읽기와 JSON 저장/동기식 commit을 IO 스레드로 이동했다. 저장 성공 후에만 화면 상태를 갱신하고 편집창을 닫는다. 저장 중에는 진행 표시로 중복 수정을 방지한다.
- 달력에서 날짜마다 모든 기록을 필터링하던 연산을 기록 변경 시 한 번 만드는 날짜별 인덱스로 교체했다.
- Gradle 빌드 캐시를 활성화하고 worker를 2개로 제한했다. 에뮬레이터와 동시에 실행할 때 CPU/메모리 경쟁을 줄이기 위한 설정으로, 전체 빌드 시간은 늘어날 수 있다.

## 재현 및 비교

1. Android Studio에서 앱을 Run으로 실행한다. 에뮬레이터 부팅, Gradle 빌드, APK 설치, 앱 첫 화면 표시를 따로 비교한다.
2. Pleos 공식 안내대로 Cold Boot를 사용한다. Quick Boot로 변경하지 않는다.
3. 기본 화면 테스트는 기존 Medium Phone AVD 또는 실제 Android 기기를 사용하고, Pleos 차량 연동 검증은 Pleos AVD에서 수행한다. 두 AVD를 동시에 실행하지 않는다.
4. 앱 내 지연은 데이터 로드, 설정 저장, 달력 월 이동 및 전체 기록 스크롤을 비교한다. 차량 SDK 초기화와 장시간 기록 목록 렌더링은 추가 측정 후보다.

점검 시 연결된 기기가 없어 앱 실행 시간과 프레임 속도의 개선 수치는 측정하지 않았다. 언어 변경 자체가 원인이라는 결론은 내리지 않았다.

참고: https://developer.android.com/studio/run/emulator-acceleration
참고: https://developer.android.com/develop/ui/compose/performance
