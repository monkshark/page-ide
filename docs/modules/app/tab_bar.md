# TabBar

> `page/app/src/main/kotlin/page/app/TabBar.kt` — 상단 탭 줄 + 드래그 재배치

`TabBook.tabs` 를 가로 스크롤 row 로 그리고, 활성 칩에 배경 톤을 입힌다. 드래그로 순서를 바꾸고, 휠로 좌우 스크롤

> English: [tab_bar_en.md](https://monkshark.github.io/PAGE_IDE/#modules/app/tab_bar_en.md)

---

## 시그니처

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

| 파라미터 | 의미 |
|---|---|
| `book` | 탭 묶음 — `tabs` / `activeIndex` 를 직접 읽는다 |
| `onActivate(i)` | 탭 클릭 시 활성 인덱스 전환 |
| `onClose(i)` | 칩의 `×` 클릭 시 — dirty 면 호출부가 다이얼로그 띄움 |
| `onMove(from, to)` | 드래그 결과 — `TabBook.move` 호출 |

높이는 `TabBarHeight = 32.dp` 고정

---

## 드래그 재배치

`pointerInput(book.tabs.size)` 안에서 `awaitFirstDown` → `drag`. `touchSlop` 를 넘긴 뒤부터 `dragOffsetPx` 를 누적

옆 칩의 폭의 절반을 넘기면 그 자리로 swap — `tabBounds` 맵을 함께 swap 해 다음 임계값을 자연스럽게 갱신. 한 번의 드래그에서 여러 칩을 쉽게 통과 가능

```kotlin
private fun swapBounds(bounds: MutableMap<Int, IntRange>, a: Int, b: Int)
```

---

## 휠 스크롤

탭이 화면 폭을 넘기면 `horizontalScroll(scrollState)`. `PointerEventType.Scroll` 을 가로채 `scrollState.scrollBy(deltaY * 60f)` 로 가로 스크롤로 변환 — 트랙패드/마우스 휠 모두 좌우 이동에 매핑

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

`onBoundsChanged` 는 `onGloballyPositioned` 에서 호출 — 드래그 로직이 칩 폭을 알아야 swap 임계값을 잡는다

활성 칩 배경: `colorScheme.background` (에디터 본문 톤과 같음 → "이 칩 = 지금 보고 있는 본문" 시각적 연결)

---

## `CloseButton`

```kotlin
@Composable
private fun CloseButton(dirty: Boolean, onClick: () -> Unit)
```

평소엔 `×`. dirty 인데 *마우스가 칩 위에 없으면* `●` (저장 표시). 호버 시 `×` 로 전환 — 저장 표시와 닫기 버튼이 같은 자리를 공유해 칩 폭이 일정

---

## 사용처

| 위치 | 용도 |
|---|---|
| `page.app.Main` 상단 | 탭 칩 렌더 + 드래그/닫기/활성 콜백 |

---

- [목차로 돌아가기](https://monkshark.github.io/PAGE_IDE/#README.md)
