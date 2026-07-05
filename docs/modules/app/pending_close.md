# PendingClose

> `page/app/src/main/kotlin/page/app/PendingClose.kt` — 저장 안 된 닫기 요청 식별

`UnsavedChangesDialog` 가 어떤 닫기 요청을 처리 중인지를 구분하기 위한 sealed interface

> English: [pending_close_en.md](https://monkshark.github.io/page-ide/#modules/app/pending_close_en.md)

---

## 정의

```kotlin
internal sealed interface PendingClose {
    data class Tab(val index: Int) : PendingClose
    data object App : PendingClose
}
```

| 케이스 | 의미 |
|---|---|
| `Tab(index)` | 탭 하나만 닫는 요청 — `Ctrl+W` 또는 탭 칩의 `×` |
| `App` | 앱 전체 종료 요청 — 창 닫기 버튼 |

---

## 왜 sealed interface 인가

다이얼로그 콜백이 두 케이스를 다르게 다룬다 — 탭 닫기는 `book.close(index)` 만, 앱 종료는 `exitApplication()` 까지 연쇄. 한 곳에서 분기하므로 enum + payload 보다 sealed 가 깔끔

---

## 사용처

| 위치 | 용도 |
|---|---|
| `page.app.Main` `pendingClose: PendingClose?` | 다이얼로그 표시 트리거 |
| `page.app.UnsavedChangesDialog` `isAppExit` | `pendingClose is PendingClose.App` 으로 메시지 분기 |

---

- [목차로 돌아가기](https://monkshark.github.io/page-ide/#README_kr.md)
