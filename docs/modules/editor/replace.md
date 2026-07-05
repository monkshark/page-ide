# Replace

> `page/editor/src/main/kotlin/page/editor/Replace.kt` — 검색 매치 치환

`SearchState.matches` 가 만들어 놓은 매치 범위들을 받아 텍스트를 치환한다. 매치를 찾는 일은 `SearchState`, 치환만 이 파일

> English: [replace_en.md](https://monkshark.github.io/page-ide/#modules/editor/replace_en.md)

---

## `Result`

```kotlin
data class Result(val text: String, val caret: Int, val replacedCount: Int)
```

치환 결과 묶음

| 필드 | 의미 |
|---|---|
| `text` | 치환된 새 텍스트 |
| `caret` | 치환 후 캐럿 위치 |
| `replacedCount` | 실제로 치환된 매치 개수 (0 도 가능) |

---

## `applyCurrent`

```kotlin
fun applyCurrent(text: String, range: IntRange, replacement: String): Result
```

매치 한 개 (`range`) 를 `replacement` 로 치환. 캐럿은 치환된 글자 끝 (`start + replacement.length`) — 다음 매치로 넘어가기 좋은 위치

`replacedCount` 는 항상 `1`

---

## `applyAll`

```kotlin
fun applyAll(text: String, matches: List<IntRange>, replacement: String): Result
```

매치 리스트 전체를 한 번에 치환. `StringBuilder` 로 매치 사이의 원본 조각을 그대로 이어붙여 단일 패스로 새 문자열을 만든다

`matches` 가 비어 있으면 원본 텍스트와 `replacedCount = 0` 반환. 캐럿은 `0` 으로 리셋 — 전부 치환 후 캐럿 위치는 의미가 모호해서 일단 단순화

`matches` 는 서로 겹치지 않고 오름차순 이라는 전제 — `SearchState.findAll` 이 이 조건을 만족시킨다

---

## 사용처

| 위치 | 용도 |
|---|---|
| `page.app.SearchBar` "바꾸기" | `applyCurrent(text, search.active, replace)` |
| `page.app.SearchBar` "전부 바꾸기" | `applyAll(text, search.matches, replace)` |

---

- [목차로 돌아가기](https://monkshark.github.io/page-ide/#README_kr.md)
