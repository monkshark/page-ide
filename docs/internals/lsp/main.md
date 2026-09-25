# LSP

> `page:lsp` — Language Server Protocol 클라이언트 층. 서버를 띄우고, 프로토콜로 말하고, 언어별 백엔드를 등록

코드 완성·정의 이동·진단은 언어마다 다른 서버가 만든다. 이 모듈은 그 서버들과 LSP로 대화하는 클라이언트 층이다. 서버 프로세스를 띄우는 전송, 초기화와 요청을 처리하는 클라이언트, 언어를 서버에 잇는 백엔드, 그리고 완성·호버·정의 같은 기능별 요청 빌더로 나뉜다.

상위 IDE 오케스트레이션(컨트롤러 생명주기·라우팅·캐시)은 [`page:language`](https://monkshark.github.io/page-ide/#internals/language/main.md)가 이 층 위에 얹는다.

> English: [main_en.md](https://monkshark.github.io/page-ide/#internals/lsp/main_en.md)

---

## 구성

```mermaid
flowchart TB
    reg["LspBackends · LanguageRegistry<br/>언어→서버 등록"] --> backend["LanguageBackend<br/>실행 파일 해석·spawn"]
    backend --> client["LspClient<br/>초기화·capability·리스너"]
    client --> transport["LspTransport<br/>프로세스 stdio"]
    client --> features["기능별 요청<br/>Completion · Hover · …"]
```

| 층 | 역할 |
|---|---|
| 등록 | `LanguageBackend`/`LspBackends`, `LanguageRegistry`/`LanguageDefinition` — 어떤 확장자를 어떤 서버가 맡는지 |
| 클라이언트 | `LspClient` — lsp4j `LanguageClient` 구현, 초기화·capability·서버 알림 수신 |
| 전송 | `LspTransport`(`StreamTransport`·`ProcessTransport`) — 서버 프로세스의 stdin/stdout |
| 기능 | `Completion`·`Hover`·`Definition`·`References`·`Rename`·`SignatureHelp`·`Symbols`·`CallHierarchy`·`InlayHints`·`CodeActions`·`Diagnostic` 등 요청 빌더 |
| 보강 | `CompletionProfile`·`CompletionAugmentor`·`PageQuickFixes` — 서버 응답에 키워드·import·퀵픽스를 더함 |

---

## 백엔드 등록

`LanguageBackend`는 한 언어를 서버에 잇는 인터페이스다.

```kotlin
interface LanguageBackend {
    val id: String
    val displayName: String
    fun supports(extension: String?): Boolean
    fun resolveExecutable(env: Map<String, String>): Resolution
    fun spawn(executable: Path, workspaceRoot: Path?, ...): LspClient
}
```

`resolveExecutable`은 서버 실행 파일을 찾아 `Found`/`NotFound`를 돌려주고, `spawn`은 그 실행 파일로 프로세스를 띄워 연결된 `LspClient`를 만든다. `LspBackends`는 등록된 백엔드를 모아 파일 경로로 알맞은 백엔드를 고른다(`forFile`). 라우팅을 가로챌 필요가 있으면 `routingInterceptor`로 확장자 기본 규칙보다 먼저 판정을 끼워 넣는다.

언어의 정적 메타데이터(확장자, 서버 바이너리 이름, OS별 설치 안내, 실행 인자)는 `LanguageRegistry`가 리소스의 `languages.json`을 읽어 `LanguageDefinition` 목록으로 제공한다. 코드가 아니라 데이터로 언어를 늘릴 수 있게 한 지점이다.

---

## LspClient — 클라이언트와 초기화

`LspClient`는 lsp4j `LanguageClient`를 구현한다. `start()`는 런처를 만들어 서버 프록시를 얻고, `initialize`로 클라이언트 capability를 알린다. 완성 스니펫·resolve, 코드 액션 리터럴·resolve, 콜 계층, 진단 태그(`Unnecessary`·`Deprecated`), `applyEdit`, 작업 진행률(work-done progress)까지 선언해 서버 기능을 최대한 끌어 쓴다.

서버가 보내는 비동기 알림은 리스너로 흘린다. `onDiagnostics`·`onLogMessage`·`onShowMessage`·`onProgress`·`onApplyEdit`로 구독한다. 상태는 `LspState`로 관리하고, 종료는 정상 `shutdown()`과 강제 `forceClose()` 두 갈래다. JDT-LS의 비표준 알림(`language/status` 등)도 조용히 받아 넘긴다.

---

## LspTransport — 프로세스 stdio

`ProcessTransport`는 서버 프로세스의 stdin/stdout을 클라이언트에 잇고, stderr는 데몬 스레드로 퍼 올려 로그로 흘린다. Windows에서는 서버와 자손을 `KILL_ON_JOB_CLOSE`가 설정된 전용 Job Object에 넣는다. PAGE가 강제로 종료되어 정리 코드가 실행되지 않아도 운영체제가 서버 트리를 종료한다. 이미 생성된 자손도 같은 Job에 연결한다. 정상 종료에서는 Job을 먼저 닫고, 남은 프로세스는 `taskkill /F /T`로 정리한다. 그 외 OS에서는 자손 프로세스를 destroy한다.

`LspClient.shutdown()`은 서버 응답을 최대 2초 기다린 뒤 응답 여부와 관계없이 transport를 닫는다. 종료 중인 Kotlin 서버가 분석 완료를 기다리더라도 PAGE가 무기한 응답을 기다리지 않는다. 초기화 전 종료에서도 이미 생성된 transport를 닫는다.

순서가 중요하다. 프로세스를 먼저 죽이고 그다음에 파이프를 닫는다. 반대로 하면 lsp4j 리스너가 read로 물려 있는 stdout을 닫게 되고, Windows에서는 그 `close()`가 돌아오지 않는다. 서버를 멈추는 모든 경로(삭제·재시작·앱 종료)가 이 한 줄에서 멈춰 선다. 닫기 자체도 데몬 스레드에서 2초만 기다려, 어떤 경우에도 호출자를 붙잡지 않는다.

---

## 기능별 요청과 보강

완성·호버·정의·참조·이름 변경·시그니처 도움말·심볼·콜 계층·인레이 힌트·코드 액션·진단은 각각 파일로 나뉘어 lsp4j 요청을 만들고 응답을 IDE가 쓰기 좋은 형태로 옮긴다.

서버 응답만으로 부족한 부분은 이 층에서 보강한다. `CompletionProfile`은 언어별 키워드 셋과 auto-import 지원 여부를 담고, `CompletionAugmentor`는 완성 목록에 키워드와 (워크스페이스 심볼로 찾은) import 후보를 끼워 넣는다. `PageQuickFixes`는 서버가 주지 않는 퀵픽스를 합성한다. `CompletionFrecency`는 자주·최근 고른 항목을 위로 올린다.


---

## 시작 시간 재기

`LspStartupBench`는 언어별 서버가 뜨는 데 걸리는 시간을 한 번에 잰다. 언어마다 프로젝트 하나씩 담은 디렉터리를 가리키면, 각 프로젝트의 대표 소스 파일을 골라 백엔드를 해석하고 spawn한 뒤 `initialize` 응답까지의 시간을 기록한다.

```
./gradlew :page:app:test --tests 'page.app.LspStartupBench' --rerun-tasks -Dpage.lsp.bench=/path/to/lsp-bench -Dpage.lsp.bench.runs=2
```

`page.lsp.bench`를 주지 않으면 조용히 통과한다. 사용법은 그때 출력으로 안내한다.

| 속성 | 뜻 |
|---|---|
| `page.lsp.bench` | 언어별 프로젝트를 담은 루트 (없으면 실행 안 함) |
| `page.lsp.bench.only` | 측정할 프로젝트만 콤마로 지정 |
| `page.lsp.bench.install` | 없으면 먼저 설치할 설치기 id |
| `page.lsp.bench.runs` | 반복 횟수 — cold와 warm을 같이 보려면 2 이상 |
| `page.lsp.bench.timeout` | `initialize` 대기 초 (기본 180) |
| `page.lsp.bench.out` | 리포트 경로 (기본 `<루트>/lsp-startup.txt`) |

프로젝트의 언어는 디렉터리 이름으로 잡고, 이름과 맞는 백엔드가 없으면 소스 파일이 가장 많이 쓰는 백엔드로 정한다. `json`·`yaml` 같은 설정 파일은 이 판정에서 빠진다.

반복이 필요한 이유는 캐시다. solargraph·sourcekit-lsp·Dart Analysis Server는 첫 기동에 타입맵이나 툴체인 색인을 만들어서 1회차와 2회차가 열 배 넘게 벌어진다.

---

- [목차로 돌아가기](https://monkshark.github.io/page-ide/#README_kr.md)
