# LineNumberGutter

> `page/app/src/main/kotlin/page/app/LineNumberGutter.kt` — 에디터 좌측 라인 번호

`EditorPanel` 본문 옆에 붙는 거터. 현재 줄만 진하게, 나머지는 흐리게

> English: [line_number_gutter_en.md](https://monkshark.github.io/PAGE_IDE/#modules/app/line_number_gutter_en.md)

---

## 시그니처

```kotlin
@Composable
internal fun LineNumberGutter(
    lineCount: Int,
    currentLine: Int,
    textStyle: TextStyle,
    modifier: Modifier = Modifier,
)
```

| 파라미터 | 의미 |
|---|---|
| `lineCount` | 표시할 줄 수 (`TextBuffer.lineCount`) |
| `currentLine` | 캐럿이 있는 줄 (`TextBuffer.lineColOf(caret).line`) — 강조용 |
| `textStyle` | 본문과 동일한 폰트/줄 높이 — 거터 행이 본문 행과 한 픽셀씩 어긋나지 않게 본문 스타일 그대로 받는다 |

---

## 색

| 줄 | 색 |
|---|---|
| `line == currentLine` | `colorScheme.onBackground` (강조) |
| 그 외 | `colorScheme.onSurfaceVariant` (흐림) |

---

## 정렬

`fillMaxWidth()` + `TextAlign.End` — 자릿수가 늘어도 우측 정렬 그대로 유지. 내부적으로 `IntrinsicSize.Max` 폭을 잡으므로 가장 큰 번호 (`lineCount`) 기준으로 거터 폭이 자동 결정된다

상하 16dp 패딩은 `EditorPanel` 본문의 첫 줄 위 여백과 정확히 맞아야 본문/거터 줄이 정렬됨

---

## 비목표

- 줄 번호 클릭 → 해당 줄로 이동 — 현재 미구현, 캐럿 이동은 본문 클릭으로
- 폴딩 / 브레이크포인트 표시 — 거터를 별도 컬럼으로 키울 때 추가

---

## 사용처

| 위치 | 용도 |
|---|---|
| `page.app.EditorPanel` | `Row { LineNumberGutter(...); editorBody }` 좌측 컬럼 |

---

- [목차로 돌아가기](https://monkshark.github.io/PAGE_IDE/#README.md)
