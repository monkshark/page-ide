# TabBook / OpenTab

> `page/editor/src/main/kotlin/page/editor/TabBook.kt` — 탭 묶음과 활성 인덱스

이뮤터블 자료구조. 탭마다 본문 / 저장된 본문 / 캐럿 / 히스토리를 따로 들고 있어 탭 간 상태가 섞이지 않는다

> English: [tab_book_en.md](https://monkshark.github.io/PAGE_IDE/#modules/editor/tab_book_en.md)

---

## `OpenTab`

```kotlin
data class OpenTab(
    val path: Path,
    val text: String,
    val savedText: String = text,
    val caret: Int = 0,
    val history: EditHistory = EditHistory(),
) {
    val dirty: Boolean get() = text != savedText
}
```

| 필드 | 의미 |
|---|---|
| `path` | 파일 경로 (탭 식별자 역할) |
| `text` | 현재 본문 |
| `savedText` | 마지막 저장 시점의 본문 |
| `caret` | 마지막 캐럿 위치 (탭 전환 시 복원) |
| `history` | 이 탭만의 undo/redo 스택 |

`dirty` 는 *현재 본문 ≠ 저장 본문* 일 때 `true`. `Ctrl+W` 누를 때 다이얼로그 띄울지 결정하는 핵심

---

## `TabBook`

```kotlin
data class TabBook(
    val tabs: List<OpenTab> = emptyList(),
    val activeIndex: Int = -1,
)
```

전체 탭 리스트와 활성 인덱스. `activeIndex == -1` 이면 활성 탭 없음 (탭이 0 개)

---

## `openOrFocus`

```kotlin
fun openOrFocus(path: Path, text: String): TabBook
```

같은 경로의 탭이 이미 있으면 활성화만, 없으면 새 탭 추가하고 마지막을 활성화. 같은 파일을 두 번 열어도 탭이 늘어나지 않게

---

## `close` / `closeActive`

```kotlin
fun close(index: Int): TabBook
fun closeActive(): TabBook
```

탭 제거 후 `activeIndex` 보정

| 닫는 위치 | 새 활성 인덱스 |
|---|---|
| `index < activeIndex` | `activeIndex - 1` |
| `index == activeIndex` | `index.coerceAtMost(lastIndex)` (다음 탭 또는 마지막) |
| `index > activeIndex` | 그대로 |

마지막 탭을 닫으면 `TabBook()` (빈 상태) 반환

---

## `updateActive`

```kotlin
fun updateActive(text: String, caret: Int): TabBook
```

활성 탭의 `text` / `caret` 갱신. 같은 값이면 자기 자신 반환 (불필요한 리컴포지션 회피)

---

## `pushHistoryOnActive` / `undoOnActive` / `redoOnActive`

```kotlin
fun pushHistoryOnActive(prev: EditSnapshot): TabBook
fun undoOnActive(current: EditSnapshot): Pair<TabBook, EditSnapshot>?
fun redoOnActive(current: EditSnapshot): Pair<TabBook, EditSnapshot>?
```

`EditHistory` 호출을 활성 탭에만 적용하고 결과를 다시 끼워넣는 래퍼. undo/redo 가 성공하면 `(새 TabBook, 복원할 스냅샷)` 반환

---

## `markActiveSaved`

```kotlin
fun markActiveSaved(): TabBook
```

`savedText = text` 로 갱신해 `dirty` 를 `false` 로 만든다. `Ctrl+S` 후 호출

---

## `move`

```kotlin
fun move(from: Int, to: Int): TabBook
```

탭 재배치 (드래그 앤 드롭). `activeIndex` 도 따라 보정

| 이동 케이스 | 새 활성 인덱스 |
|---|---|
| 활성 탭이 옮겨지는 경우 | `to` |
| 옮긴 범위 안에 활성 탭이 끼임 | 한 칸 밀려남 |
| 그 외 | 그대로 |

---

## 사용처

| 위치 | 용도 |
|---|---|
| `page.app.Main` `book: TabBook` | 윈도우 상태 |
| `page.app.TabBar` | `book.tabs` / `activeIndex` 로 탭 칩 렌더 |
| `Ctrl+O` / `Ctrl+W` / `Ctrl+S` 핸들러 | `openOrFocus` / `closeActive` / `markActiveSaved` |

---

- [목차로 돌아가기](https://monkshark.github.io/PAGE_IDE/#README.md)
