# TextBuffer / LineCol

> `page/editor/src/main/kotlin/page/editor/TextBuffer.kt` — `StringBuilder` 래퍼 + 라인/컬럼 좌표

`offset` ↔ `(line, col)` 변환과 라인 단위 접근을 제공. 현재 `EditorPanel` 의 라인 번호 거터·상태바·현재 줄 하이라이트가 같은 인덱스를 본다

> English: [text_buffer_en.md](https://monkshark.github.io/page-ide/#modules/editor/text_buffer_en.md)

---

## `LineCol`

```kotlin
data class LineCol(val line: Int, val col: Int)
```

행/열 좌표. 둘 다 0-기반 (`(0, 0)` = 첫 줄 첫 글자)

---

## 생성

```kotlin
class TextBuffer(initial: String = "")
```

`StringBuilder` 를 내부에 보유. 빈 버퍼 (`length = 0`) 도 `lineCount = 1` (빈 한 줄)

---

## 메서드

| 메서드 | 동작 |
|---|---|
| `length` / `lineCount` / `text()` | 길이, 줄 수, 전체 문자열 스냅샷 |
| `lineAt(index)` | 해당 줄의 텍스트 (개행 미포함). 범위 밖이면 `IllegalArgumentException` |
| `insert(offset, text)` | 위치에 삽입 |
| `delete(start, end)` | 범위 삭제 (`end` 는 exclusive) |
| `insertAt(line, col, text)` | 라인/컬럼 좌표로 삽입 (내부에서 `offsetOf` 변환) |
| `deleteAt(startLine, startCol, endLine, endCol)` | 라인/컬럼 좌표로 삭제 |
| `offsetOf(line, col)` | 좌표 → offset |
| `lineColOf(offset)` | offset → 좌표 |

모든 변경 메서드는 `require` 로 경계 검사 — 잘못된 인자는 즉시 예외, 호출자 버그를 빨리 잡자

---

## 라인 좌표 계산

```kotlin
private fun lineStartOffset(line: Int): Int
private fun lineEndOffset(line: Int): Int
```

`\n` 을 매번 처음부터 카운트. 큰 파일에선 O(N) 비용이라 호출 빈도가 높으면 라인 인덱스 캐시가 필요 — 현재는 `EditorPanel` 이 `remember(value.text)` 로 한 번만 만들고 공유하므로 충분

---

## 비목표

- 증분 갱신 / rope / piece table — `BasicTextField` 가 `String` 을 그대로 다뤄 호환성 우선
- 멀티 캐럿 / 멀티 셀렉션 좌표 — 현재 `TextEdit` 한 쌍의 selection 만 다룸

---

## 사용처

| 위치 | 용도 |
|---|---|
| `page.app.EditorPanel` | `remember(value.text) { TextBuffer(...) }` 로 라인 인덱스 한 번만 만들고 공유 |
| `LineNumberGutter` | `lineCount` 로 거터 줄 수 |
| `EditorStatusBar` | `lineColOf(caret)` 로 현재 행/열 표시 |

---

- [목차로 돌아가기](https://monkshark.github.io/page-ide/#README_kr.md)
