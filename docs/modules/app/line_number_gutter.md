# LineNumberGutter

> `page/app/src/main/kotlin/page/app/LineNumberGutter.kt` — 에디터 좌측 라인 번호 + 폴딩 토글

`EditorPanel` 본문 옆에 붙는 거터. 현재 줄만 진하게, 나머지는 흐리게. 폴딩 가능 줄에는 ▾/▸ 토글이 같이 표시됨

> English: [line_number_gutter_en.md](https://monkshark.github.io/page-ide/#modules/app/line_number_gutter_en.md)

---

## 시그니처

```kotlin
@Composable
internal fun LineNumberGutter(
    lines: List<GutterLine>,
    currentOriginalLine: Int,
    onToggleFold: (Int) -> Unit,
    textStyle: TextStyle,
    modifier: Modifier = Modifier,
)

internal data class GutterLine(
    val originalLine: Int,
    val foldable: Boolean,
    val folded: Boolean,
)
```

| 파라미터 | 의미 |
|---|---|
| `lines` | 실제로 그릴 행 — 폴딩으로 숨겨진 줄은 호출자가 미리 솎아서 넘김 |
| `currentOriginalLine` | 캐럿 줄 (원본 텍스트 기준) — 매칭 행만 진하게 |
| `onToggleFold(originalLine)` | 토글 클릭 콜백 — 인자는 0-인덱스 원본 줄 번호 |
| `textStyle` | 본문과 동일한 폰트/줄 높이 — 행 정렬을 위해 본문 스타일 그대로 받음 |

---

## 색

| 행 상태 | 색 |
|---|---|
| `originalLine == currentOriginalLine` | `colorScheme.onBackground` (강조) |
| 그 외 | `colorScheme.onSurfaceVariant` (흐림) |
| 토글 (접힌 상태) | `colorScheme.primary` 살짝 밝게 — 접혀 있다는 시그널 |
| 토글 (펼친 상태 / 미사용) | `onSurfaceVariant` |

---

## 행 구조

각 행은 `Row { FoldToggle; Text(번호) }`. `FoldToggle` 은 14dp 폭의 클릭 가능한 `Box` — 폴딩 가능 줄에는 ▾ (펼침) / ▸ (접힘), 그 외에는 공백이 들어가 폭이 일정하게 유지됨

---

## 정렬

`fillMaxWidth()` + `TextAlign.End` — 자릿수가 늘어도 우측 정렬 유지. 내부적으로 `IntrinsicSize.Max` 폭을 잡아 가장 큰 번호 (`lineCount`) 기준으로 거터 폭이 자동 결정됨

상하 16dp 패딩은 `EditorPanel` 본문의 첫 줄 위 여백과 정확히 맞춰져 본문/거터 줄이 정렬됨

---

## 폴딩 행 솎기

호출자 (`EditorPanel`) 가 `hiddenLines = 각 fold 의 (startLine+1..endLine)` 을 계산하고 그 외의 줄만 `lines` 로 넘김. 거터 행 수가 본문에서 보이는 행 수와 정확히 일치 — 폴딩된 영역은 시작 줄 한 행으로 압축됨

---

## 사용처

| 위치 | 용도 |
|---|---|
| `page.app.EditorPanel` | `Row { LineNumberGutter(...); editorBody }` 좌측 컬럼 |

---

- [목차로 돌아가기](https://monkshark.github.io/page-ide/#README_kr.md)
