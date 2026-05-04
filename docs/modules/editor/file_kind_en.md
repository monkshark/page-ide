# FileKind / FileKinds

> 한국어: [file_kind.md](https://monkshark.github.io/PAGE_IDE/#modules/editor/file_kind.md)

> `page/editor/src/main/kotlin/page/editor/FileKind.kt` — File classification (extension-based)

Classifies by extension only — text / image / SVG. No MIME inference or header sniffing.

---

## `FileKind`

```kotlin
enum class FileKind { TEXT, IMAGE, SVG }
```

Used as a branch key in `PreviewPanel`. Adding `BINARY`, `PDF`, `MARKDOWN` later means widening the `EditorPanel` / `PreviewPanel` branches too.

---

## `FileKinds.classify`

```kotlin
fun classify(path: Path): FileKind
```

Lowercases the extension before matching.

| Extension | Result |
|---|---|
| `.svg` | `FileKind.SVG` |
| `.png`, `.jpg`, `.jpeg`, `.gif`, `.bmp`, `.webp` | `FileKind.IMAGE` |
| Anything else / no extension | `FileKind.TEXT` |

Files with no extension (`Makefile`, `Dockerfile`, `.gitignore`) all fall through to `TEXT`. Treating them as text is fine as long as `BasicTextField` doesn't choke.

---

## Usage

| Location | Purpose |
|---|---|
| `page.app.Main` | Branches between `EditorPanel` / `PreviewPanel` based on the active tab |
| `page.app.PreviewPanel` | `IMAGE` / `SVG` branch picks the Skia decoder |

---

- [Back to index](https://monkshark.github.io/PAGE_IDE/#README_en.md)
