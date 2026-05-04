# JavaLexer

> 한국어: [java_lexer.md](https://monkshark.github.io/PAGE_IDE/#modules/editor/java_lexer.md)

> `page/editor/src/main/kotlin/page/editor/JavaLexer.kt` — Java syntax lexer (delegate)

A thin delegate that passes the Java keyword set and the triple-quote (Text Block) flag to `JvmLexer`. The tokenizer itself lives in `JvmLexer`.

---

## Definition

```kotlin
object JavaLexer : SyntaxLexer by JavaLexerImpl

private object JavaLexerImpl : JvmLexer(
    keywords = JAVA_KEYWORDS,
    supportTripleQuoted = true,
)
```

`SyntaxLexer by JavaLexerImpl` — Kotlin interface delegation. `JavaLexer.tokenize(...)` forwards to `JavaLexerImpl.tokenize(...)`.

`JavaLexerImpl` is `private` because there's never a reason to instantiate it directly — the only entry point is `object JavaLexer`.

---

## `JAVA_KEYWORDS`

About 60 entries — JLS (Java Language Specification) reserved words plus contextual keywords.

```kotlin
"abstract", "assert", "boolean", "break", "byte", "case", "catch", "char",
"class", "const", "continue", "default", "do", "double", "else", "enum",
"extends", "false", "final", "finally", "float", "for", "goto", "if",
"implements", "import", "instanceof", "int", "interface", "long", "native",
"new", "non-sealed", "null", "package", "permits", "private", "protected",
"public", "record", "return", "sealed", "short", "static", "strictfp",
"super", "switch", "synchronized", "this", "throw", "throws", "transient",
"true", "try", "var", "void", "volatile", "while", "yield",
```

Includes newer keywords like `record`, `sealed`, `non-sealed`, `permits`, `var`, `yield` (Java 17 baseline).

---

## Usage

| Location | Purpose |
|---|---|
| `SyntaxLexers.forPath(path)` | `.java` extension dispatch |

---

- [Back to index](https://monkshark.github.io/PAGE_IDE/#README_en.md)
