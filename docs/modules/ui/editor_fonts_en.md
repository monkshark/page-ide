# EditorFontFamily

> 한국어: [editor_fonts.md](https://monkshark.github.io/PAGE_IDE/#modules/ui/editor_fonts.md)

> `page/ui/src/main/kotlin/page/ui/EditorFonts.kt` — Editor body font (D2Coding with Monospace fallback)

Uses `D2Coding.ttf` from resources if present, falls back to `FontFamily.Monospace`. A missing font does not break the build.

---

## Definition

```kotlin
private const val PRIMARY_FONT_RESOURCE = "fonts/D2Coding.ttf"

val EditorFontFamily: FontFamily = run {
    val cl = Thread.currentThread().contextClassLoader
        ?: object {}.javaClass.classLoader
    if (cl?.getResource(PRIMARY_FONT_RESOURCE) != null) {
        FontFamily(Font(resource = PRIMARY_FONT_RESOURCE))
    } else {
        FontFamily.Monospace
    }
}
```

The `run { ... }` evaluates once into a top-level `val`. Classloader lookup happens once at boot.

| Condition | Result |
|---|---|
| `fonts/D2Coding.ttf` is on the classpath | D2Coding |
| Resource missing | `FontFamily.Monospace` (JDK default mono) |

The `object {}.javaClass.classLoader` fallback covers environments where `Thread.currentThread().contextClassLoader` is `null` (some IDE test runners). Without it the app NPEs at startup.

---

## Usage

| Location | Purpose |
|---|---|
| `page.app.EditorPanel` | `textStyle.fontFamily` for `BasicTextField` |
| `page.app.LineNumberGutter` | Line-number text — must match the body font for line heights to align |

---

- [Back to index](https://monkshark.github.io/PAGE_IDE/#README_en.md)
