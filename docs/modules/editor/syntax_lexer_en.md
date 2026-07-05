# SyntaxLexer / Token / TokenKind

> 한국어: [syntax_lexer.md](https://monkshark.github.io/page-ide/#modules/editor/syntax_lexer.md)

> `page/editor/src/main/kotlin/page/editor/SyntaxLexer.kt` — Syntax-lexer interface

The contract every language lexer follows. `TokenKind` keeps the kinds limited to seven (six color, one transparent).

---

## `TokenKind`

```kotlin
enum class TokenKind {
    KEYWORD,
    STRING,
    NUMBER,
    COMMENT,
    ANNOTATION,
    TYPE,
    PUNCT,
}
```

Maps 1:1 to the six colors in `SyntaxPalette`. `PUNCT` is intentionally uncolored, preserving the body tone — see `SyntaxColors` for the why.

---

## `Token`

```kotlin
data class Token(val kind: TokenKind, val range: IntRange) {
    val start: Int get() = range.first
    val endExclusive: Int get() = range.last + 1
}
```

One token = kind + text range. `endExclusive` is a convenience for `AnnotatedString.Builder.addStyle(start, end)`'s exclusive end.

---

## `SyntaxLexer`

```kotlin
interface SyntaxLexer {
    fun tokenize(text: String): List<Token>
}
```

A simple contract: read the file once, return a token list once. No incremental tokenization or partial update optimization — `EditorPanel` caches the result via `remember(value.text)`.

---

## Implementations

| Implementation | Target |
|---|---|
| `KotlinLexer` | `.kt`, `.kts` |
| `JavaLexer` | `.java` |
| `JsonLexer` | `.json` |

The dispatch entry point is `SyntaxLexers.forPath(path)` — unsupported extensions return `null` (body tone only, no token colors).

---

## Usage

| Location | Purpose |
|---|---|
| `CombinedHighlightTransformation` in `page.app.EditorPanel` | Feeds `lexer.tokenize(text)` into an `AnnotatedString` with `palette[kind]` colors |

---

- [Back to index](https://monkshark.github.io/page-ide/#README_en.md)
