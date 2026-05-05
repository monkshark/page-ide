# EditorPanel

> `page/app/src/main/kotlin/page/app/EditorPanel.kt` — 에디터 본문

`BasicTextField` 한 개를 띄우고, 그 위에 라인 번호 거터 / 토큰 컬러 / 매치 하이라이트 / 브래킷 매치 / 현재 줄 배경 / 상태바를 얹는다

> English: [editor_panel_en.md](https://monkshark.github.io/PAGE_IDE/#modules/app/editor_panel_en.md)

---

## 시그니처

```kotlin
@Composable
fun EditorPanel(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    activePath: Path?,
    lexer: SyntaxLexer?,
    search: SearchState?,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    pushHistory: (EditSnapshot) -> Unit,
    modifier: Modifier = Modifier,
)
```

| 파라미터 | 의미 |
|---|---|
| `value` | 텍스트 + selection — Compose `TextFieldValue` 그대로 |
| `lexer` | `null` 이면 토큰 색칠 없음 (본문 톤만) |
| `search` | 검색 매치/active match 강조용 |
| `pushHistory(prev)` | 변경 전 스냅샷 푸시 — undo/redo 입력은 `Main` 의 `doUndo` / `doRedo` |

---

## TextBuffer & 토큰

```kotlin
val buffer = remember(value.text) { TextBuffer(value.text) }
val tokens = remember(value.text, lexer) { lexer?.tokenize(value.text).orEmpty() }
```

`buffer` 와 `tokens` 둘 다 `value.text` 기준으로 메모이즈 — 텍스트가 바뀔 때만 재계산. 라인 번호 거터, 상태바, 현재 줄 강조가 모두 같은 `buffer` 를 본다

---

## 브래킷 매치

```kotlin
val bracketMatch = remember(value.text, value.selection.start, value.selection.end) {
    if (value.selection.collapsed) BracketMatch.find(value.text, value.selection.start) else null
}
```

selection 이 collapsed (= 캐럿) 일 때만 매칭. 선택 중에는 `null` 로 두어 시각 노이즈 제거

---

## 현재 줄 배경

```kotlin
val currentLineBg = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.06f)
.drawBehind { ... }
```

`buffer.lineColOf(caret).line` 의 y 범위에 직사각형 한 줄. `drawBehind` 로 그려서 텍스트 셰이프 위에 색이 깔리지 않게

---

## 포커스 후 스크롤 보존

```kotlin
focusGainVersion ↑ 시 LaunchedEffect(focusGainVersion) {
    delay(250)
    scrollState.scrollTo(scrollState.value, MutatePriority.PreventUserInput)
}
```

탭 전환 직후 BasicTextField 가 캐럿을 화면 안으로 끌고 오는 기본 동작이 있다 — 250ms 안에 한 번 잠궈서 무시하게 만든다. `MutatePriority.PreventUserInput` 으로 사용자 스크롤만 풀어줌

---

## `onValueChange` 체인

```kotlin
val after = AutoClose.apply(value, next)
val unindented = Indent.maybeUnindentClosingBrace(after)
val final = Indent.maybeApplyEnter(unindented)
onValueChange(final)
```

타이핑 직후 세 단계로 후처리. `AutoClose` 가 짝괄호/따옴표, `Indent` 가 닫는 중괄호 정렬과 `Enter` 자동 들여쓰기

---

## 키 처리 (`onPreviewKeyEvent`)

| 키 | 동작 |
|---|---|
| `Alt+Up/Down` | `LineMove.moveUp/moveDown` |
| `Alt+Shift+Up/Down` | `duplicateUp/duplicateDown` |
| `Tab` | 마크다운 코드 펜스 안이면 `handleLiteralTab`, 아니면 들여쓰기 |
| `Shift+Tab` | `handleShiftTab` (역들여쓰기) |
| `Enter` | `handleEnter` (자동 들여쓰기) |
| `Backspace` | `handleBackspace` — `null` 폴백이면 기본 동작 |

`MarkdownFence.isInsideFence` 가 `Tab` 분기를 결정 — 코드 블록 안에서는 리터럴 탭이 들어가야 위지윅이 안 깨짐

---

## 마우스 클릭 (더블 / 트리플)

```kotlin
.pointerInput(Unit) {
    awaitPointerEventScope {
        ... PointerEventPass.Final 의 Press 이벤트만 본다
        clickCount = if (now - lastClickTime < 400 && close && clickCount < 3) clickCount + 1 else 1
    }
}
```

`onTextLayout` 으로 잡아둔 `TextLayoutResult.getOffsetForPosition` 으로 클릭 좌표를 텍스트 오프셋으로 변환

| 클릭 | 동작 |
|---|---|
| 더블 | `WordBoundary.wordRangeAt(text, offset)` → 같은 클래스 런 선택 (공백/개행 위에서는 무시) |
| 트리플 | `WordBoundary.lineRangeAt(text, offset)` → 줄 시작 ~ `\n` 직전 |

`PointerEventPass.Final` 이라 `BasicTextField` 가 자체 포인터 처리를 끝낸 뒤 우리가 덮어 쓴다. 400ms / 8px 안에서만 시퀀스로 인정 → 그 외엔 카운터 리셋

---

## `CombinedHighlightTransformation`

`VisualTransformation` 한 번에 세 종류 색을 칠한다:

1. 토큰 컬러 (`colorFor(kind)`, `PUNCT` 는 `null` → 본문 색 유지)
2. 매치 배경 — active match 와 일반 match 색 구분
3. 브래킷 매치 배경

`OffsetMapping.Identity` — 글자 수가 안 변하니 매핑 변환은 단위함수로 충분

---

## `EditorStatusBar`

하단 한 줄. `Ln {line+1}, Col {col+1}` · 줄 수 · 글자 수. `buffer.lineColOf(caret)` 결과를 직접 표시

---

## 폰트

| 항목 | 값 |
|---|---|
| 패밀리 | `EditorFontFamily` |
| 크기 | `14sp` |
| 줄 높이 | `20sp` |
| 줄 정렬 | `LineHeightStyle(Center, Trim.None)` |

`Trim.None` 이라 첫 줄/마지막 줄에도 같은 줄 높이 → 거터와 정확히 정렬

---

## 사용처

| 위치 | 용도 |
|---|---|
| `page.app.Main` 본문 영역 | 활성 탭의 텍스트/lexer/search 를 그대로 전달 |

---

- [목차로 돌아가기](https://monkshark.github.io/PAGE_IDE/#README.md)
