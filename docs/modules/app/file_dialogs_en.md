# FileDialogs

> 한국어: [file_dialogs.md](https://monkshark.github.io/PAGE_IDE/#modules/app/file_dialogs.md)

> `page/app/src/main/kotlin/page/app/FileDialogs.kt` — `JFileChooser` wrappers

Wraps Swing's `JFileChooser` and normalizes the result to `Path?`, so callers only need to check `null` for cancellation.

---

## `open`

```kotlin
fun open(parent: Frame): Path?
```

File-open dialog in `FILES_ONLY` mode. Returns `null` on *Cancel*.

| Caller | When |
|---|---|
| `Ctrl+O` | Open a single file |

---

## `saveAs`

```kotlin
fun saveAs(parent: Frame, suggested: String? = null): Path?
```

Save dialog. When `suggested` is given (e.g., `Untitled.kt`), it pre-fills the filename.

| Caller | When |
|---|---|
| `Ctrl+S` on a tab whose path is a virtual *untitled* | Pick a new path |

---

## `openDirectory`

```kotlin
fun openDirectory(parent: Frame): Path?
```

Folder-open dialog in `DIRECTORIES_ONLY` mode — used as the sidebar tree root.

| Caller | When |
|---|---|
| `Ctrl+Shift+O` | Open a project folder |

---

## Why Swing, not Compose

Compose Desktop already runs on top of Swing, and the JVM file dialog is the closest thing to a native OS chooser. Drawing one in pure Compose would mean re-implementing OS-specific filesystem behavior.

---

- [Back to index](https://monkshark.github.io/PAGE_IDE/#README_en.md)
