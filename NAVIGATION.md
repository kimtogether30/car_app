# 주변 장소 검색 연동

홈의 주유소·세차장·정비소 배너를 누르면 매번 지도 앱 선택창을 표시한다. 지원하는 앱 중 현재 차량/에뮬레이터에 설치되어 해당 검색을 처리할 수 있는 앱만 목록에 표시한다. 미설치 앱은 숨긴다. 스마트폰에만 설치된 앱을 차량에 설치된 것으로 취급하지 않는다.

## 연결 방식

- 차량 기본 내비게이션: 공식 `NaviHelper:3.0.5`의 `requestNavigationScreen(NavigationScreenRequest.SearchPlace(keyword))`를 사용한다. 일반 ACTION_SEARCH는 Pleos에서 검색 결과를 열지 않아 제거했다. NAVI_CUSTOM_MAP 권한만 추가했고, 목적지 설정이나 경로 안내 요청은 하지 않는다.
- 네이버지도: `nmap://search?query=<검색어>&appname=<applicationId>`를 `com.nhn.android.nmap`으로 전달한다.
- 카카오맵: `kakaomap://search?q=<검색어>`를 `net.daum.android.map`으로 전달한다.
- 검색 결과의 주변 범위와 위치 사용은 각 지도 앱의 현재 위치·설정에 따른다. VEMORING이 고정 좌표나 가짜 검색 결과를 사용하지 않는다.

`NavigationSearch.kt`에 지도별 요청과 Pleos 클라이언트를 분리했다. 기존 차량 데이터 연동 파일은 수정하지 않았다. Pleos 연결은 선택 시 초기화하며, 연결 대기 제한은 8초이고 배너가 제거되면 연결을 해제한다. 앱 설치 조회는 선택창을 열 때 IO 스레드에서 한 번 실행한다. 상시 위치 조회, 지도 렌더링 SDK, 자동 경로 시작은 추가하지 않았다.

## 확인 범위

- 빌드와 기존 단위 테스트 통과.
- 지도별 한글 검색 링크, 선택창의 미설치 앱 비활성화, 배너의 버튼/스와이프 전환 UI 테스트 통과.
- Pleos에서 주유소·세차장·정비소 각각 배너 → 기본 내비게이션 선택 → 해당 업종의 실제 주변 목록과 거리 표시를 확인했다.
- 현재 에뮬레이터에 카카오맵·네이버지도가 없어 해당 앱의 실제 화면 검증은 수행하지 못했다. 공식 링크 형식과 검색어·패키지 전달을 테스트했다.

공식 자료:

- https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/intro
- https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/declaring-permissions
- https://guide.ncloud-docs.com/docs/maps-url-scheme
- https://apis.map.kakao.com/android_v2/docs/api-guide/urlscheme/
