# JsonLexer

> 한국어: [json_lexer.md](https://monkshark.github.io/PAGE_IDE/#modules/editor/json_lexer.md)

> `page/editor/src/main/kotlin/page/editor/JsonLexer.kt` — JSON syntax lexer (standalone)

JSON has no keywords / identifiers / annotations, so it shares too little with `JvmLexer` to reuse it — implemented standalone.

---

## `tokenize`

```kotlin
override fun tokenize(text: String): List<Token>
```

Scans one char at a time and emits only these tokens:

| Token | Rule |
|---|---|
| `STRING` | `"` start, `\` skips one escape char, `"` end. Stops at newline (broken JSON should still color cleanly) |
| `NUMBER` | Optional `-`, integer, optional `.fraction`, optional `e/E[+−]exponent` |
| `KEYWORD` | `true`, `false`, `null` (exactly these three) |

Punctuation (`,`, `:`, `{`, `}`, `[`, `]`) is intentionally untokenized — `PUNCT` has no color, so it stays the body tone.

---

## Non-goals

- Schema validation — invalid JSON is rendered too (mid-edit it's almost always invalid)
- JSON5 / JSONC — no support for `//`, `/* */`, trailing commas. Would need a separate lexer

---

## Usage

| Location | Purpose |
|---|---|
| `SyntaxLexers.forPath(path)` | `.json` extension dispatch |

---

- [Back to index](https://monkshark.github.io/PAGE_IDE/#README_en.md)
