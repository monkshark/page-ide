# SyntaxLexers

> 한국어: [syntax_lexers.md](https://monkshark.github.io/PAGE_IDE/#modules/editor/syntax_lexers.md)

> `page/editor/src/main/kotlin/page/editor/SyntaxLexers.kt` — Extension-based lexer dispatch

Takes a `Path` and returns the matching `SyntaxLexer`. Unsupported extensions return `null` — the caller renders body-tone only.

---

## `forPath`

```kotlin
fun forPath(path: Path): SyntaxLexer?
```

Lowercases the extension before matching.

| Extension | Lexer |
|---|---|
| `.kt`, `.kts` | `KotlinLexer` |
| `.java` | `JavaLexer` |
| `.json` | `JsonLexer` |
| Anything else / no extension | `null` |

Adding a new language is a one-line addition to the `when` — implement the lexer and add a branch.

---

## Planned

| Extension | Lexer (planned) |
|---|---|
| `.py` | `PythonLexer` |
| `.md` | `MarkdownLexer` (separate from `MarkdownFence`, for body tokenization) |
| `.xml`, `.html` | `XmlLexer` |
| `.css`, `.scss` | `CssLexer` |

---

## Usage

| Location | Purpose |
|---|---|
| `page.app.Main` (on tab activation) | Picks a lexer from the active tab's path and passes it to `EditorPanel` |

---

- [Back to index](https://monkshark.github.io/PAGE_IDE/#README_en.md)
