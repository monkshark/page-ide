# FileDocument

> 한국어: [file_document.md](https://monkshark.github.io/page-ide/#modules/editor/file_document.md)

> `page/editor/src/main/kotlin/page/editor/FileDocument.kt` — File read / write (UTF-8)

A thin wrapper over `Files.readString` / `writeString`. Single entry point so the encoding is pinned in one place.

---

## `load`

```kotlin
fun load(path: Path): String
```

Reads the entire file as UTF-8 and returns it as a string. `IOException` is rethrown — the caller handles it.

---

## `loadOrNull`

```kotlin
fun loadOrNull(path: Path): String?
```

`load` with try/catch. Returns `null` on failure. Used where the failure mode doesn't need to be distinguished (e.g. previewing a file from the sidebar).

---

## `save`

```kotlin
fun save(path: Path, text: String)
```

Overwrites the file with the text as UTF-8. Creates the file if missing. No file locking / partial-write protection / temp-file-then-rename safety — plain overwrite.

---

## Encoding policy

| Item | Value |
|---|---|
| `Charset` | `StandardCharsets.UTF_8` (no BOM) |
| Line endings | Preserved as-is (Windows CRLF stays CRLF) |

To make encoding user-configurable later, add a `Charset` parameter and keep UTF-8 as the default.

---

## Usage

| Location | Purpose |
|---|---|
| `page.app.Main` (Ctrl+O) | Opens via `loadOrNull` and adds a tab |
| `page.app.Main` (Ctrl+S) | `save`, then `markActiveSaved` |
| `page.app.FileTreePanel` | `loadOrNull` on file click |

---

- [Back to index](https://monkshark.github.io/page-ide/#README_en.md)
