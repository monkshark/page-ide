# BracketMatch

> `page/editor/src/main/kotlin/page/editor/BracketMatch.kt` — 캐럿 위치의 짝 괄호 찾기

여는 괄호면 오른쪽으로, 닫는 괄호면 왼쪽으로 깊이 카운트하며 짝을 찾는다. 본문 하이라이트 (괄호 매칭 배경) 의 데이터 소스

> English: [bracket_match_en.md](https://monkshark.github.io/page-ide/#modules/editor/bracket_match_en.md)

---

## `find`

```kotlin
fun find(text: String, caret: Int): Pair<Int, Int>?
```

캐럿 직전 글자를 먼저 검사, 매칭 실패 시 캐럿 직후 글자를 검사. 둘 다 짝이 아니면 `null`. 반환 페어는 `(여는 위치, 닫는 위치)` 순서로 정규화

| 캐럿 위치 글자 | 동작 |
|---|---|
| 여는 괄호 (`(` `[` `{`) | 오른쪽으로 같은 종류 깊이 +1, 짝 종류 깊이 −1 → 0 도달 시점 |
| 닫는 괄호 (`)` `]` `}`) | 왼쪽으로 동일 로직 |
| 그 외 | 다음 후보 (캐럿 직후) 시도 |

---

## 깊이 스캔

```kotlin
private fun scan(text: String, from: Int, same: Char, target: Char, dir: Int): Int?
```

`depth = 1` 으로 시작해 같은 글자에서 +1, 짝 글자에서 −1. 0 이 되면 그 인덱스 반환. 텍스트 끝까지 못 찾으면 `null`

문자열 / 주석 안의 괄호도 카운트한다 — 토큰 인지 없이 단순 문자 매칭. 작은 비용으로 충분히 정확한 결과를 내는 트레이드오프

---

## 짝 정의

```kotlin
private val openers = mapOf('(' to ')', '[' to ']', '{' to '}')
private val closers = openers.entries.associate { (k, v) -> v to k }
```

따옴표 (`"`, `'`) 는 깊이 개념이 모호해서 (한 글자가 여닫이 둘 다) 일부러 빠짐

---

## 사용처

| 위치 | 용도 |
|---|---|
| `page.app.EditorPanel` 의 `CombinedHighlightTransformation` | 캐럿 양옆 짝 괄호에 배경 색 입혀 시각적 매칭 |

---

- [목차로 돌아가기](https://monkshark.github.io/page-ide/#README_kr.md)
