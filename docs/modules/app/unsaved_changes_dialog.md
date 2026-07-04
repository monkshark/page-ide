# UnsavedChangesDialog

> `page/app/src/main/kotlin/page/app/UnsavedChangesDialog.kt` — 저장 확인 다이얼로그

dirty 한 탭을 닫거나 앱을 종료할 때, 저장/버림/취소를 묻는 모달. 탭 단위와 앱 종료 단위가 같은 다이얼로그를 공유한다

> English: [unsaved_changes_dialog_en.md](https://monkshark.github.io/page-ide/#modules/app/unsaved_changes_dialog_en.md)

---

## 시그니처

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

| 파라미터 | 의미 |
|---|---|
| `fileNames` | 저장 안 된 파일 이름 리스트 — 본문에 불릿으로 나열 |
| `isAppExit` | `true` 면 "저장하지 않고 종료하시겠습니까?", `false` 면 "저장하시겠습니까?" |
| `onSave` / `onDiscard` / `onCancel` | 세 버튼 콜백 — 호출자 (`Main`) 가 실제 닫기/종료를 처리 |

---

## 키보드

| 키 | 동작 |
|---|---|
| `Esc` | `onCancel` |
| `Y` | `onSave` |
| `N` | `onDiscard` |

`onPreviewKeyEvent` 에서 가로채므로 다이얼로그 내부 입력 위젯이 없어도 동작

---

## 모양

```kotlin
DialogWindow(undecorated = true, resizable = false, ...)
```

- `460 × 220 dp` 고정 크기, 타이틀바 없음
- 상단 20dp 영역만 `WindowDraggableArea` 로 처리해 드래그 이동 가능
- `GlassTheme` 안에서 그려져 메인 윈도우와 톤 일치

`FileList` 는 surface 톤 박스에 `• 파일명` 들을 줄지어 보여줌 — 여러 파일이 한 번에 dirty 일 때 (앱 종료 케이스) 가독성 확보

---

## 버튼

순서는 `[취소] [저장 안 함] [저장]` — `저장` 만 primary

`Enter` 가 기본 액션이 아닌 이유: 본문 입력 위젯이 없으므로 `Enter` 가 어디로 갈지 모호. 명시 키 (`Y/N/Esc`) 만 받음

---

## 사용처

| 위치 | 용도 |
|---|---|
| `page.app.Main` `pendingClose != null` 일 때 표시 | dirty 탭/앱 종료 확인 |

---

- [목차로 돌아가기](https://monkshark.github.io/page-ide/#README_kr.md)
