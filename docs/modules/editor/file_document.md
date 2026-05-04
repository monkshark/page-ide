# FileDocument

> `page/editor/src/main/kotlin/page/editor/FileDocument.kt` — 파일 읽기 / 쓰기 (UTF-8)

`Files.readString` / `writeString` 의 얇은 래퍼. 인코딩을 한 곳에 못 박아 두기 위한 단일 진입점

> English: [file_document_en.md](https://monkshark.github.io/PAGE_IDE/#modules/editor/file_document_en.md)

---

## `load`

```kotlin
fun load(path: Path): String
```

UTF-8 로 파일 전체를 읽어 문자열로 반환. `IOException` 은 그대로 던진다 — 호출자가 처리

---

## `loadOrNull`

```kotlin
fun loadOrNull(path: Path): String?
```

`load` 의 try/catch 버전. 실패 시 `null`. 에러 종류를 구분할 필요 없는 곳 (예: 사이드바에서 파일을 미리 열어보기) 에서 사용

---

## `save`

```kotlin
fun save(path: Path, text: String)
```

UTF-8 로 텍스트 전체를 덮어쓴다. 파일이 없으면 새로 생성. 파일 잠금 / 부분 쓰기 / 임시 파일 후 rename 같은 안전 장치는 *없음* — 단순 덮어쓰기

---

## 인코딩 정책

| 항목 | 값 |
|---|---|
| `Charset` | `StandardCharsets.UTF_8` (BOM 미포함) |
| 줄바꿈 | 변환 없음. 파일에 있는 그대로 (Windows CRLF 도 보존) |

향후 사용자 설정에서 인코딩을 바꾸려면 `Charset` 인자를 추가하고 디폴트만 UTF-8 유지

---

## 사용처

| 위치 | 용도 |
|---|---|
| `page.app.Main` (Ctrl+O) | `loadOrNull` 로 파일 열어 탭 추가 |
| `page.app.Main` (Ctrl+S) | `save` 후 `markActiveSaved` |
| `page.app.FileTreePanel` | 파일 클릭 시 `loadOrNull` |

---

- [목차로 돌아가기](https://monkshark.github.io/PAGE_IDE/#README.md)
