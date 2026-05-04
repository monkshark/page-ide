# ui

> `page/ui/` — Glass design tokens, fonts, syntax palette

PAGE's visual base. Colors, fonts, and highlighting are centralized so every panel and dialog shares the same tone. The Composables that draw the screens live in `page:app`; this module only provides *values*

> 한국어: [ui.md](https://monkshark.github.io/PAGE_IDE/#modules/ui.md)

---

## Dependencies

| Kind | Content |
|---|---|
| External | Compose Material3, Compose Foundation |
| Internal | none |

Doesn't even depend on `page:core` — token-only, no domain types ever land here

---

## `GlassTheme`

```kotlin
@Composable
fun GlassTheme(content: @Composable () -> Unit)
```

Wrapper that applies a Material3 dark color scheme overridden with PAGE's tone. Both the main window and dialogs (`UnsavedChangesDialog`) wrap their content with this

### Color palette

| Token | Hex | Purpose |
|---|---|---|
| `primary` | `#8AB4FF` | Caret, active match, toggle chips |
| `onPrimary` | `#0A1126` | Text on primary |
| `secondary` | `#B8C5E0` | Secondary text / emphasis |
| `background` | `#0D1117` | Editor canvas |
| `onBackground` | `#E6EDF3` | Body text |
| `surface` | `#161B22` | Title bar, tab bar, status bar, search bar |
| `onSurface` | `#E6EDF3` | Text on surface |
| `surfaceVariant` | `#1C2128` | Sidebar (file tree) |
| `onSurfaceVariant` | `#8B949E` | Secondary labels, line-number gutter |
| `outline` | `#30363D` | Resize-handle guide line |

Brightness sits close to GitHub Dark — bright enough to stay readable without drowning out code colors

---

## `EditorFontFamily`

```kotlin
val EditorFontFamily: FontFamily
```

Monospace font used by the editor body and the line-number gutter. Uses `fonts/D2Coding.ttf` if present in resources, else falls back to `FontFamily.Monospace` — a missing font does not break the build

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

val GlassDarkSyntax: SyntaxPalette
```

Color bundle that maps 1:1 to `page:editor`'s `TokenKind`. `EditorPanel`'s `CombinedHighlightTransformation` consumes this palette to color tokens

| Token | Hex | Note |
|---|---|---|
| keyword | `#FF7B72` | `class`, `fun`, `val`, etc. |
| string | `#A5D6FF` | String literals |
| number | `#79C0FF` | Numeric literals |
| comment | `#8B949E` | Line / block comments (gray so they recede) |
| annotation | `#D2A8FF` | `@Override`, `@Composable`, etc. |
| type | `#FFA657` | Type identifiers (capitalized) |

`PUNCT` is intentionally left uncolored — punctuation is too dense, and coloring it actually hurts readability

---

## Planned

| Token | Purpose |
|---|---|
| `GlassLight` | Light-theme color scheme (Glass value: respect the user's environment) |
| `GlassLightSyntax` | Light-theme syntax palette |
| `Spacing` / `Radius` | Spacing / corner-radius tokens (currently `dp` literals scattered across panels) |
| `Typography` | Material3 `Typography` override |

---

- [Back to index](https://monkshark.github.io/PAGE_IDE/#README_en.md)
- [Architecture](https://monkshark.github.io/PAGE_IDE/#guides/architecture_en.md)
