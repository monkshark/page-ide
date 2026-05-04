# KotlinLexer

> 한국어: [kotlin_lexer.md](https://monkshark.github.io/PAGE_IDE/#modules/editor/kotlin_lexer.md)

> `page/editor/src/main/kotlin/page/editor/KotlinLexer.kt` — Kotlin syntax lexer (delegate)

A thin delegate that passes the Kotlin keyword set to `JvmLexer`. Same shape as `JavaLexer`.

---

## Definition

```kotlin
object KotlinLexer : SyntaxLexer by KotlinLexerImpl

private object KotlinLexerImpl : JvmLexer(
    keywords = KOTLIN_KEYWORDS,
    supportTripleQuoted = true,
)
```

`SyntaxLexer by KotlinLexerImpl` — interface delegation. Only `object KotlinLexer` is exposed.

---

## `KOTLIN_KEYWORDS`

About 75 entries — Kotlin's hard keywords + soft keywords + modifier keywords.

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

`it` is included — it's the implicit lambda parameter that shows up everywhere, and coloring it helps scanability. Modifier-only keywords like `delegate`, `field`, `file` are included too.

---

## Usage

| Location | Purpose |
|---|---|
| `SyntaxLexers.forPath(path)` | `.kt`, `.kts` extension dispatch |

---

- [Back to index](https://monkshark.github.io/PAGE_IDE/#README_en.md)
