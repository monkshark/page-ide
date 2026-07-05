# EditHistory / EditSnapshot

> `page/editor/src/main/kotlin/page/editor/EditHistory.kt` — undo/redo 스택 (탭별 보유)

이뮤터블 자료구조. 각 탭의 `OpenTab.history` 가 별도 인스턴스를 들고 있어 탭 간 undo 가 섞이지 않는다

> English: [edit_history_en.md](https://monkshark.github.io/page-ide/#modules/editor/edit_history_en.md)

---

## `EditSnapshot`

```kotlin
data class EditSnapshot(val text: String, val caret: Int)
```

undo/redo 단위. 텍스트 전체와 캐럿 위치를 한 묶음으로 저장. 큰 파일에서도 문자열 공유 (JVM `String` 의 불변성) 로 메모리 부담은 작음

---

## `EditHistory`

```kotlin
data class EditHistory(
    val past: List<EditSnapshot> = emptyList(),
    val future: List<EditSnapshot> = emptyList(),
)
```

`past` 는 undo 스택, `future` 는 redo 스택. 새 편집이 들어오면 `future` 는 비워진다

| 상수 | 값 | 설명 |
|---|---|---|
| `MAX_SIZE` | `1000` | `past` 최대 길이. 넘치면 가장 오래된 스냅샷부터 제거 |

---

## `pushBeforeChange`

```kotlin
fun pushBeforeChange(prev: EditSnapshot, maxSize: Int = MAX_SIZE): EditHistory
```

새 편집이 발생하기 직전 의 상태를 `past` 에 쌓고 `future` 를 비운다. 같은 스냅샷 (`past.last() == prev`) 이 또 들어오면 무시 → 동일 상태가 두 번 쌓이지 않음

---

## `undo` / `redo`

```kotlin
fun undo(current: EditSnapshot): Pair<EditHistory, EditSnapshot>?
fun redo(current: EditSnapshot): Pair<EditHistory, EditSnapshot>?
```

`current` 는 현재 보이는 상태. `undo` 는 `past` 의 마지막을 꺼내 보여주고 현재를 `future` 로 보낸다. `redo` 는 반대. 각자 스택이 비어 있으면 `null`

반환은 `(새 EditHistory, 복원할 스냅샷)` — 호출자는 둘 다 받아서 탭 상태에 반영해야 한다.

실제 사용처 `TabBook.undoOnActive` 는 이 Pair 를 이렇게 소비한다.

```kotlin
val (newHistory, restored) = tab.history.undo(current) ?: return null
```

`restored` 로 활성 탭의 텍스트·캐럿을 바꾸고, `newHistory` 를 그 탭의 `history` 에 다시 끼워 넣는다.

---

## 사용처

| 위치 | 용도 |
|---|---|
| `OpenTab.history` | 탭 단위로 보유 |
| `TabBook.pushHistoryOnActive` | `onValueChange` 에서 직전 상태를 푸시 |
| `TabBook.undoOnActive` / `redoOnActive` | Ctrl+Z / Ctrl+Shift+Z (Ctrl+Y) 핸들러 |

---

- [목차로 돌아가기](https://monkshark.github.io/page-ide/#README_kr.md)
