# JvmLexer

> 한국어: [jvm_lexer.md](https://monkshark.github.io/page-ide/#modules/editor/jvm_lexer.md)

> `page/editor/src/main/kotlin/page/editor/JvmLexer.kt` — Shared tokenizer base for Kotlin / Java

`internal abstract class`. Kotlin and Java differ only in keyword set and the triple-quote flag, so the common tokenizer was lifted into a parent.

---

## Constructor

```kotlin
internal abstract class JvmLexer(
    private val keywords: Set<String>,
    private val supportTripleQuoted: Boolean,
) : SyntaxLexer
```

| Parameter | Description |
|---|---|
| `keywords` | Reserved words for the language (identifiers in this set become `KEYWORD`) |
| `supportTripleQuoted` | Enables `"""..."""` recognition (Kotlin raw string, Java text block) |

---

## `tokenize`

```kotlin
override fun tokenize(text: String): List<Token>
```

Walks one char at a time, emitting tokens in priority order.

| Pattern | Token | Note |
|---|---|---|
| `//...` to end of line | `COMMENT` | |
| `/* ... */` | `COMMENT` | Runs to EOF if unclosed |
| `"""..."""` | `STRING` | Only when `supportTripleQuoted = true` |
| `"..."` | `STRING` | `\` escapes, ends at newline |
| `'...'` | `STRING` | Same rule |
| `@identifier` | `ANNOTATION` | `@Override`, `@Composable` |
| Numbers | `NUMBER` | `0x...` hex, `1_000_000` underscores, `1.5e10`, suffix `f F d D L l u` |
| Identifiers | `KEYWORD` / `TYPE` | In keyword set → `KEYWORD`; capitalized start, length ≥ 2 → `TYPE` |
| Other | (no token) | Body tone preserved |

---

## Identifier rules

```kotlin
private fun isIdentStart(c: Char) = c == '_' || c.isLetter()
private fun isIdentPart(c: Char) = c == '_' || c.isLetterOrDigit()
private fun startsWithUpperCase(s: String) = s.isNotEmpty() && s[0].isUpperCase() && s.length > 1
```

The `length > 1` guard in `startsWithUpperCase` keeps single-letter uppercase identifiers (`T`, `K`) from being colored as types — they look like generic type parameters, but body color reads more naturally.

---

## Non-goals

- Semantic analysis — type matching is a simple capitalized start = type rule. A variable named `Foo` gets type-colored (a misclass, but cheap)
- Macro / annotation-arg parsing — `@Composable` itself is tokenized, but `(arg = 1)` is skipped

---

## Usage

| Location | Purpose |
|---|---|
| `KotlinLexer` | `keywords = KOTLIN_KEYWORDS`, `supportTripleQuoted = true` |
| `JavaLexer` | `keywords = JAVA_KEYWORDS`, `supportTripleQuoted = true` |

---

- [Back to index](https://monkshark.github.io/page-ide/#README_en.md)
