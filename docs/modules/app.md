# app

> `page/app/` — 조립 계층 / 진입점, Compose 패널, 단축키, 다이얼로그

`page:editor` 의 순수 로직 동작과 `page:ui` 의 디자인 토큰을 실제 화면 / 키 입력 / 파일 다이얼로그에 묶는 곳. 다른 모든 모듈을 의존하지만 다른 모듈은 이 모듈을 의존하지 않는다 (의존 그래프의 leaf)

> English: [app_en.md](https://monkshark.github.io/PAGE_IDE/#modules/app_en.md)

---

## 의존성

| 종류 | 내용 |
|---|---|
| 외부 | Compose Multiplatform Desktop, Compose Material3, Skia (SVG/이미지 디코딩), Swing (`JFileChooser`) |
| 내부 | `page:core`, `page:editor`, `page:ui` |

조립 계층이라 의존이 가장 많다. 대신 *어떤 모듈도 `app` 을 의존하면 안 된다* — 거꾸로 의존하기 시작하면 진입점이 모호해진다

---

## 진입점

### `Main.kt`

```kotlin
fun main() = application { ... }
```

Compose `application` 안에 단일 `Window`. 윈도우 상단에서 다음 상태를 들고 있다.

| 상태 | 타입 | 역할 |
|---|---|---|
| `book` | `TabBook` | 열린 탭과 활성 인덱스 |
| `editorValue` | `TextFieldValue` | 현재 에디터 텍스트 + 선택 범위 |
| `rootDir` | `Path?` | 사이드바 루트 폴더 |
| `expanded` | `Set<Path>` | 사이드바에서 펼쳐진 디렉터리 |
| `sidebarWidth` | `Dp` | 좌측 사이드바 너비 (드래그로 조정) |
| `search` | `SearchState?` | 검색·치환 상태 (`null` 이면 검색바 닫힘) |
| `pendingClose` | `PendingClose?` | 저장 안 된 변경 사항 다이얼로그 트리거 |

활성 탭이 바뀔 때 `LaunchedEffect(book.activeIndex, book.tabs.size)` 로 `editorValue` 를 새 탭의 `text` / `caret` 으로 동기화. `BasicTextField` 의 단일 인스턴스 위에서 *값* 만 갈아끼우는 구조라 활성 탭 전환이 가벼움

### 단축키 매트릭스

`onPreviewKeyEvent` + `onKeyEvent` 양쪽에서 `handleShortcut` 으로 가로채서 처리

| 키 | 동작 |
|---|---|
| Ctrl+O | 파일 열기 (`FileDialogs.open`) |
| Ctrl+Shift+O | 폴더 열기 (`FileDialogs.openDirectory`) |
| Ctrl+S | 저장 (`FileDocument.save` → `markActiveSaved`) |
| Ctrl+W | 활성 탭 닫기 (dirty 면 `UnsavedChangesDialog`) |
| Ctrl+F | 검색바 열기 |
| Ctrl+R | 검색·치환 바 열기 |
| Ctrl+Z | undo (검색바 포커스 시 통과 → 검색 입력칸 자체 undo) |
| Ctrl+Shift+Z / Ctrl+Y | redo |
| Esc | 검색바 닫기 |

검색바가 열려 있을 때 Ctrl+Z 등을 통과시키는 분기가 핵심 — 안 그러면 검색어 한 글자 지우다가 본문이 통째로 되감긴다 ([개발기 #4](https://monkshark.github.io/p/page-ide-undo-per-file/) 참고)

---

## 패널 / Composable

### `EditorPanel`

```kotlin
@Composable
fun EditorPanel(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    search: SearchState?,
    lexer: SyntaxLexer?,
    activePath: Path?,
    ...
)
```

본문 편집 영역. 안에 `BasicTextField` 를 두고, `onValueChange` 단계에서 `AutoClose`, `Indent.maybeUnindentClosingBrace`, `Indent.maybeApplyEnter` 를 순서대로 통과시킨다. `onPreviewKeyEvent` 에서는 Tab / Shift+Tab / Enter / Backspace / Alt+Up·Down 을 직접 가로채 `Indent` / `LineMove` 로 위임

`TextBuffer` 를 `remember(value.text)` 로 보유 — 라인 인덱스 캐시를 한 번만 만들어 라인 번호 거터·상태바·현재 줄 하이라이트가 모두 같은 인덱스를 본다

`CombinedHighlightTransformation` 가 토큰 색·검색 매치 배경·괄호 매칭 배경을 한 번에 입힌다 (`VisualTransformation` 한 단계로 처리해 `BasicTextField` 의 캐럿 매핑이 흐트러지지 않음)

### `FileTreePanel`

```kotlin
@Composable
fun FileTreePanel(
    root: Path?,
    expanded: Set<Path>,
    selectedFile: Path?,
    onToggle: (Path) -> Unit,
    onOpenFile: (Path) -> Unit,
    ...
)
```

좌측 사이드바. `FileTree.listTree(root, expanded)` 결과를 `LazyColumn` 으로 그린다. 디렉터리 클릭은 `onToggle`, 파일 클릭은 `onOpenFile`. `root == null` 일 때는 "Press Ctrl+Shift+O" 안내

### `TabBar`

```kotlin
@Composable
fun TabBar(
    book: TabBook,
    onActivate: (Int) -> Unit,
    onClose: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
)
```

상단 탭 바. dirty 인 탭은 `×` 대신 `●` 점, 호버 시 `×` 로 전환. 드래그 재배치는 `awaitEachGesture` + `touchSlop` 검사로 클릭과 드래그를 구분 — slop 을 넘어선 시점부터만 `onMove` 호출. 휠 스크롤은 가로 스크롤로 매핑

### `LineNumberGutter`

```kotlin
@Composable
internal fun LineNumberGutter(
    lineCount: Int,
    currentLine: Int,
    textStyle: TextStyle,
)
```

좌측 라인 번호 거터. `IntrinsicSize.Max` 로 가장 긴 번호 너비 기준으로 폭이 결정 → 1000 줄을 넘어가도 거터가 흔들리지 않는다. 현재 줄만 `onBackground` 색, 나머지는 `onSurfaceVariant`

### `SearchBar`

```kotlin
@Composable
fun SearchBar(state: SearchState, ...)
```

상단 검색·치환 바. 두 줄 구조 — 첫 줄: 쿼리 입력 + 매치 카운터(`1 / 12`) + 대소문자 토글(`Aa`) + 이전/다음/닫기 칩. 둘째 줄(치환 모드만): 치환 텍스트 입력 + "바꾸기" / "전부 바꾸기" 버튼. Esc → 닫기, Enter → 다음 매치 / 치환

`onWindowShortcut(e)` 위임 한 줄이 핵심. 검색바 입력칸이 처리하지 않은 키는 윈도우 단축키 핸들러로 흘려 — 검색바 안에서도 Ctrl+S 같은 글로벌 단축키가 살아남는다

### `PreviewPanel`

```kotlin
@Composable
fun PreviewPanel(path: Path, kind: FileKind, ...)
```

이미지 / SVG 미리보기. `FileKind.IMAGE` 는 Skia `Image.makeFromEncoded`, `FileKind.SVG` 는 `SVGDOM` 으로 로드. 휠 스크롤 줌, +/− 버튼 줌, 라벨 클릭 시 100% 리셋. 첫 진입 시 70% 핏(이미지가 패널보다 크면 비율 유지로 축소). PNG/JPG/SVG 모두 같은 `ImageViewer` 가 그려 줌 / 패닝 일관성 유지

### `UnsavedChangesDialog`

```kotlin
@Composable
internal fun UnsavedChangesDialog(
    fileNames: List<String>,
    isAppExit: Boolean,
    onSave / onDiscard / onCancel: () -> Unit,
)
```

저장 안 된 변경 사항 확인 다이얼로그. `undecorated` `DialogWindow` 에 `WindowDraggableArea` 로 자체 드래그 영역. 키 매핑: `Y` = 저장, `N` = 저장 안 함, `Esc` = 취소. 메인 윈도우 `onCloseRequest` 가 트리거 — `book.tabs.any { dirty }` 이면 `pendingClose = PendingClose.App` 으로 두고 다이얼로그를 띄운다

---

## 헬퍼

### `FileDialogs`

```kotlin
object FileDialogs {
    fun open(parent: Frame): Path?
    fun saveAs(parent: Frame, suggested: String? = null): Path?
    fun openDirectory(parent: Frame): Path?
}
```

Swing `JFileChooser` 래퍼. Compose Desktop 에서 OS 네이티브 파일 다이얼로그를 빠르게 쓰는 가장 간단한 방법. 향후 OS 별 네이티브 다이얼로그(Windows COM, macOS NSOpenPanel) 가 필요해지면 같은 시그니처로 교체 예정

### `PendingClose`

```kotlin
internal sealed interface PendingClose {
    data class Tab(val index: Int) : PendingClose
    data object App : PendingClose
}
```

`UnsavedChangesDialog` 트리거 종류. `Tab(idx)` 는 특정 탭 닫기, `App` 은 앱 종료 — 다이얼로그 텍스트 ("저장하시겠습니까?" / "저장하지 않고 종료하시겠습니까?") 와 onSave / onDiscard 후속 동작이 분기

---

## 화면 구조

```
Window
├── TitleBar (PAGE · v0.1.0 · 활성 파일 경로)
└── Row
    ├── FileTreePanel (사이드바)
    ├── ResizeHandle (드래그로 폭 조정)
    └── Column
        ├── TabBar
        ├── EditorPanel | PreviewPanel  ← 활성 탭 FileKind 로 분기
        │   └── (EditorPanel 내부) SearchBar + LineNumberGutter + BasicTextField + StatusBar
        └── (UnsavedChangesDialog 는 별도 DialogWindow)
```

---

## 향후 추가 예정

| 패널 | 모듈 |
|---|---|
| Pair 채팅 패널 | `page:pair` (LLMProvider 어댑터) |
| Atlas 코드 그래프 | `page:atlas` |
| Echo 타임라인 | `page:echo` |
| Git diff / 스테이지 | `page:git` |
| 터미널 / 빌드 출력 | `page:runtime` |

---

- [목차로 돌아가기](https://monkshark.github.io/PAGE_IDE/#README.md)
- [아키텍처](https://monkshark.github.io/PAGE_IDE/#guides/architecture.md)
