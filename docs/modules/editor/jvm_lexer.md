# JvmLexer

> `page/editor/src/main/kotlin/page/editor/JvmLexer.kt` — Kotlin / Java 공용 토큰화 베이스

`internal abstract class`. `KotlinLexer` 와 `JavaLexer` 가 키워드 셋과 트리플 따옴표 옵션만 다르고 토큰화 규칙은 동일해서 공통 부모로 추출

> English: [jvm_lexer_en.md](https://monkshark.github.io/page-ide/#modules/editor/jvm_lexer_en.md)

---

## 생성자

```kotlin
internal abstract class JvmLexer(
    private val keywords: Set<String>,
    private val supportTripleQuoted: Boolean,
) : SyntaxLexer
```

| 파라미터 | 설명 |
|---|---|
| `keywords` | 해당 언어의 예약어 셋 (식별자가 이 셋에 포함되면 `KEYWORD`) |
| `supportTripleQuoted` | `"""..."""` 인식 활성화 (Kotlin Raw String, Java Text Block) |

---

## `tokenize`

```kotlin
override fun tokenize(text: String): List<Token>
```

한 글자씩 보면서 우선순위 분기로 토큰을 잘라낸다

| 패턴 | 토큰 | 비고 |
|---|---|---|
| `//...` 줄 끝까지 | `COMMENT` | |
| `/* ... */` | `COMMENT` | 닫힘 없으면 텍스트 끝까지 |
| `"""..."""` | `STRING` | `supportTripleQuoted = true` 일 때만 |
| `"..."` | `STRING` | `\` 이스케이프, 줄바꿈 만나면 종료 |
| `'...'` | `STRING` | 동일 규칙 |
| `@식별자` | `ANNOTATION` | `@Override`, `@Composable` |
| 숫자 | `NUMBER` | `0x...` 16 진, `1_000_000` 언더스코어, `1.5e10`, 접미사 `f F d D L l u` |
| 식별자 | `KEYWORD` / `TYPE` | 키워드 셋이면 `KEYWORD`, 대문자 시작 + 길이 ≥ 2 면 `TYPE` |
| 그 외 | (토큰 없음) | 본문 톤 그대로 |

---

## 식별자 규칙

```kotlin
private fun isIdentStart(c: Char) = c == '_' || c.isLetter()
private fun isIdentPart(c: Char) = c == '_' || c.isLetterOrDigit()
private fun startsWithUpperCase(s: String) = s.isNotEmpty() && s[0].isUpperCase() && s.length > 1
```

`startsWithUpperCase` 의 `length > 1` 가드는 한 글자 대문자 변수 (`T`, `K`) 가 의도치 않게 타입 색을 받지 않게 — 제네릭 타입 파라미터처럼 보이지만 실제로는 본문 색이 더 자연스러움

---

## 비목표

- 의미 분석 — 식별자 → 타입 매칭은 단순 대문자 시작 = 타입 규칙. `String` 변수 이름이 `Foo` 면 타입으로 칠해진다 (오인식이지만 비용이 낮음)
- 매크로 / 어노테이션 인자 파싱 — `@Composable` 자체만 토큰화, `(arg = 1)` 부분은 건너뜀

---

## 사용처

| 위치 | 용도 |
|---|---|
| `KotlinLexer` | `keywords = KOTLIN_KEYWORDS`, `supportTripleQuoted = true` |
| `JavaLexer` | `keywords = JAVA_KEYWORDS`, `supportTripleQuoted = true` |

---

- [목차로 돌아가기](https://monkshark.github.io/page-ide/#README_kr.md)
