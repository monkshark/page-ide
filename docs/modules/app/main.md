# Main

> `page/app/src/main/kotlin/page/app/Main.kt` — `application { ... }` 진입점 + 윈도우 상태

윈도우/탭/사이드바/검색/저장 다이얼로그를 묶는 톱레벨. `TabBook`, `TextFieldValue`, `rootDir`, `expanded`, `sidebarWidth`, `search`, `pendingClose` 가 모두 여기서 산다

> English: [main_en.md](https://monkshark.github.io/page-ide/#modules/app/main_en.md)

---

## 윈도우 상태

```kotlin
var book by remember { mutableStateOf(TabBook()) }
var editorValue by remember { mutableStateOf(TextFieldValue("")) }
var rootDir by remember { mutableStateOf<Path?>(null) }
var expanded by remember { mutableStateOf<Set<Path>>(emptySet()) }
var sidebarWidth by remember { mutableStateOf(260.dp) }
var search by remember { mutableStateOf<SearchState?>(null) }
var pendingClose by remember { mutableStateOf<PendingClose?>(null) }
```

| 필드 | 의미 |
|---|---|
| `book` | 모든 탭과 활성 인덱스 (불변 자료구조 → 매 변경마다 새 인스턴스) |
| `editorValue` | 활성 탭의 본문/캐럿 (Compose `TextFieldValue`). 활성 탭이 바뀌면 동기화 |
| `rootDir` / `expanded` | 사이드바 트리 — 루트 + 펼친 디렉터리 집합 |
| `sidebarWidth` | 드래그 핸들로 조정. 기본 `260.dp` |
| `search` | `null` 이면 검색 바 숨김 |
| `pendingClose` | dirty 닫기 요청. `null` 이 아니면 다이얼로그 표시 |

---

## 활성 탭 ↔ editorValue 동기화

```kotlin
LaunchedEffect(book.activeIndex, book.tabs.size) {
    val active = book.tabs.getOrNull(book.activeIndex)
    editorValue = active?.let {
        TextFieldValue(it.text, TextRange(it.caret))
    } ?: TextFieldValue("")
    search = search?.retargetedFor(active?.text)
}
```

탭 전환/새 탭 시 `editorValue` 를 그 탭의 본문/캐럿으로 갈아끼운다. 검색이 켜져 있으면 새 본문으로 매치 재계산

---

## 핵심 핸들러 (요약)

| 이름 | 역할 |
|---|---|
| `openInTab(path, text)` | `book = book.openOrFocus(path, text)` — 같은 경로 탭 있으면 활성화만 |
| `openFile()` | `FileDialogs.open` → 텍스트 읽고 `openInTab` |
| `openFolder()` | `FileDialogs.openDirectory` → `rootDir` 설정 |
| `saveFile()` | 활성 탭 텍스트를 디스크에 쓰고 `book.markActiveSaved()` |
| `requestCloseTab(i)` / `closeActiveTab()` | dirty 면 `pendingClose = PendingClose.Tab(i)`, 아니면 즉시 닫기 |
| `requestExit()` | dirty 가 있으면 `pendingClose = PendingClose.App`, 아니면 `exitApplication()` |
| `openSearch()` / `openReplace()` / `closeSearch()` | `search` 토글 |
| `onSearchNext` / `onSearchPrev` | 매치 이동 + `moveCaretToActiveMatch` 로 캐럿 동기화 |
| `onReplace` / `onReplaceAll` | `Replace.applyCurrent` / `applyAll` 후 본문 갱신 |
| `doUndo` / `doRedo` | `book.undoOnActive(...)` / `redoOnActive(...)` 결과로 `editorValue` 복원 |

---

## `handleShortcut`

```kotlin
private fun handleShortcut(e: KeyEvent): Boolean
```

윈도우 레벨 단축키 핸들러. 이미 검색 바가 포커스 가져갈 때만 처리해야 하는 키는 `if (search != null) false` 로 흘려보낸다

| 키 | 동작 |
|---|---|
| `Ctrl+O` | `openFile` |
| `Ctrl+Shift+O` | `openFolder` |
| `Ctrl+S` | `saveFile` |
| `Ctrl+W` | `closeActiveTab` |
| `Ctrl+F` | `openSearch` |
| `Ctrl+R` | `openReplace` |
| `Ctrl+Z` | 검색 바 켜져 있으면 패스, 아니면 `doUndo` |
| `Ctrl+Shift+Z` / `Ctrl+Y` | `doRedo` |
| `Esc` | `closeSearch` (검색 바 켜져 있을 때만) |

---

## `windowTitle`

```kotlin
private fun windowTitle(path: Path?): String =
    "${path?.fileName ?: "Untitled"} — ${PageIdentity.NAME}"
```

활성 탭 파일명 + ` — PAGE` — 탭 없으면 `Untitled`. `PageIdentity.NAME` 을 직접 박지 않고 한 군데에서 가져온다

---

## 비공개 컴포저블

| 이름 | 역할 |
|---|---|
| `Shell` | 사이드바 + 본문 + 상태바를 묶는 가로 분할 컨테이너 |
| `TitleBar` | undecorated 윈도우 상단 — 드래그 영역 + 메뉴 버튼 |
| `ResizeHandle` | 사이드바 폭 조절 핸들 (드래그) |

---

## 사용처

이 파일이 `page:app` 의 진입점. `mainClassName = "page.app.MainKt"` 로 패키징됨

---

- [목차로 돌아가기](https://monkshark.github.io/page-ide/#README_kr.md)
