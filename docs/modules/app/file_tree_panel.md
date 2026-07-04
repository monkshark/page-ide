# FileTreePanel

> `page/app/src/main/kotlin/page/app/FileTreePanel.kt` — 좌측 사이드바 (파일 트리)

`FileTree.listTree(root, expanded)` 결과를 `LazyColumn` 으로 렌더. 디렉터리 chevron 토글, 파일 클릭으로 탭 열기

> English: [file_tree_panel_en.md](https://monkshark.github.io/page-ide/#modules/app/file_tree_panel_en.md)

---

## 시그니처

```kotlin
@Composable
fun FileTreePanel(
    root: Path?,
    expanded: Set<Path>,
    activePath: Path?,
    onToggle: (Path) -> Unit,
    onOpen: (Path) -> Unit,
    modifier: Modifier = Modifier,
)
```

| 파라미터 | 의미 |
|---|---|
| `root` | 프로젝트 루트 — `null` 이면 빈 상태 힌트 표시 |
| `expanded` | 현재 펼쳐진 디렉터리 집합 (위쪽에서 관리) |
| `activePath` | 활성 탭 경로 — 같은 행을 selected 톤으로 |
| `onToggle(path)` | 디렉터리 클릭 → `expanded` 토글 |
| `onOpen(path)` | 파일 클릭 → `openOrFocus` |

---

## 상수

```kotlin
private val RowHeight = 22.dp
private val ChevronWidth = 14.dp
private val IndentStep = 14.dp
private val EdgePadding = 8.dp
```

`IndentStep` 은 깊이 1 단계의 들여쓰기. chevron 영역은 깊이가 들어가도 고정 폭 — 파일/디렉터리 아이콘이 흔들리지 않게

---

## 렌더 흐름

```kotlin
Surface(surfaceVariant) {
    Column {
        SectionHeader   // "PROJECT"
        HorizontalDivider
        if (root == null) EmptyTreeHint
        else LazyColumn {
            items(nodes, key = { it.path.toString() }) { TreeRow(...) }
        }
    }
}
```

`nodes = remember(root, expanded) { FileTree.listTree(root, expanded) }` — `root` / `expanded` 가 변할 때만 재계산

`key` 가 path string 이라 동일 경로 노드는 재사용 → 펼침/접힘 시 깜빡임 없음

---

## `TreeRow`

```kotlin
@Composable
private fun TreeRow(
    node: FileTreeNode,
    isActive: Boolean,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
)
```

| 상태 | 배경 |
|---|---|
| `isActive` (활성 탭과 같은 파일) | `primary 16%` |
| hover | `onSurface 6%` |
| 그 외 | 투명 |

| chevron | |
|---|---|
| 디렉터리 + 펼침 | `▾` |
| 디렉터리 + 접힘 | `▸` |
| 파일 | (chevron 폭만큼 빈칸 → 정렬 유지) |

텍스트는 12sp, 디렉터리는 `Medium` / 파일은 `Normal`. `maxLines = 1` + `Ellipsis` — 긴 이름은 `…` 로

---

## `EmptyTreeHint`

```kotlin
"No folder open"
"Press Ctrl+Shift+O"
```

`root == null` 일 때 두 줄 안내. 사이드바를 닫는 단축키와 폴더 여는 단축키를 같은 자리에서 노출 → 진입점 명확

---

## `SectionHeader`

상단 "PROJECT" 라벨. `10sp`, `FontWeight.Medium`, `letterSpacing = 0.8sp` — 작은 캡션 톤

---

## 사용처

| 위치 | 용도 |
|---|---|
| `page.app.Main` 좌측 (`sidebarWidth = 260.dp`) | 트리 + 토글/오픈 콜백 |

---

- [목차로 돌아가기](https://monkshark.github.io/page-ide/#README_kr.md)
