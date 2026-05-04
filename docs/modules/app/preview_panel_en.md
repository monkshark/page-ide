# PreviewPanel

> 한국어: [preview_panel.md](https://monkshark.github.io/PAGE_IDE/#modules/app/preview_panel.md)

> `page/app/src/main/kotlin/page/app/PreviewPanel.kt` — Image / SVG preview

Replaces the editor body when the active tab's `FileKind` is `IMAGE` or `SVG`. Wheel zoom, +/− buttons, click on `100%` to reset.

---

## Signature

```kotlin
@Composable
fun PreviewPanel(
    path: Path,
    kind: FileKind,
    modifier: Modifier = Modifier,
)
```

When `path` / `kind` changes, `loadPreview` re-runs and `zoom` resets to `1.0f`.

---

## `PreviewSource`

```kotlin
private sealed interface PreviewSource {
    val intrinsic: Size

    class Raster(val bitmap: ImageBitmap) : PreviewSource
    class Svg(val dom: SVGDOM) : PreviewSource
}
```

| Case | Decoder |
|---|---|
| `Raster` | `SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap()` |
| `Svg` | `SVGDOM(Data.makeFromBytes(bytes))` |

`loadPreview(path, kind)` is wrapped in `runCatching {...}.getOrNull()` — a failed decode falls back to a "Cannot preview" message instead of crashing the panel.

---

## Zoom model

| Constant | Value | Meaning |
|---|---|---|
| `MIN_ZOOM` / `MAX_ZOOM` | `0.1f` / `8f` | Zoom clamps |
| `ZOOM_STEP` | `1.25f` | One `+` / `−` step |
| `INITIAL_FIT_FRACTION` | `0.7f` | Initially fills only 70% of the panel — leaves margin |
| `WHEEL_ZOOM_FACTOR` | `1.1f` | One wheel notch |

```kotlin
private fun effectiveScale(intrinsic: Size, panelW: Float, panelH: Float, zoom: Float): Float
```

`baseline = min(panelW/iw, panelH/ih) × 0.7` — large images shrink to fit the panel, small ones cap at native size. The user's `zoom` multiplies on top.

`computeBaseline` returns `INITIAL_FIT_FRACTION` when intrinsic is non-finite, zero, or negative — covers SVGs with `width="100%"` and similar unitless declarations.

---

## SVG rendering

```kotlin
.drawBehind {
    drawIntoCanvas { canvas ->
        val nativeCanvas = canvas.nativeCanvas
        source.dom.setContainerSize(intrinsicW, intrinsicH)
        val cp = nativeCanvas.save()
        nativeCanvas.scale(size.width / intrinsicW, size.height / intrinsicH)
        source.dom.render(nativeCanvas)
        nativeCanvas.restoreToCount(cp)
    }
}
```

Sets the container to the intrinsic size, then scales to the layout box. `save` / `restoreToCount` keeps the canvas transform isolated so it doesn't accumulate across frames.

---

## Wheel / buttons

```kotlin
.onPointerEvent(PointerEventType.Scroll, PointerEventPass.Initial) { ... }
```

`PointerEventPass.Initial` runs before any child handler — wheel routes straight to zoom, not scroll. Up shrinks (`1/WHEEL_ZOOM_FACTOR`), down enlarges.

`ZoomToolbar` at the bottom:
- `−` → `zoom / ZOOM_STEP`
- `100%` label → `zoom = 1f` (reset)
- `+` → `zoom * ZOOM_STEP`

Everything `coerceIn(MIN_ZOOM, MAX_ZOOM)`.

---

## Usage

| Location | Purpose |
|---|---|
| `page.app.Main` (when the active tab's `FileKind` isn't text) | Renders this panel instead of the body |

---

- [Back to index](https://monkshark.github.io/PAGE_IDE/#README_en.md)
