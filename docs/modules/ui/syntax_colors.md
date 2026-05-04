# SyntaxPalette / GlassDarkSyntax

> `page/ui/src/main/kotlin/page/ui/SyntaxColors.kt` — 신택스 토큰 색상 번들

`page:editor` 의 `TokenKind` 와 1:1 로 매핑. 팔레트와 렉서가 분리돼 있어 다른 테마는 인스턴스만 갈아끼우면 끝

> English: [syntax_colors_en.md](https://monkshark.github.io/PAGE_IDE/#modules/ui/syntax_colors_en.md)

---

## `SyntaxPalette`

```kotlin
data class SyntaxPalette(
    val keyword: Color,
    val string: Color,
    val number: Color,
    val comment: Color,
    val annotation: Color,
    val type: Color,
)
```

`data class` — 동등성/`copy` 자동. `PUNCT` 는 의도적으로 빠져 있음 (괄호·세미콜론·콤마는 너무 자주 나와 색을 입히면 본문이 시끄럽다 → `onBackground` 그대로)

---

## `GlassDarkSyntax`

```kotlin
val GlassDarkSyntax = SyntaxPalette(
    keyword = Color(0xFFFF7B72),
    string = Color(0xFFA5D6FF),
    number = Color(0xFF79C0FF),
    comment = Color(0xFF8B949E),
    annotation = Color(0xFFD2A8FF),
    type = Color(0xFFFFA657),
)
```

| 토큰 | Hex | 예시 |
|---|---|---|
| keyword | `#FF7B72` | `class`, `fun`, `val`, `if`, `return` |
| string | `#A5D6FF` | `"hello"`, `'a'`, `"""multi"""` |
| number | `#79C0FF` | `42`, `3.14f`, `0xFF`, `1e10` |
| comment | `#8B949E` | `// line`, `/* block */` |
| annotation | `#D2A8FF` | `@Composable`, `@Override` |
| type | `#FFA657` | `String`, `MutableList` (대문자 시작 식별자) |

GitHub Dark 와 같은 색감

---

## 사용처

| 위치 | 용도 |
|---|---|
| `page.app.EditorPanel` 의 `CombinedHighlightTransformation` | `Token.kind` → `palette[kind]` 매핑으로 `AnnotatedString` 의 `SpanStyle` 생성 |

---

## 향후

| 토큰 | 용도 |
|---|---|
| `GlassLightSyntax` | 라이트 테마 |
| 사용자 커스텀 팔레트 | 설정 화면 컬러 피커 |

---

- [목차로 돌아가기](https://monkshark.github.io/PAGE_IDE/#README.md)
