# LineMove

> `page/editor/src/main/kotlin/page/editor/LineMove.kt` — 라인 이동 / 복제

Alt+↑ / Alt+↓ 와 (향후) Shift+Alt+↑·↓ 의 핵심 로직. 선택 범위 전체를 하나의 블록 으로 다루어 위/아래 라인과 자리를 바꾼다

> English: [line_move_en.md](https://monkshark.github.io/page-ide/#modules/editor/line_move_en.md)

---

## `moveUp` / `moveDown`

```kotlin
fun moveUp(edit: TextEdit): TextEdit?
fun moveDown(edit: TextEdit): TextEdit?
```

선택된 줄 블록 전체와 직전/직후 한 줄을 통째로 swap. 선택 범위는 같이 따라간다 (드래그 중인 코드를 그대로 옮기는 느낌)

| 경계 케이스 | 반환 |
|---|---|
| `moveUp` 인데 첫 줄이 이미 0 행 | `null` (호출자가 무시) |
| `moveDown` 인데 마지막 줄이 EOF | `null` |

---

## `duplicateUp` / `duplicateDown`

```kotlin
fun duplicateUp(edit: TextEdit): TextEdit
fun duplicateDown(edit: TextEdit): TextEdit
```

선택된 줄 블록을 위/아래로 복제

| 함수 | 동작 |
|---|---|
| `duplicateUp` | 사본을 위 에 두고, 캐럿/선택은 아래쪽 (원본) 에 머무름 |
| `duplicateDown` | 사본을 아래 에 두고, 캐럿/선택은 아래쪽 (사본) 으로 이동 |

블록 길이를 알기 위해 `lastEnd - firstStart + 1` (줄바꿈 한 글자 포함) 을 더해 캐럿을 보정

---

## 라인 경계

```kotlin
private fun lineStart(text: String, offset: Int): Int
private fun lineEnd(text: String, offset: Int): Int
```

`\n` 만 보고 라인 경계를 잡는다. CRLF 환경에서도 `\r` 은 라인 끝의 일부로 같이 swap 됨 (별도 처리 안 함)

---

## 사용처

| 위치 | 용도 |
|---|---|
| `page.app.EditorPanel` `onPreviewKeyEvent` | Alt+Up → `moveUp`, Alt+Down → `moveDown` |

향후 Shift+Alt+Up / Down 단축키로 `duplicateUp` / `duplicateDown` 와이어링 예정

---

- [목차로 돌아가기](https://monkshark.github.io/page-ide/#README_kr.md)
