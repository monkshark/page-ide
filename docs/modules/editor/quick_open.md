# QuickOpen

> `page/editor/src/main/kotlin/page/editor/QuickOpen.kt` — 빠른 열기 결과 랭킹

`FuzzyMatcher` 단일 매칭을 파일 리스트 위에서 돌려서 결과 정렬 + 자르기. 다이얼로그는 이게 돌려준 `QuickOpenResult` 만 받아서 그린다

> English: [quick_open_en.md](https://monkshark.github.io/page-ide/#modules/editor/quick_open_en.md)

---

## `rank`

```kotlin
fun rank(query: String, files: List<IndexedFile>): List<QuickOpenResult>
```

| 상황 | 동작 |
|---|---|
| `query` 가 비거나 공백만 | 입력 순서대로 앞 50 개 (점수 0, 인덱스 빈 배열) |
| 파일 이름에도 경로에도 매치 안 됨 | 결과에서 제외 |
| 매치 1개 이상 | 점수 합산 → 내림차순 정렬 → 50 개로 자름 |

이름 매치 (파일명만) 가 가장 큰 가중치 (`+200`), 경로 매치는 보조 (`/4` 로 약화). 그래서 `editor` 라고 치면 `editor/` 폴더 내 모든 파일이 아니라 `Editor.kt` 류가 위로 올라온다

---

## `QuickOpenResult`

```kotlin
data class QuickOpenResult(
    val file: IndexedFile,
    val nameIndices: IntArray,
    val pathIndices: IntArray,
    val score: Int,
)
```

| 필드 | 용도 |
|---|---|
| `file` | 선택했을 때 열어야 할 대상 |
| `nameIndices` | 파일명 영역에서 어떤 글자를 강조할지 |
| `pathIndices` | 경로 영역 강조 (현재 UI 에선 미사용 — 부모 경로는 흐릿하게만 표시) |
| `score` | 정렬 키 |

`equals`/`hashCode` 는 `IntArray` 안의 값까지 비교하도록 직접 작성

---

## `nameOf` / `nameOffset`

```kotlin
fun nameOf(relative: String): String
fun nameOffset(relative: String): Int
```

`a/b/Foo.kt` → `Foo.kt` / `4`. 마지막 `/` 만 본다 — 이미 `walk` 에서 `\` 를 `/` 로 정규화해 두었으므로 plat-dep 없음

---

## 사용처

| 위치 | 역할 |
|---|---|
| `page.app.QuickOpenDialog` | `query` 가 바뀔 때마다 `rank(query, files)` 재계산 |

---

- [목차로 돌아가기](https://monkshark.github.io/page-ide/#README_kr.md)
