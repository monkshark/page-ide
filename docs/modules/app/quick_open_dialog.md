# QuickOpenDialog

> `page/app/src/main/kotlin/page/app/QuickOpenDialog.kt` — `Ctrl+P` 빠른 열기 다이얼로그

VS Code 류 "go to file". 사이드바를 펼치지 않고 파일명 한 번 입력으로 탭에 연다

> English: [quick_open_dialog_en.md](https://monkshark.github.io/page-ide/#modules/app/quick_open_dialog_en.md)

---

## 시그니처

```kotlin
@Composable
internal fun QuickOpenDialog(
    files: List<IndexedFile>,
    onPick: (IndexedFile) -> Unit,
    onDismiss: () -> Unit,
)
```

`files` 는 `Main` 이 `Ctrl+P` 누르는 순간 `ProjectFileIndex.walk(rootDir)` 로 한 번 만들어 넘긴 인덱스. 다이얼로그 자체는 디스크에 안 닿는다 — 매칭 + 그림만

---

## 키맵

| 키 | 동작 |
|---|---|
| 글자 입력 | `query` 갱신 → `QuickOpen.rank(query, files)` 재계산, `selected` 0 으로 |
| `↓` / `↑` | `selected` 이동, 리스트 자동 스크롤 |
| `Enter` | `selected` 위치의 `file` 로 `onPick` 호출 후 닫힘 |
| `Esc` | `onDismiss` 호출 후 닫힘 |
| 행 클릭 | `onPick` 호출 후 닫힘 |

`onPreviewKeyEvent` 가 `KeyDown` 만 잡아서 `Enter` 가 두 번 발사되는 문제 방지

---

## 레이아웃

```
┌─ Surface (640×420, undecorated) ──────────────┐
│ ┌─ QueryInput (32dp, BasicTextField) ──────┐ │
│ └──────────────────────────────────────────┘ │
│   spacer 8dp                                  │
│ ┌─ ResultList (LazyColumn) ────────────────┐ │
│ │  Foo.kt           src/                    │ │ ← selected 행은 primary @ 18% bg
│ │  Foo_en.md        docs/                   │ │
│ │  ...                                      │ │
│ └──────────────────────────────────────────┘ │
└───────────────────────────────────────────────┘
```

`undecorated = true` + `Surface(border)` — `UnsavedChangesDialog` 와 같은 패턴이라 글래스 테마와 톤이 맞는다

---

## 매치 강조

```kotlin
private fun highlightedName(
    text: String,
    indices: IntArray,
    base: Color,
    highlight: Color,
): AnnotatedString
```

`QuickOpenResult.nameIndices` 가 가리키는 글자만 `primary` 색 + `Bold` 로 그린다. 부모 경로 (`parent` = `relative.dropLast(name.length).trimEnd('/')`) 는 흐릿한 `onSurfaceVariant` 로 그대로 표기 — 매치 강조 없음 (지금은 이름 매치만 충분히 잘 잡혀서)

---

## 빈 결과

`rank` 가 빈 리스트를 돌려주면 "결과 없음" 만 가운데 표시. `Enter` 를 눌러도 `getOrNull(0)` 이 null 이라 아무 일 안 일어남

---

## 사용처

| 위치 | 트리거 |
|---|---|
| `page.app.Main` `openQuickOpen` | `Ctrl+P` (`rootDir != null` 일 때만) |

폴더 안 열린 상태 (`rootDir == null`) 에서는 다이얼로그가 뜨지 않는다 — 인덱싱 대상이 없기 때문

---

- [목차로 돌아가기](https://monkshark.github.io/page-ide/#README_kr.md)
