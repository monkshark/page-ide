# SearchState

> `page/editor/src/main/kotlin/page/editor/SearchState.kt` — 검색 / 치환 상태

검색바의 모든 상태가 들어있는 이뮤터블 데이터 클래스. `null` 이면 검색바 닫힘, 인스턴스가 있으면 열림

> English: [search_state_en.md](https://monkshark.github.io/PAGE_IDE/#modules/editor/search_state_en.md)

---

## 필드

```kotlin
data class SearchState(
    val query: String = "",
    val replace: String = "",
    val replaceVisible: Boolean = false,
    val caseSensitive: Boolean = false,
    val matches: List<IntRange> = emptyList(),
    val activeMatchIndex: Int = -1,
)
```

| 필드 | 의미 |
|---|---|
| `query` | 검색어 |
| `replace` | 치환 텍스트 |
| `replaceVisible` | `true` 면 치환 입력칸과 버튼이 두 번째 줄에 표시 |
| `caseSensitive` | 대소문자 구분 토글 (`Aa` 칩) |
| `matches` | 본문에서 찾은 매치 범위 리스트 |
| `activeMatchIndex` | 현재 강조된 매치의 인덱스 (`matches.getOrNull` 으로 사용) |

---

## 파생 프로퍼티

```kotlin
val isActive: Boolean get() = query.isNotEmpty()
val active: IntRange? get() = matches.getOrNull(activeMatchIndex)
```

`isActive` 는 *질의가 들어있는 상태* (검색바가 열려 있어도 빈 검색어일 수 있음). `active` 는 활성 매치의 범위 — 없으면 `null`

---

## 변환 메서드 (with*)

| 메서드 | 동작 |
|---|---|
| `withQuery(text, query)` | 검색어 변경 → 새로 매치 찾고 활성 인덱스 0 으로 |
| `withReplace(value)` | 치환 텍스트만 갱신 |
| `withReplaceVisible(value)` | 치환 줄 표시/숨김 |
| `withCaseSensitive(text, value)` | 토글 후 동일 검색어로 다시 찾기 |
| `retarget(text)` | 본문이 바뀌었을 때 매치 다시 찾고, 직전 활성 위치에 가장 가까운 매치를 새 활성으로 |

`retarget` 가 핵심 — 사용자가 치환하거나 본문을 편집한 뒤에도 *현재 보던 매치 근처* 에 머무르게 하는 사용감을 만든다

---

## 이동 메서드

```kotlin
fun next(): SearchState
fun prev(): SearchState
```

`activeMatchIndex` 만 순환 (마지막 → 0, 0 → 마지막). 매치가 없으면 자기 자신 반환

---

## `findAll` (private)

```kotlin
private fun findAll(text: String, query: String, caseSensitive: Boolean): List<IntRange>
```

`String.regionMatches` 로 한 번씩 비교하며 *겹치지 않는* 매치만 수집 — 매치를 찾으면 인덱스를 `query.length` 만큼 건너뛴다 (예: `aaa` 에서 `aa` 검색 → `[0..1]` 한 개. `[0..1]`, `[1..2]` 가 아님)

대소문자는 `ignoreCase = !caseSensitive` 로 한 줄 처리. 정규식 미사용

---

## 비목표

- 정규식 검색 — 향후 추가 예정 (별도 토글)
- 단어 단위 (whole word) 검색 — 향후 토글
- 다중 라인 / 멀티커서 검색 — 미지원

---

## 사용처

| 위치 | 용도 |
|---|---|
| `page.app.Main` `search: SearchState?` | 윈도우 상태 |
| `page.app.SearchBar` | 모든 입력은 `SearchState` 변환으로 |
| `CombinedHighlightTransformation` | `matches` 와 `activeMatchIndex` 로 매치 / 활성 매치 배경 색 입힘 |

---

- [목차로 돌아가기](https://monkshark.github.io/PAGE_IDE/#README.md)
