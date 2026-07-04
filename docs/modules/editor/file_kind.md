# FileKind / FileKinds

> `page/editor/src/main/kotlin/page/editor/FileKind.kt` — 파일 종류 분류 (확장자 기반)

확장자만 보고 텍스트 / 이미지 / SVG 로 분류. MIME 추론이나 파일 헤더 검사는 안 함

> English: [file_kind_en.md](https://monkshark.github.io/page-ide/#modules/editor/file_kind_en.md)

---

## `FileKind`

```kotlin
enum class FileKind { TEXT, IMAGE, SVG }
```

`PreviewPanel` 에서 분기 키로 사용. 향후 `BINARY`, `PDF`, `MARKDOWN` 등이 늘어나면 `EditorPanel` / `PreviewPanel` 의 분기도 같이 넓혀야 함

---

## `FileKinds.classify`

```kotlin
fun classify(path: Path): FileKind
```

확장자를 소문자로 정규화한 뒤 분류

| 확장자 | 결과 |
|---|---|
| `.svg` | `FileKind.SVG` |
| `.png`, `.jpg`, `.jpeg`, `.gif`, `.bmp`, `.webp` | `FileKind.IMAGE` |
| 그 외 / 확장자 없음 | `FileKind.TEXT` |

확장자가 없는 파일 (`Makefile`, `Dockerfile`, `.gitignore`) 도 모두 `TEXT` 처리. 텍스트 파일이라고 단정해도 `BasicTextField` 가 깨지지 않으면 충분

---

## 사용처

| 위치 | 용도 |
|---|---|
| `page.app.Main` | 활성 탭의 파일 종류로 `EditorPanel` / `PreviewPanel` 분기 |
| `page.app.PreviewPanel` | `IMAGE` / `SVG` 분기로 Skia 디코더 선택 |

---

- [목차로 돌아가기](https://monkshark.github.io/page-ide/#README_kr.md)
