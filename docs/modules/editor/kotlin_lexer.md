# KotlinLexer

> `page/editor/src/main/kotlin/page/editor/KotlinLexer.kt` — Kotlin 신택스 렉서 (위임)

`JvmLexer` 에 Kotlin 키워드 셋만 넘기는 박막 위임. `JavaLexer` 와 같은 구조

> English: [kotlin_lexer_en.md](https://monkshark.github.io/page-ide/#modules/editor/kotlin_lexer_en.md)

---

## 정의

```kotlin
object KotlinLexer : SyntaxLexer by KotlinLexerImpl

private object KotlinLexerImpl : JvmLexer(
    keywords = KOTLIN_KEYWORDS,
    supportTripleQuoted = true,
)
```

`SyntaxLexer by KotlinLexerImpl` — 인터페이스 위임. 외부에서는 `object KotlinLexer` 만 보인다

---

## `KOTLIN_KEYWORDS`

Kotlin 의 hard keywords + soft keywords + modifier keywords 75 여 개

```kotlin
"abstract", "actual", "annotation", "as", "break", "by", "catch",
"class", "companion", "const", "constructor", "continue", "crossinline",
"data", "delegate", "do", "dynamic", "else", "enum", "expect", "external",
"false", "field", "file", "final", "finally", "for", "fun", "get", "if",
"import", "in", "infix", "init", "inline", "inner", "interface", "internal",
"is", "it", "lateinit", "noinline", "null", "object", "open", "operator",
"out", "override", "package", "param", "private", "property", "protected",
"public", "receiver", "reified", "return", "sealed", "set", "setparam",
"super", "suspend", "tailrec", "this", "throw", "true", "try", "typealias",
"typeof", "val", "var", "vararg", "when", "where", "while",
```

`it` 도 키워드로 포함 — 람다 안에서 자주 등장하는 암묵적 파라미터라 색을 입혀두면 가독성에 도움. `delegate`, `field`, `file` 같은 modifier-only 키워드도 포함

---

## 사용처

| 위치 | 용도 |
|---|---|
| `SyntaxLexers.forPath(path)` | `.kt`, `.kts` 확장자 분기 |

---

- [목차로 돌아가기](https://monkshark.github.io/page-ide/#README_kr.md)
