# SyntaxLexers

> `page/editor/src/main/kotlin/page/editor/SyntaxLexers.kt` — 확장자 기반 렉서 분기

`Path` 를 받아 적절한 `SyntaxLexer` 를 골라준다. 미지원 확장자는 `null` 반환 — 호출자는 본문 톤 그대로 그리기

> English: [syntax_lexers_en.md](https://monkshark.github.io/page-ide/#modules/editor/syntax_lexers_en.md)

---

## `forPath`

```kotlin
fun forPath(path: Path): SyntaxLexer?
```

확장자를 소문자로 정규화한 뒤 매칭

| 확장자 | 렉서 |
|---|---|
| `.kt`, `.kts` | `KotlinLexer` |
| `.java` | `JavaLexer` |
| `.json` | `JsonLexer` |
| 그 외 / 확장자 없음 | `null` |

`when` 문 한 블록만 늘리면 새 언어 추가 가능 — 새 렉서 구현 → `forPath` 분기 한 줄 추가가 끝

---

## 향후 추가

| 확장자 | 렉서 (예정) |
|---|---|
| `.py` | `PythonLexer` |
| `.md` | `MarkdownLexer` (`MarkdownFence` 와 별개로 본문 토큰화) |
| `.xml`, `.html` | `XmlLexer` |
| `.css`, `.scss` | `CssLexer` |

---

## 사용처

| 위치 | 용도 |
|---|---|
| `page.app.Main` (탭 활성화 시) | 활성 탭의 파일 경로로 렉서를 골라 `EditorPanel` 에 전달 |

---

- [목차로 돌아가기](https://monkshark.github.io/page-ide/#README_kr.md)
