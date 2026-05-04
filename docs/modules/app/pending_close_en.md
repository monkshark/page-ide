# PendingClose

> 한국어: [pending_close.md](https://monkshark.github.io/PAGE_IDE/#modules/app/pending_close.md)

> `page/app/src/main/kotlin/page/app/PendingClose.kt` — Identifies which close request is in flight

A sealed interface so `UnsavedChangesDialog` can distinguish the close request it is handling.

---

## Definition

```kotlin
internal sealed interface PendingClose {
    data class Tab(val index: Int) : PendingClose
    data object App : PendingClose
}
```

| Case | Meaning |
|---|---|
| `Tab(index)` | Closing a single tab — `Ctrl+W` or the chip's `×` |
| `App` | Closing the whole window |

---

## Why a sealed interface

The dialog callbacks branch on the case — tab close ends at `book.close(index)`, app close cascades to `exitApplication()`. A sealed interface keeps that branch in one place.

---

## Usage

| Location | Purpose |
|---|---|
| `page.app.Main` `pendingClose: PendingClose?` | Drives dialog visibility |
| `page.app.UnsavedChangesDialog` `isAppExit` | `pendingClose is PendingClose.App` flips the trailing question |

---

- [Back to index](https://monkshark.github.io/PAGE_IDE/#README_en.md)
