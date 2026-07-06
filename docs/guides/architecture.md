# 아키텍처

> English: [architecture_en.md](https://monkshark.github.io/page-ide/#guides/architecture_en.md)

> 모듈 경계, 의존 방향, 기술 스택 결정.

PAGE 는 16 개 Gradle 모듈로 나뉜다. 경계는 의존이 한 방향으로만 흐르도록 잡았고, 대부분 모듈은 이미 구현돼 있다. `echo` · `pair` 두 모듈만 아직 스캐폴딩 단계다.

---

## 기술 스택

| 영역 | 선택 | 비고 |
|---|---|---|
| 언어 | Kotlin (JVM 21+) | 자바 생태계 라이브러리 그대로 활용 |
| UI | Compose Multiplatform | 데스크톱은 Skia, docs 뷰어는 wasmJs 타깃 |
| 빌드 | Gradle (Kotlin DSL) | 멀티모듈 16 개 |
| LSP | LSP4J (Eclipse) | `lsp` 모듈의 전송·초기화 층 |
| 신택스 | Tree-sitter | `atlas` 의 import·심볼 추출 (JNI). 에디터 하이라이팅은 자체 렉서 |
| Git | `git status --porcelain` 서브프로세스 | 시스템 git 호출, JGit 미사용 |
| PTY | pty4j | `runtime` 내장 터미널 |
| 로컬 저장 | SQLite (xerial JDBC) | Echo 타임라인 (예정) |
| AI HTTP | OkHttp | `LLMProvider` 인터페이스 + 어댑터 (Pair, 예정) |

---

## 모듈 구조

`shared-core` 와 `docs-viewer` 는 멀티플랫폼(jvm+wasmJs) 모듈이라 레포 루트에 있고, 나머지 데스크톱 모듈은 `page/` 아래에 있다.

```
page-ide/
├── shared-core   (Compose MPP: 마크다운·JSON 파서, 그래프 모델, FilePath)
├── docs-viewer   (Compose wasmJs: 공개 docs 뷰어 + 위젯 섬)
└── page/
    ├── core         (앱 정체성, 공통 도메인 타입)
    ├── perf         (시작 성능 계측, UI 멈춤 감시)
    ├── editor       (텍스트 버퍼, 편집 이력, 신택스 렉서, 퍼지/빠른 열기)
    ├── ui           (Compose 컴포넌트, Glass 디자인 토큰, 신택스 팔레트)
    ├── lsp          (LSP4J 클라이언트: 전송·초기화·백엔드 등록)
    ├── language     (언어 지능 오케스트레이션: 라우팅·문서 동기화·완성·진단)
    ├── runtime      (툴체인·언어 서버 설치, pty4j 실행, 내장 터미널)
    ├── workspace    (파일 트리, 파일 조작, 이름 변경 리팩터, 프로젝트 검색)
    ├── atlas-view   (Compose MPP: overview 그래프 모델 + 렌더)
    ├── atlas        (tree-sitter 코드 그래프 분석, IDE 패널, 스냅샷 export)
    ├── git          (`git status --porcelain` 기반 워크스페이스 VCS 상태)
    ├── echo         (키스트로크 레코더 + 타임라인 — 스캐폴딩)
    ├── pair         (LLMProvider 어댑터: 채팅·관찰자·에이전트·튜터 — 스캐폴딩)
    └── app          (조립층, 메인 진입점)
```

각 모듈의 상세 책임은 [목차](https://monkshark.github.io/page-ide/#README_kr.md)의 모듈별 문서로 이어진다.

---

## 의존 방향

아래 그래프는 현재 모듈이 서로 어떻게 의존하는지 그대로 보여준다. 노드에 마우스를 올리면 이웃만 남고, 휠로 확대·드래그로 이동한다.

```page-widget
atlas
```

의존은 위에서 아래로만 흐르고 순환이 없다.

- `core` (JVM 공통) 와 `shared-core` (Compose MPP) 는 아무것도 의존하지 않는 바닥층이다.
- `editor` 는 `ui` · `language` · `workspace` 가 함께 딛는 텍스트 기반이라, 기능 모듈이 그 위에 선다.
- 기능 모듈은 필요한 바닥·기반 모듈(`core` · `editor` · `lsp` · `runtime` · `ui`)만 아래로 의존하고, 서로 옆으로는 의존하지 않는다.
- `atlas-view` 는 `atlas` 에서 overview 렌더만 멀티플랫폼으로 뽑아낸 층이라, 데스크톱 `atlas` 와 wasm `docs-viewer` 가 같은 렌더 코드를 공유한다.
- 조립과 와이어링은 `app` 이 전담한다.

---

## AI 프로바이더 전략

`pair` 모듈은 아직 스캐폴딩이지만, 층 설계는 정해져 있다. `LLMProvider` 인터페이스를 두고 어댑터 네 가지로 갈아끼운다.

```kotlin
interface LLMProvider {
    suspend fun complete(prompt: Prompt): Flow<TokenChunk>
    fun supportsTools(): Boolean
}
```

- Ollama — 로컬, 기본. 코드가 PC를 떠나지 않는다.
- Anthropic Claude — 사용자 본인 API 키.
- OpenAI ChatGPT — 사용자 본인 API 키.
- OpenAI 호환 endpoint — Together AI / Groq / 자체 호스팅 등 endpoint URL 직접 입력.

API 키는 OS 키체인에 저장한다 (Windows Credential Manager 등). 평문 저장 금지.

---

- [개요로 돌아가기](https://monkshark.github.io/page-ide/#guides/overview.md)
- [목차로 돌아가기](https://monkshark.github.io/page-ide/#README_kr.md)
