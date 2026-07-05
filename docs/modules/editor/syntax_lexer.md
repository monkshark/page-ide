# SyntaxLexer / Token / TokenKind

> `page/editor/src/main/kotlin/page/editor/SyntaxLexer.kt` — 신택스 렉서 인터페이스

언어별 렉서가 따라야 할 계약. 토큰 종류는 `TokenKind` enum 6 종으로 한정 (`PUNCT` 포함 7 종이지만 실제 색은 6 종만)

> English: [syntax_lexer_en.md](https://monkshark.github.io/page-ide/#modules/editor/syntax_lexer_en.md)

---

## `TokenKind`

```kotlin
enum class TokenKind {
    KEYWORD,
    STRING,
    NUMBER,
    COMMENT,
    ANNOTATION,
    TYPE,
    PUNCT,
}
```

`SyntaxPalette` 의 6 색과 1:1 매핑. `PUNCT` 는 의도적으로 색이 비어 있어 본문 톤 그대로 보임 — 자세한 이유는 `SyntaxColors` 참고

---

## `Token`

```kotlin
data class Token(val kind: TokenKind, val range: IntRange) {
    val start: Int get() = range.first
    val endExclusive: Int get() = range.last + 1
}
```

토큰 한 개 = 종류 + 텍스트 범위. `endExclusive` 는 `AnnotatedString.Builder.addStyle(start, end)` 의 exclusive end 와 맞추기 위한 편의 프로퍼티

---

## `SyntaxLexer`

```kotlin
interface SyntaxLexer {
    fun tokenize(text: String): List<Token>
}
```

파일 한 번 읽고 토큰 리스트 한 번 만드는 단순 계약. 증분 토큰화 / 부분 갱신 같은 최적화는 미지원 — `EditorPanel` 이 `remember(value.text)` 로 캐싱

---

## 구현체

| 구현 | 대상 |
|---|---|
| `KotlinLexer` | `.kt`, `.kts` |
| `JavaLexer` | `.java` |
| `JsonLexer` | `.json` |

분기 진입점은 `SyntaxLexers.forPath(path)` — 미지원 확장자는 `null` 반환 → 토큰 색 없음 (본문 톤만)

---

## 사용처

| 위치 | 용도 |
|---|---|
| `page.app.EditorPanel` `CombinedHighlightTransformation` | `lexer.tokenize(text)` 결과를 `palette[kind]` 색과 함께 `AnnotatedString` 으로 변환 |

---

- [목차로 돌아가기](https://monkshark.github.io/page-ide/#README_kr.md)
