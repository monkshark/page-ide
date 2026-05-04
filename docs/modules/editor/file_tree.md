# FileTree / TreeNode

> `page/editor/src/main/kotlin/page/editor/FileTree.kt` — 사이드바 파일 트리 평탄화

`expanded` 집합을 받아 트리를 깊이-우선으로 펼친 평탄한 노드 리스트를 만든다. `LazyColumn` 으로 그리기 위한 데이터 표현

> English: [file_tree_en.md](https://monkshark.github.io/PAGE_IDE/#modules/editor/file_tree_en.md)

---

## `TreeNode`

```kotlin
data class TreeNode(
    val path: Path,
    val depth: Int,
    val isDirectory: Boolean,
)
```

평탄한 리스트의 한 행. `depth` 는 들여쓰기 레벨 (`0` = root)

---

## `listTree`

```kotlin
fun listTree(root: Path, expanded: Set<Path>): List<TreeNode>
```

`root` 자체를 첫 번째 노드로 추가하고, 펼쳐져 있으면 자식들을 재귀적으로 평탄화. 펼치지 않은 디렉터리는 헤더만 나오고 자식은 빠진다

| 정렬 | 우선순위 |
|---|---|
| 1차 | 디렉터리 우선 (`isDirectory` 가 true 인 것들이 먼저) |
| 2차 | 파일명 사전순 (소문자 비교) |

`Files.list` 가 `IOException` / 권한 오류로 실패하면 그 디렉터리만 빈 자식으로 처리 — 트리 전체가 죽지 않는다

---

## 안전 헬퍼

| 함수 | 동작 |
|---|---|
| `isDirectorySafe(path)` | `Files.isDirectory` 의 try/catch 버전. 예외 시 `false` |
| `listChildrenSorted(dir)` | `Files.list` + 정렬. 예외 시 `null` (자식 없음으로 처리) |

심볼릭 링크 / 끊어진 링크 / 권한 거부 폴더가 섞여 있어도 사이드바가 멈추지 않게 하기 위한 가드

---

## 사용처

| 위치 | 용도 |
|---|---|
| `page.app.FileTreePanel` | `listTree(root, expanded)` 결과를 `LazyColumn` 으로 렌더 |

---

- [목차로 돌아가기](https://monkshark.github.io/PAGE_IDE/#README.md)
