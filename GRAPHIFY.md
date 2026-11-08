# Graphify 사용

이 PC에는 공식 `graphifyy==0.9.104`를 전용 Python 환경 `%LOCALAPPDATA%\Graphify\venv`에 설치했다. Codex 스킬은 `%USERPROFILE%\.codex\skills\graphify`에 있다. 사용자 PATH를 추가했으므로 새 터미널에서 `graphify` 명령을 사용할 수 있다. 현재 터미널에서는 아래 래퍼를 사용하면 된다.

프로젝트 루트의 PowerShell에서:

```powershell
# 코드 인덱스 생성/갱신: 코드만 로컬 분석, AST 작업자 1개
.\tools\graphify.cmd

# 변경할 기능과 연결된 코드 탐색
.\tools\graphify.cmd query "GarageStore save" --budget 1500
.\tools\graphify.cmd query "SetupScreen" --budget 1500

# 필요할 때만 보고서 갱신 (HTML 생략)
.\tools\graphify.cmd cluster-only . --no-viz
```

Codex의 다음 턴부터 설치된 스킬을 사용할 수 있다. 예: “Graphify로 SetupScreen과 연결된 코드를 확인해 줘. 코드만 로컬 분석하고 작업자는 1개로 제한해 줘.”

## 이 PC에 맞춘 운영

- 상시 watch, 자동 Git/에이전트 훅, 추가 AI 모델, Cloud 연동은 설정하지 않았다.
- 빌드/에뮬레이터 실행과 동시에 전체 인덱스를 반복 생성하지 않는다. 구조 변경 후 필요한 시점에 갱신하고, 평소에는 기존 그래프를 조회한다.
- 래퍼는 AST 작업자와 숫자 연산 라이브러리 스레드를 1개로 제한한다. 직접 `graphify extract`를 사용한다면 `--code-only --max-workers 1`을 지정한다.
- 빌드 산출물, IDE 캐시, SDK 로컬 설정, 키스토어는 분석 대상에서 제외한다. 생성된 `graphify-out/`은 Git에서 제외한다.
- 앱/에뮬레이터 실행 속도를 직접 높이는 도구는 아니다. 코드 관계를 찾아 수정 범위와 반복 탐색을 줄이는 보조 도구다. 앱 성능은 별도의 실행 측정으로 확인한다.
- 첫 분석은 코드 18개, 노드 174개, 관계 333개였다. Gradle Kotlin DSL과 일부 최상위 값 선언에는 심볼 추출 한계가 있으며 XML은 이번 코드 분석에 포함되지 않았다. 중요한 관계와 추론은 원본 코드로 확인한다.
- 분석 보고서의 커뮤니티 이름은 로컬 기본값이다. 이름 생성을 위해 API 키를 추가할 필요는 없다.
- 앱 의존성 및 공모전의 Pleos 실행 조건에는 영향이 없다.

공식 문서: https://docs.graphify.com/open-source/quickstart
공식 소스: https://github.com/Graphify-Labs/graphify
