# Atlas

> `page:atlas` — 코드 그래프. import · 호출 · 모듈 의존을 노드와 엣지로 시각화

파일 트리는 디렉터리 구조만 보여준다. 무엇이 무엇을 부르고, 어떤 모듈이 어디에 의존하는지는 트리에 드러나지 않는다. Atlas는 소스에서 관계를 뽑아 그래프로 그려, 낯선 코드베이스에 들어온 개발자가 "이 파일이 프로젝트 어디에 붙어 있나"를 눈으로 확인하게 한다.

> English: [main_en.md](https://monkshark.github.io/page-ide/#modules/atlas/main_en.md)

---

## 구성

모듈은 네 층으로 나뉜다.

```mermaid
flowchart LR
    src[소스 파일] --> analyzer
    analyzer[analyzer<br/>import 추출·해석] --> graph
    graph[graph<br/>그래프 모델·쿼리] --> render
    render[render<br/>패널·캔버스]
    graph --> export[export<br/>스냅샷]
```

| 층 | 역할 |
|---|---|
| `analyzer` | tree-sitter로 소스에서 import를 추출하고, 실제 파일 경로로 해석 |
| `graph` | 추출한 관계를 노드·엣지 모델로 쌓고 질의 (사이클, 의존 수 등) |
| `render` | Compose 캔버스·패널로 그래프를 그리고 상호작용 |
| `export` | 그래프 스냅샷을 JSON으로 내보냄 (문서 뷰어 위젯이 소비) |

---

## analyzer — import 추출과 해석

`ImportExtractor`는 tree-sitter 파서로 소스에서 import 구문을 뽑는다. 확장자로 언어를 판정하며, 다음을 지원한다.

Java · Kotlin · Python · JavaScript · TypeScript · Go · Rust · Dart · C · C++ · Scala · Ruby · PHP

추출한 import는 문자열일 뿐이라, 실제 파일로 이어 줘야 엣지가 된다. `ImportResolver`가 공통 해석을 맡고, 패키지 매니페스트가 있는 언어는 전용 resolver가 처리한다.

| resolver | 근거 |
|---|---|
| `GoModResolver` | `go.mod`의 module 경로 |
| `PubspecResolver` | Dart `pubspec.yaml`의 package name |
| `TsConfigResolver` | `tsconfig.json`의 path 매핑 |

`DeclarationIndex`는 심볼 선언 위치를, `StaticCallHierarchySource`는 정적 호출 관계를 모아 호출 그래프의 바탕이 된다.

---

## graph — 모델과 질의

`CodeGraphProvider`는 그래프 데이터의 인터페이스다. `ImportGraphProvider`가 이를 구현해 파일별·프로젝트별 슬라이스를 만든다.

```kotlin
interface CodeGraphProvider {
    fun nodesForFile(path: Path, text: String): GraphSlice
    fun nodesForProject(activePath: Path?, activeText: String?): GraphSlice
}
```

프로젝트 전체 그래프에서는 순환 의존(`projectCycles`), 파일별 피의존 수(`dependentCountOf`), 의존 다이제스트(`dependencyDigest`)를 뽑는다. `ModuleGraph` · `ModuleLayers` · `ModulePath`는 파일 그래프를 모듈 단위로 접어 계층을 만들고, `SymbolGraph`는 심볼 단위 관계를 다룬다.

---

## render — 두 가지 뷰

엔트리 컴포저블은 `AtlasContent`다. 상단 칩으로 두 탭을 오간다.

| 탭 (`AtlasViewTab`) | 내용 |
|---|---|
| `RELATIONS` | `OverviewCanvas` — 노드·엣지 그래프. 줌·팬, 선택, 파일 열기 |
| `ANALYSIS` | `DependencyInsightPanel` — 의존/피의존, 순환 등 텍스트 인사이트 |

호출 그래프는 `CallGraphPanel` · `CallsView`가 별도 뷰로 그린다. `AtlasSearch` · `AtlasSearchBar`로 노드를 찾고, `VcsOverlay`가 변경 상태를 그래프 위에 겹친다. 뷰 상태는 `AtlasViewState`가 보관한다.

---

## IDE 통합

Atlas는 확장 패널(`ExpandedPanel.ATLAS`)로 열린다.

- 에디터 컨텍스트 메뉴 Show in Atlas — 현재 파일을 그래프에서 선택
- 단축키 `FOCUS_IN_ATLAS` → `focusInAtlas(path)` — 활성 파일을 Relations 탭에 포커스
- 탭 전환·패널 크기는 MVI 이벤트(`AtlasViewTabChanged` · `ResizeAtlas` · `FocusInAtlas`)로 흐른다

---

## export — 스냅샷

`SnapshotExporter`는 그래프를 JSON 스냅샷으로 내보낸다. 문서 뷰어의 Atlas 위젯이 이 스냅샷을 읽어 라이브 IDE 없이도 그래프를 보여 준다.

---

- [목차로 돌아가기](https://monkshark.github.io/page-ide/#README_kr.md)
