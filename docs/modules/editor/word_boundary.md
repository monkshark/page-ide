# WordBoundary

> `page/editor/src/main/kotlin/page/editor/WordBoundary.kt` — 워드 단위 캐럿 이동 / 삭제

`Ctrl+←/→` (이동), `Ctrl+Shift+←/→` (선택), `Ctrl+Backspace/Delete` (삭제) 의 경계 계산. VS Code 식 분류 — 단어 (영숫자 + `_`) / 구두점 / 가로 공백 / 개행

> English: [word_boundary_en.md](https://monkshark.github.io/PAGE_IDE/#modules/editor/word_boundary_en.md)

---

## `nextBoundary`

```kotlin
fun nextBoundary(text: String, offset: Int): Int
```

`offset` 에서 오른쪽으로 다음 워드 경계까지의 위치 반환

| 상황 | 동작 |
|---|---|
| 가로 공백 (` `, `\t`) | 먼저 모두 건너뛴다 |
| 그다음 글자가 `\n` | 캐럿이 `\n` 바로 위면 한 칸 (개행 한 번에 통과). 공백을 건너온 결과면 `\n` 직전에 멈춤 |
| 그다음 글자가 단어/구두점 | 같은 클래스가 이어지는 동안 진행 |

---

## `prevBoundary`

```kotlin
fun prevBoundary(text: String, offset: Int): Int
```

대칭. 가로 공백을 먼저 뒤로 건너뛰고, 그다음 클래스를 한 덩어리 묶는다

---

## `deleteWordBackward` / `deleteWordForward`

```kotlin
fun deleteWordBackward(edit: TextEdit): TextEdit?
fun deleteWordForward(edit: TextEdit): TextEdit?
```

단일 캐럿 (`selectionStart == selectionEnd`) 일 때만 동작. 선택 영역이 있으면 `null` → 호출자가 기본 Backspace/Delete 동작 (선택 삭제) 으로 폴백

| 입력 | 결과 |
|---|---|
| `hello world|` + `Ctrl+Backspace` | `hello |` |
| `|hello world` + `Ctrl+Delete` | `| world` |

`Ctrl+Backspace` 가 트레일링 공백까지 한꺼번에 먹는 건 의도 — VS Code/IntelliJ 와 같은 동작

---

## `CharClass`

```kotlin
private enum class CharClass { WORD, PUNCT }
```

내부 분류. 가로 공백과 개행은 별도 단계로 처리하므로 enum 에 포함 안 함

| 클래스 | 글자 |
|---|---|
| `WORD` | `Char.isLetterOrDigit()` 또는 `_` (camelCase 식별자가 한 덩어리로 잡히게) |
| `PUNCT` | 그 외 |

---

## 사용처

| 위치 | 키 |
|---|---|
| `page.app.EditorPanel` `handleWordShortcut` | `Ctrl+←/→`, `Ctrl+Shift+←/→`, `Ctrl+Backspace`, `Ctrl+Delete` |

---

- [목차로 돌아가기](https://monkshark.github.io/PAGE_IDE/#README.md)
