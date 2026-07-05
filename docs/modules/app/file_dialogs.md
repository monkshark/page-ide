# FileDialogs

> `page/app/src/main/kotlin/page/app/FileDialogs.kt` — `JFileChooser` 래퍼

Swing 의 `JFileChooser` 를 그대로 띄우되, 결과를 `Path?` 로 통일해 호출부가 `null` 만 보고 취소를 판정하도록 만든다

> English: [file_dialogs_en.md](https://monkshark.github.io/page-ide/#modules/app/file_dialogs_en.md)

---

## `open`

```kotlin
fun open(parent: Frame): Path?
```

파일 열기 다이얼로그. `FILES_ONLY` 모드. 사용자가 Cancel 을 누르면 `null`

| 호출부 | 시점 |
|---|---|
| `Ctrl+O` | 단일 파일 열기 |

---

## `saveAs`

```kotlin
fun saveAs(parent: Frame, suggested: String? = null): Path?
```

저장 다이얼로그. `suggested` 가 있으면 기본 파일명으로 채운다 (e.g., `Untitled.kt`)

| 호출부 | 시점 |
|---|---|
| `Ctrl+S` (탭 경로가 가상 untitled 일 때) | 새 경로로 저장 |

---

## `openDirectory`

```kotlin
fun openDirectory(parent: Frame): Path?
```

폴더 열기 다이얼로그. `DIRECTORIES_ONLY` 모드 — 사이드바 트리의 루트로 사용

| 호출부 | 시점 |
|---|---|
| `Ctrl+Shift+O` | 프로젝트 폴더 열기 |

---

## 왜 Compose 가 아니라 Swing 인가

Compose Desktop 자체가 Swing 위에 얹혀 있고, JVM 표준 파일 다이얼로그가 OS 네이티브 룩에 가장 가깝다. Compose 로 직접 그리면 OS 별 파일 시스템 디테일을 다시 구현해야 함

---

- [목차로 돌아가기](https://monkshark.github.io/page-ide/#README_kr.md)
