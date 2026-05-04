# SyntaxPalette / GlassDarkSyntax

> 한국어: [syntax_colors.md](https://monkshark.github.io/PAGE_IDE/#modules/ui/syntax_colors.md)

> `page/ui/src/main/kotlin/page/ui/SyntaxColors.kt` — Syntax token color bundle

Maps 1:1 to `TokenKind` in `page:editor`. Palette and lexer are decoupled — swap the instance to re-color.

---

## `SyntaxPalette`

```kotlin
data class SyntaxPalette(
    val keyword: Color,
    val string: Color,
    val number: Color,
    val comment: Color,
    val annotation: Color,
    val type: Color,
)
```

`data class` — equality/`copy` for free. `PUNCT` is intentionally absent (parens, semicolons, commas are too frequent — coloring them adds noise → keep `onBackground`).

---

## `GlassDarkSyntax`

```kotlin
val GlassDarkSyntax = SyntaxPalette(
    keyword = Color(0xFFFF7B72),
    string = Color(0xFFA5D6FF),
    number = Color(0xFF79C0FF),
    comment = Color(0xFF8B949E),
    annotation = Color(0xFFD2A8FF),
    type = Color(0xFFFFA657),
)
```

| Token | Hex | Examples |
|---|---|---|
| keyword | `#FF7B72` | `class`, `fun`, `val`, `if`, `return` |
| string | `#A5D6FF` | `"hello"`, `'a'`, `"""multi"""` |
| number | `#79C0FF` | `42`, `3.14f`, `0xFF`, `1e10` |
| comment | `#8B949E` | `// line`, `/* block */` |
| annotation | `#D2A8FF` | `@Composable`, `@Override` |
| type | `#FFA657` | `String`, `MutableList` (capitalized identifiers) |

GitHub Dark tones.

---

## Usage

| Location | Purpose |
|---|---|
| `CombinedHighlightTransformation` in `page.app.EditorPanel` | Maps `Token.kind` → `palette[kind]` to build `SpanStyle`s on the `AnnotatedString` |

---

## Planned

| Token | Purpose |
|---|---|
| `GlassLightSyntax` | Light theme |
| User-custom palette | Settings color picker |

---

- [Back to index](https://monkshark.github.io/PAGE_IDE/#README_en.md)
