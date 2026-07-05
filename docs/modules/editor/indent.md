# Indent

> `page/editor/src/main/kotlin/page/editor/Indent.kt` — 들여쓰기 / 내어쓰기 / Enter 자동 들여쓰기

Tab / Shift+Tab / Enter / Backspace 키 동작과 `}` / `]` / `)` 자동 정렬을 한 곳에 모은 순수 함수 묶음

> English: [indent_en.md](https://monkshark.github.io/page-ide/#modules/editor/indent_en.md)

---

## 상수

| 상수 | 값 | 설명 |
|---|---|---|
| `TAB_UNIT` | `4` | 한 단위 들여쓰기 = 4 칸 공백 |
| `TAB_SPACES` | `"    "` | 4 칸 공백 리터럴 |
| `indentTriggers` | `{`, `(`, `[`, `:` | Enter 직전 글자가 이 중 하나면 한 단계 더 들여씀 |
| `matchingClosers` | `{→}`, `(→)`, `[→]` | Enter 시 짝이 캐럿 양쪽에 있으면 분할 들여쓰기 |
| `unindentChars` | `}`, `]`, `)` | 입력 시 라인의 들여쓰기 한 단계 빼기 |

---

## `handleTab`

```kotlin
fun handleTab(edit: TextEdit): TextEdit
```

| 입력 상태 | 동작 |
|---|---|
| 캐럿만 있음 | 다음 4 칸 경계까지 공백 삽입 (`col % 4` 만큼) |
| 한 줄 선택 | 줄 시작에 `TAB_SPACES` 한 단위 추가 (선택 그대로 유지) |
| 여러 줄 선택 | 모든 줄 시작에 `TAB_SPACES` 추가 |

---

## `handleShiftTab`

```kotlin
fun handleShiftTab(edit: TextEdit): TextEdit
```

선택된 모든 줄의 들여쓰기를 한 단계 (최대 `TAB_UNIT` 칸) 줄임. 줄에 공백이 부족하면 가능한 만큼만 제거

---

## `handleLiteralTab`

```kotlin
fun handleLiteralTab(edit: TextEdit): TextEdit
```

`\t` 문자 그대로 삽입. 사용자가 의도적으로 탭 문자를 원하는 경우 (Makefile, TSV)

---

## `handleBackspace`

```kotlin
fun handleBackspace(edit: TextEdit): TextEdit?
```

캐럿 앞이 공백뿐인 들여쓰기 일 때만 동작. 4 칸 경계까지 한 번에 지운다 (한 칸씩 지우는 대신). 그 외 (선택 있음 / 라인 첫 위치 / 들여쓰기에 비공백 글자) 는 `null` 반환 → 호출자가 기본 백스페이스로 폴백

---

## `maybeApplyEnter`

```kotlin
fun maybeApplyEnter(old: TextEdit, new: TextEdit): TextEdit
```

`onValueChange` 단계에서 호출. `new` 가 `old` 위치에 정확히 `\n` 한 글자 삽입한 결과인지 확인하고, 맞으면 `handleEnter` 로 넘긴다 — IME / 한글 조합 / 붙여넣기 같은 케이스에서 오작동 안 하게 보호하는 가드

---

## `handleEnter`

```kotlin
fun handleEnter(edit: TextEdit): TextEdit
```

Enter 키의 핵심 로직

| 케이스 | 동작 |
|---|---|
| 캐럿 양쪽이 짝 (`{|}`, `[|]`, `(|)`) | 두 줄 분할 들여쓰기 — 가운데 줄은 한 단계 더 들여쓰고 닫는 짝은 새 줄로 |
| 직전 글자가 `indentTriggers` 중 하나 | 다음 줄을 한 단계 더 들여씀 |
| 그 외 | 직전 줄의 선행 공백을 그대로 복사 |

---

## `maybeUnindentClosingBrace`

```kotlin
fun maybeUnindentClosingBrace(old: TextEdit, new: TextEdit): TextEdit
```

`}` `]` `)` 를 입력한 직후 라인 전체가 공백 + 입력 글자 한 개로만 구성돼 있으면, 들여쓰기를 한 단계 (`TAB_UNIT`) 줄여 닫는 짝을 여는 짝과 같은 들여쓰기로 정렬

---

## 사용처

| 위치 | 용도 |
|---|---|
| `page.app.EditorPanel` `onValueChange` | `maybeUnindentClosingBrace`, `maybeApplyEnter` 통과 |
| `page.app.EditorPanel` `onPreviewKeyEvent` | Tab → `handleTab`, Shift+Tab → `handleShiftTab`, Backspace → `handleBackspace` |

---

- [목차로 돌아가기](https://monkshark.github.io/page-ide/#README_kr.md)
