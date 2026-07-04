# JsonLexer

> `page/editor/src/main/kotlin/page/editor/JsonLexer.kt` — JSON 신택스 렉서 (독립)

JSON 은 키워드 / 식별자 / 어노테이션이 없어 `JvmLexer` 를 공유할 만한 공통점이 적다 → 독립 구현

> English: [json_lexer_en.md](https://monkshark.github.io/page-ide/#modules/editor/json_lexer_en.md)

---

## `tokenize`

```kotlin
override fun tokenize(text: String): List<Token>
```

한 글자씩 스캔하며 다음 종류만 토큰으로 만든다

| 토큰 | 인식 규칙 |
|---|---|
| `STRING` | `"` 시작, `\` 이스케이프 한 글자 스킵, `"` 종료. 줄바꿈 만나면 종료 (잘못된 JSON 도 그래도 칠해야 보기 편함) |
| `NUMBER` | 선택적 `-`, 정수, 선택적 `.소수`, 선택적 `e/E[+−]지수` |
| `KEYWORD` | `true`, `false`, `null` (정확히 이 셋만) |

`,`, `:`, `{`, `}`, `[`, `]` 같은 punctuation 은 일부러 토큰화 안 함 → `PUNCT` 색이 없으므로 본문 톤 그대로

---

## 비목표

- 스키마 검증 — 잘못된 JSON 도 그대로 그려준다 (편집 중에는 항상 invalid 상태일 수 있음)
- JSON5 / JSONC — `//`, `/* */`, trailing comma 같은 확장은 미지원. 필요해지면 별도 렉서

---

## 사용처

| 위치 | 용도 |
|---|---|
| `SyntaxLexers.forPath(path)` | `.json` 확장자 분기 |

---

- [목차로 돌아가기](https://monkshark.github.io/page-ide/#README_kr.md)
