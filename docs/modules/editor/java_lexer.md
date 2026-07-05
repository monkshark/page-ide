# JavaLexer

> `page/editor/src/main/kotlin/page/editor/JavaLexer.kt` — Java 신택스 렉서 (위임)

`JvmLexer` 에 Java 키워드 셋과 트리플 따옴표 (Text Block) 활성화 옵션만 넘기는 박막 위임. 토큰화 로직 본체는 `JvmLexer` 에 있음

> English: [java_lexer_en.md](https://monkshark.github.io/page-ide/#modules/editor/java_lexer_en.md)

---

## 정의

```kotlin
object JavaLexer : SyntaxLexer by JavaLexerImpl

private object JavaLexerImpl : JvmLexer(
    keywords = JAVA_KEYWORDS,
    supportTripleQuoted = true,
)
```

`SyntaxLexer by JavaLexerImpl` — Kotlin 의 인터페이스 위임. `JavaLexer.tokenize(...)` 호출은 그대로 `JavaLexerImpl.tokenize(...)` 로 전달

`JvmLexerImpl` 을 `private` 으로 가린 이유는 외부에서 직접 인스턴스화할 일이 없기 때문 — 진입점은 `object JavaLexer` 하나

---

## `JAVA_KEYWORDS`

JLS (Java Language Specification) 의 예약어 + 컨텍스트 키워드를 합한 60 여 개 셋

```kotlin
"abstract", "assert", "boolean", "break", "byte", "case", "catch", "char",
"class", "const", "continue", "default", "do", "double", "else", "enum",
"extends", "false", "final", "finally", "float", "for", "goto", "if",
"implements", "import", "instanceof", "int", "interface", "long", "native",
"new", "non-sealed", "null", "package", "permits", "private", "protected",
"public", "record", "return", "sealed", "short", "static", "strictfp",
"super", "switch", "synchronized", "this", "throw", "throws", "transient",
"true", "try", "var", "void", "volatile", "while", "yield",
```

`record`, `sealed`, `non-sealed`, `permits`, `var`, `yield` 같은 비교적 새 키워드 포함 (Java 17 기준)

---

## 사용처

| 위치 | 용도 |
|---|---|
| `SyntaxLexers.forPath(path)` | `.java` 확장자 분기 |

---

- [목차로 돌아가기](https://monkshark.github.io/page-ide/#README_kr.md)
