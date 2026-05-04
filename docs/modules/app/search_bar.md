# SearchBar

> `page/app/src/main/kotlin/page/app/SearchBar.kt` — 에디터 상단 검색/치환 바

`SearchState` 를 받아 검색어 입력, 매치 카운트, 케이스 토글, 이전/다음, 치환 입력 / 한 개 / 전부 치환을 한 줄(또는 두 줄) 로 그린다

> English: [search_bar_en.md](https://monkshark.github.io/PAGE_IDE/#modules/app/search_bar_en.md)

---

## 시그니처

```kotlin
@Composable
fun SearchBar(
    state: SearchState,
    onQueryChange: (String) -> Unit,
    onReplaceChange: (String) -> Unit,
    onToggleCase: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onReplace: () -> Unit,
    onReplaceAll: () -> Unit,
    onClose: () -> Unit,
    onWindowShortcut: (KeyEvent) -> Boolean,
    modifier: Modifier = Modifier,
)
```

| 파라미터 | 의미 |
|---|---|
| `state` | 현재 검색 상태 — query / replace / matches / activeMatchIndex / replaceVisible |
| `onWindowShortcut` | 검색 바가 처리하지 않은 키를 위 윈도우로 위임 — `Ctrl+S` 같은 글로벌 단축키 보존 |

---

## 레이아웃

| 줄 | 구성 |
|---|---|
| 1줄 | 검색 입력 · `n / total` 카운터 · `Aa` 케이스 토글 · `<` 이전 · `>` 다음 · `×` 닫기 |
| 2줄 (옵션) | 치환 입력 · `바꾸기` · `전부 바꾸기` |

`state.replaceVisible` 가 `true` 일 때만 2줄 표시 — `Ctrl+R` 로 토글

---

## 포커스 자동 이동

```kotlin
LaunchedEffect(state.replaceVisible) {
    if (state.replaceVisible) replaceFocus.requestFocus() else queryFocus.requestFocus()
}
```

치환 줄을 켜면 자동으로 치환 입력에 포커스, 끄면 검색 입력으로 돌아감

---

## 키 처리

| 입력 위젯 | 키 | 동작 |
|---|---|---|
| 검색 | `Enter` | `onNext` |
| 검색 | `Shift+Enter` | `onPrev` |
| 검색 | `Esc` | `onClose` |
| 치환 | `Enter` | `onReplace` |
| 치환 | `Esc` | `onClose` |
| 둘 다 | 그 외 | `onWindowShortcut(e)` 로 윈도우 키핸들러에 위임 |

`Ctrl+S` 같이 검색 바와 무관한 단축키는 위로 흘려보내야 하므로 `onWindowShortcut` 위임이 필수

---

## 보조 컴포넌트

| 이름 | 역할 |
|---|---|
| `InputBox` | 입력 필드의 박스/보더 (`220 × 24 dp`) |
| `CounterLabel` | `state.query.isEmpty()` 이면 빈 문자열, 매치 0이면 `0 matches`, 그 외 `n / total` |
| `ToggleChip` | `Aa` 케이스 토글 (`active` 면 primary 톤) |
| `IconChip` | `<` `>` `×` (단문자 + hover bg) |
| `TextChip` | `바꾸기` / `전부 바꾸기` (텍스트 라벨 + hover bg, `enabled = matches.isNotEmpty()`) |

---

## 사용처

| 위치 | 용도 |
|---|---|
| `page.app.Main` `search != null` 일 때 에디터 상단에 표시 | 검색/치환 입력 |

---

- [목차로 돌아가기](https://monkshark.github.io/PAGE_IDE/#README.md)
