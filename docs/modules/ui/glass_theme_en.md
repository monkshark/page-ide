# GlassTheme

> 한국어: [glass_theme.md](https://monkshark.github.io/PAGE_IDE/#modules/ui/glass_theme.md)

> `page/ui/src/main/kotlin/page/ui/GlassTheme.kt` — Material3 dark color scheme + wrapper Composable

PAGE's dark palette. The main window and every dialog must wrap their content with this function to get the same colors.

---

## Color scheme

```kotlin
private val GlassDark = darkColorScheme(
    primary = Color(0xFF8AB4FF),
    onPrimary = Color(0xFF0A1126),
    secondary = Color(0xFFB8C5E0),
    background = Color(0xFF0D1117),
    onBackground = Color(0xFFE6EDF3),
    surface = Color(0xFF161B22),
    onSurface = Color(0xFFE6EDF3),
    surfaceVariant = Color(0xFF1C2128),
    onSurfaceVariant = Color(0xFF8B949E),
    outline = Color(0xFF30363D),
)
```

| Token | Hex | Purpose |
|---|---|---|
| `primary` | `#8AB4FF` | Caret, active match, toggle chips |
| `onPrimary` | `#0A1126` | Text on primary |
| `secondary` | `#B8C5E0` | Secondary text |
| `background` | `#0D1117` | Editor canvas |
| `onBackground` | `#E6EDF3` | Body text |
| `surface` | `#161B22` | Title bar, tab bar, status bar, search bar |
| `onSurface` | `#E6EDF3` | Text on surface |
| `surfaceVariant` | `#1C2128` | Sidebar (file tree) |
| `onSurfaceVariant` | `#8B949E` | Secondary labels, line-number gutter |
| `outline` | `#30363D` | Resize-handle guide |

GitHub Dark tones. Body readability and token colors both stay legible at this brightness.

---

## `GlassTheme`

```kotlin
@Composable
fun GlassTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = GlassDark, content = content)
}
```

A one-line `MaterialTheme` wrap. Only `colorScheme` is overridden; `Typography` and `Shapes` keep Material3 defaults. Fonts are handled separately by `EditorFontFamily`.

Both `Window` and `DialogWindow` (`UnsavedChangesDialog`) must wrap their content with this. A window that omits it falls back to Material3's default dark (grayer) palette.

---

## Planned

| Token | Purpose |
|---|---|
| `GlassLight` | Light color scheme |
| `Theme(content)` | Dark/light auto-dispatch wrapper |

---

- [Back to index](https://monkshark.github.io/PAGE_IDE/#README_en.md)
