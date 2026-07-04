# FoldRegions

> `page/editor/src/main/kotlin/page/editor/FoldRegions.kt` — 코드 폴딩 영역 탐지 + 표시 변환 매핑

`{`/`}` 페어로 폴딩 가능한 블록을 찾고, 접힌 영역을 ` ... }` 플레이스홀더로 압축하기 위한 세그먼트 / 오프셋 매핑 / 클릭 히트테스트를 제공함

> English: [fold_regions_en.md](https://monkshark.github.io/page-ide/#modules/editor/fold_regions_en.md)

---

## 데이터

```kotlin
data class Region(val startLine: Int, val endLine: Int)
data class Segment(val origStart: Int, val origEnd: Int, val replacement: String)
```

| 타입 | 의미 |
|---|---|
| `Region` | 폴딩 후보 — 시작 줄 `{` 와 끝 줄 `}` 의 0-인덱스 줄 번호 |
| `Segment` | 표시 변환 단위 — 원본 텍스트의 `[origStart, origEnd)` 를 `replacement` 로 치환함 |

---

## `detect`

```kotlin
fun detect(text: String): List<Region>
```

설명: 본문에서 폴딩 가능한 모든 `{`/`}` 블록을 추출함

흐름:

1. `{` 만나면 현재 줄을 스택에 푸시:
   ```kotlin
   c == '{' -> { stack.addLast(line); i++ }
   ```

2. `}` 만나면 스택에서 매칭 줄을 꺼내고, 다른 줄에 있을 때만 `Region` 추가:
   ```kotlin
   val openLine = stack.removeLastOrNull()
   if (openLine != null && openLine < line) {
       regions.add(Region(openLine, line))
   }
   ```
   같은 줄 (`{ ... }` 한 줄 블록) 은 무시 — 접을 의미 없음

3. 스캔 중 다음 컨텍스트 안의 괄호는 무시:
   - 문자열 리터럴 (`"..."`, `'...'`) — 백슬래시 이스케이프 처리
   - 라인 코멘트 (`// ...`)
   - 블록 코멘트 (`/* ... */`) — 줄바꿈 카운트 유지

반환값: `startLine` 오름차순 정렬된 `List<Region>`. 매칭 안 된 괄호는 결과에서 빠짐

---

## `segmentsFor`

```kotlin
fun segmentsFor(text: String, foldedRegions: Collection<Region>): List<Segment>
```

설명: 접힌 region 들을 `VisualTransformation` 적용용 세그먼트로 변환함

흐름:

1. 줄 시작 오프셋 배열을 빌드:
   ```kotlin
   val lineStarts = mutableListOf(0)
   for (i in text.indices) if (text[i] == '\n') lineStarts.add(i + 1)
   ```

2. 각 region 별로 절단 범위 계산:
   - `origStart` = 시작 줄 끝의 `\n` 위치 (또는 `text.length`)
   - `origEnd` = 끝 줄 다음의 `\n` 직후 (또는 `text.length`) — 끝 줄 통째로 흡수

3. 플레이스홀더 결정:
   ```kotlin
   val replacement = if (hasTrailingNewline) " ... }\n" else " ... }"
   ```
   닫는 `}` 가 들어가서 시작 줄의 `{` 와 합쳐 `{ ... }` 모양으로 보임

4. 중첩된 fold 는 가장 바깥만 살림 — 안쪽이 이미 바깥에 흡수되어 있으므로 자연스럽게 합쳐짐:
   ```kotlin
   if (origStart < lastConsumedEnd) continue
   ```

반환값: 겹치지 않고 `origStart` 오름차순 정렬된 `List<Segment>`

---

## `originalToTransformed` / `transformedToOriginal`

```kotlin
fun originalToTransformed(segments: List<Segment>, original: Int): Int
fun transformedToOriginal(segments: List<Segment>, transformed: Int): Int
```

설명: `OffsetMapping` 양방향 변환 — Compose `VisualTransformation` 의 캐럿 매핑에 그대로 꽂힘

| 위치 | 매핑 결과 |
|---|---|
| 폴드 전 | 누적 절약 (`savings`) 만큼 빼서 변환 |
| 폴드 안 (원본) | 폴드 시작 (`origStart`) 으로 클램프 |
| 플레이스홀더 왼쪽 절반 (변환) | `origStart` 으로 매핑 (`{` 직후, 폴드 시작 좌표) |
| 플레이스홀더 오른쪽 절반 (변환) | `origEnd` 으로 매핑 (`}` 직후, 폴드 끝 좌표) |
| 폴드 후 | 누적 `savings` 빼고 다음 검사 |

플레이스홀더 안 좌표를 절반 기준으로 양 끝에 묶어 두면, 본문 `}` 클릭 시 캐럿이 폴드 다음 줄로 가서 의도치 않은 브래킷 매칭이 안 잡히고 드래그 선택도 자연스럽게 폴드 전체를 덮음

`savings` = `(origEnd - origStart) - replacement.length` 의 누적 합

---

## `foldedRegionAt`

```kotlin
fun foldedRegionAt(
    text: String,
    foldedRegions: Collection<Region>,
    transformedOffset: Int,
): Region?
```

설명: 표시 좌표가 플레이스홀더의 `...` 위에 있을 때 그 region 을 돌려줌 — `...` 클릭으로만 펼치고, `{`/`}`/주변 공백은 미스 (드래그 선택용 여유)

흐름:

1. region 들을 segments 로 변환 후, transformed 좌표가 어떤 segment 의 `...` 구간 `[dotsStart, dotsStart+3)` 에 들어가는지 검사
2. 매칭된 segment 의 `origStart` 가 `lineEnd(r.startLine)` 과 같은 region 을 sorted 순서로 첫 번째 발견 (= 가장 바깥)

반환값: 클릭이 `...` 위면 해당 `Region`, 그 외 (`{`, 공백, `}`, 플레이스홀더 바깥) 는 `null`

---

## 사용처

| 위치 | 용도 |
|---|---|
| `page.app.EditorPanel` | `detect` → 거터 토글 표시 / `segmentsFor` + 매핑 함수 → `CombinedHighlightTransformation` 폴딩 적용 / `foldedRegionAt` → 본문 플레이스홀더 클릭 펼치기 |

---

- [목차로 돌아가기](https://monkshark.github.io/page-ide/#README_kr.md)
