# FuzzyMatcher

> `page/editor/src/main/kotlin/page/editor/FuzzyMatcher.kt` — 서브시퀀스 퍼지 매칭 + 점수

`Ctrl+P` 빠른 열기에서 파일명/경로 후보를 순위 매기는 데 쓰는 순수 함수. 쿼리의 모든 글자가 타겟에 같은 순서로 (연속일 필요는 없음) 등장하면 매치, 그렇지 않으면 `null`

> English: [fuzzy_matcher_en.md](https://monkshark.github.io/page-ide/#modules/editor/fuzzy_matcher_en.md)

---

## `match`

```kotlin
fun match(query: String, target: String): Match?
```

| 입력 | 결과 |
|---|---|
| `query` 가 비어 있음 | `Match(0, [])` |
| `target.length < query.length` | `null` |
| 서브시퀀스 매치 안 됨 | `null` |
| 매치 성공 | `Match(score, indices)` — `indices[i]` 는 `query[i]` 가 잡힌 `target` 위치 |

대소문자 무시 — 비교는 `lowercase()` 로, 보너스만 원본 글자 그대로 본다

---

## 점수 보너스

연속 매치 / 단어 경계 / 시작점 일치 → 합쳐져서 정렬 키가 된다

| 보너스 | 조건 |
|---|---|
| `+15 + streak·5` | 직전 매치 바로 다음 글자 (연속 스트릭) |
| `+30` | 단어 경계 시작 (`/`, `\`, `.`, `_`, `-`, ` `, camelCase 의 대문자 직전이 소문자) |
| `+20` | 타겟의 0 번째 인덱스 |
| `+5` | 원본 글자 그대로 일치 (대소문자까지) |
| `+1` | 매치마다 기본 |
| `-(타겟길이 - 쿼리길이)` | 길이 패널티 (짧은 타겟이 살짝 우선) |

이 조합은 IntelliJ / VS Code 의 "search anything" 류 휴리스틱 — 정확한 가중치가 중요한 게 아니라, 연속 매치가 흩어진 매치보다, 단어 경계 매치가 중간 매치보다 위로 올라오는 정성적 순위가 핵심

---

## `Match`

```kotlin
data class Match(val score: Int, val indices: IntArray)
```

`indices` 는 하이라이트용으로 그대로 쓴다 — `QuickOpenDialog` 가 `query` 글자 위치를 강조 색으로 그릴 때 인덱스 셋만 던져주면 됨

`equals`/`hashCode` 는 `IntArray` 의 `contentEquals` 로 직접 구현 — `data class` 기본은 참조 비교라 테스트가 깨짐

---

## 사용처

| 위치 | 역할 |
|---|---|
| `page.editor.QuickOpen.rank` | 파일 인덱스를 쿼리로 정렬 + 결과 자르기 |
| `page.app.QuickOpenDialog` | `Match.indices` 로 매치 글자 강조 |

---

- [목차로 돌아가기](https://monkshark.github.io/page-ide/#README_kr.md)
