# PreviewPanel

> `page/app/src/main/kotlin/page/app/PreviewPanel.kt` — 이미지/SVG 미리보기

`FileKind` 가 `IMAGE` 또는 `SVG` 인 탭에서 본문 대신 표시. 휠 줌, +/− 버튼, 100% 라벨 클릭으로 리셋

> English: [preview_panel_en.md](https://monkshark.github.io/page-ide/#modules/app/preview_panel_en.md)

---

## 시그니처

```kotlin
@Composable
fun PreviewPanel(
    path: Path,
    kind: FileKind,
    modifier: Modifier = Modifier,
)
```

`path` / `kind` 가 바뀌면 `loadPreview` 가 다시 돌고 `zoom` 도 `1.0f` 로 리셋

---

## `PreviewSource`

```kotlin
private sealed interface PreviewSource {
    val intrinsic: Size

    class Raster(val bitmap: ImageBitmap) : PreviewSource
    class Svg(val dom: SVGDOM) : PreviewSource
}
```

| 케이스 | 디코더 |
|---|---|
| `Raster` | `SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap()` |
| `Svg` | `SVGDOM(Data.makeFromBytes(bytes))` |

`loadPreview(path, kind)` 는 `runCatching {...}.getOrNull()` — 디코딩 실패해도 패널이 죽지 않고 "Cannot preview" 텍스트로 폴백

---

## 줌 모델

| 상수 | 값 | 의미 |
|---|---|---|
| `MIN_ZOOM` / `MAX_ZOOM` | `0.1f` / `8f` | 줌 한계 |
| `ZOOM_STEP` | `1.25f` | `+` / `−` 버튼 1단계 |
| `INITIAL_FIT_FRACTION` | `0.7f` | 초기에 패널의 70% 만 채워서 여백 확보 |
| `WHEEL_ZOOM_FACTOR` | `1.1f` | 휠 한 노치 |

```kotlin
private fun effectiveScale(intrinsic: Size, panelW: Float, panelH: Float, zoom: Float): Float
```

`baseline = min(panelW/iw, panelH/ih) × 0.7` — 큰 이미지는 패널에 맞춰 줄이고, 작은 이미지는 원본 크기까지만. 그 위에 사용자 `zoom` 을 곱해 최종 스케일

`computeBaseline` 은 intrinsic 이 무한/0/음수일 때 `INITIAL_FIT_FRACTION` 을 그대로 반환 — SVG 의 `width="100%"` 같은 단위 없는 케이스 대비

---

## SVG 렌더

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

intrinsic 좌표계로 `setContainerSize` 한 다음 `scale` 로 박스 크기에 맞춤. `save/restoreToCount` 로 캔버스 변환을 격리해 다음 프레임에 누적되지 않게

---

## 휠 / 버튼

```kotlin
.onPointerEvent(PointerEventType.Scroll, PointerEventPass.Initial) { ... }
```

`PointerEventPass.Initial` 로 받아서 자식 위젯보다 먼저 처리 → 휠이 스크롤 대신 줌으로 직행. 위로 스크롤하면 축소 (`1/WHEEL_ZOOM_FACTOR`), 아래로 확대

`ZoomToolbar` 하단:
- `−` 버튼 → `zoom / ZOOM_STEP`
- `100%` 라벨 → `zoom = 1f` (리셋)
- `+` 버튼 → `zoom * ZOOM_STEP`

전부 `coerceIn(MIN_ZOOM, MAX_ZOOM)`

---

## 사용처

| 위치 | 용도 |
|---|---|
| `page.app.Main` (활성 탭의 `FileKind` 가 텍스트가 아닐 때) | 본문 대신 이 패널 |

---

- [목차로 돌아가기](https://monkshark.github.io/page-ide/#README_kr.md)
