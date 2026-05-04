# UnsavedChangesDialog

> 한국어: [unsaved_changes_dialog.md](https://monkshark.github.io/PAGE_IDE/#modules/app/unsaved_changes_dialog.md)

> `page/app/src/main/kotlin/page/app/UnsavedChangesDialog.kt` — Save-confirmation modal

Asks Save / Discard / Cancel when a dirty tab is being closed or the app is exiting. Tab-close and app-exit share the same dialog.

---

## Signature

```kotlin
@Composable
internal fun UnsavedChangesDialog(
    fileNames: List<String>,
    isAppExit: Boolean,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
    onCancel: () -> Unit,
)
```

| Parameter | Meaning |
|---|---|
| `fileNames` | Names of unsaved files — listed as bullets in the body |
| `isAppExit` | `true` ⇒ "Quit without saving?"; `false` ⇒ "Save?" |
| `onSave` / `onDiscard` / `onCancel` | Three button callbacks — `Main` performs the actual close/exit |

---

## Keyboard

| Key | Action |
|---|---|
| `Esc` | `onCancel` |
| `Y` | `onSave` |
| `N` | `onDiscard` |

Captured in `onPreviewKeyEvent`, so they work without any input widget on screen.

---

## Appearance

```kotlin
DialogWindow(undecorated = true, resizable = false, ...)
```

- Fixed `460 × 220 dp`, no title bar.
- Top 20dp is `WindowDraggableArea` so the dialog can still be moved.
- Wrapped in `GlassTheme` so it matches the main window's tone.

`FileList` paints `• filename` rows on a surface-tinted block — keeps it readable when multiple files are dirty at once (the app-exit case).

---

## Buttons

Order is `[Cancel] [Don't Save] [Save]` — only `Save` is primary.

`Enter` is intentionally not bound: with no input widget in the body, where it would go is ambiguous. Only the explicit keys (`Y/N/Esc`) are accepted.

---

## Usage

| Location | Purpose |
|---|---|
| `page.app.Main` shows it while `pendingClose != null` | Confirms dirty tab/app close |

---

- [Back to index](https://monkshark.github.io/PAGE_IDE/#README_en.md)
