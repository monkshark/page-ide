# TabBar

> 한국어: [tab_bar.md](https://monkshark.github.io/PAGE_IDE/#modules/app/tab_bar.md)

> `page/app/src/main/kotlin/page/app/TabBar.kt` — Top tab strip with drag-reorder

Renders `TabBook.tabs` as a horizontally scrollable row, tints the active chip, drags reorder, wheel scrolls.

---

## Signature

```kotlin
@Composable
fun TabBar(
    book: TabBook,
    onActivate: (Int) -> Unit,
    onClose: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
)
```

| Parameter | Meaning |
|---|---|
| `book` | The tab bundle — reads `tabs` / `activeIndex` directly |
| `onActivate(i)` | Tab tap activates that index |
| `onClose(i)` | `×` click — caller pops the dialog if the tab is dirty |
| `onMove(from, to)` | Drag result — calls `TabBook.move` |

Fixed height: `TabBarHeight = 32.dp`.

---

## Drag-reorder

Inside `pointerInput(book.tabs.size)`, `awaitFirstDown` → `drag`. `dragOffsetPx` accumulates after `touchSlop` is crossed.

When the offset exceeds half of the neighbor chip's width, the two swap. `tabBounds` swaps with them so the next threshold updates naturally — a single drag can pass through several chips smoothly.

```kotlin
private fun swapBounds(bounds: MutableMap<Int, IntRange>, a: Int, b: Int)
```

---

## Wheel scrolling

When tabs exceed the viewport, `horizontalScroll(scrollState)` kicks in. `PointerEventType.Scroll` is intercepted and translated to `scrollState.scrollBy(deltaY * 60f)` — trackpad and mouse wheel both map to horizontal motion.

---

## `TabChip`

```kotlin
@Composable
private fun TabChip(
    tab: OpenTab,
    isActive: Boolean,
    offsetPx: Int,
    onClose: () -> Unit,
    onBoundsChanged: (Int, Int) -> Unit,
)
```

`onBoundsChanged` fires from `onGloballyPositioned` — the drag logic needs each chip's width to compute swap thresholds.

Active background: `colorScheme.background` (same tone as the editor body → visual link "this chip = the body I'm looking at").

---

## `CloseButton`

```kotlin
@Composable
private fun CloseButton(dirty: Boolean, onClick: () -> Unit)
```

Normally `×`. When the tab is dirty and the cursor is *not* over the chip, shows `●` (unsaved indicator). On hover it flips back to `×` — the indicator and the button share the same slot, so chip width never shifts.

---

## Usage

| Location | Purpose |
|---|---|
| Top of `page.app.Main` | Renders chips, hands drag/close/activate back as callbacks |

---

- [Back to index](https://monkshark.github.io/PAGE_IDE/#README_en.md)
