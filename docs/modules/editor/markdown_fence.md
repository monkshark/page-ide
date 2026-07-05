# MarkdownFence

> `page/editor/src/main/kotlin/page/editor/MarkdownFence.kt` — 캐럿이 코드 펜스 안에 있는지 판별

Markdown 의 ```` ``` ```` / `~~~` 코드 펜스 안에서는 `AutoClose` / `Indent` 같은 자동 동작을 멈춰야 깔끔하다. 그 판별을 위한 단일 함수

> English: [markdown_fence_en.md](https://monkshark.github.io/page-ide/#modules/editor/markdown_fence_en.md)

---

## `isInsideFence`

```kotlin
fun isInsideFence(text: String, caret: Int): Boolean
```

텍스트 처음부터 한 줄씩 진행하며 펜스 상태를 추적. 캐럿이 위치한 줄에 도달했을 때의 상태를 반환

| 케이스 | 반환 |
|---|---|
| 펜스 밖에서 캐럿 만남 | `false` |
| 펜스 안에서 캐럿 만남 (열기/닫기 라인 자체는 제외) | `true` |
| 펜스가 닫히지 않은 채 EOF | `inFence` 의 마지막 값 |

펜스 경계 라인 (열기 / 닫기 글자가 있는 줄) 에 캐럿이 있으면 `false` — 마크다운 의미상 그 라인은 펜스 바깥 으로 친다

---

## `parseFenceLine`

```kotlin
private fun parseFenceLine(text: String, lineStart: Int, lineEnd: Int): Pair<Char, Int>?
```

CommonMark 펜스 규칙을 따른다

| 규칙 | 값 |
|---|---|
| 선행 공백 | 최대 3 칸 |
| 펜스 글자 | `` ` `` 또는 `~` |
| 펜스 길이 | 3 개 이상 연속 |

여는 펜스의 글자 종류 와 길이 를 기억해 두고, 닫는 펜스는 같은 글자 + 같거나 더 긴 길이여야 인정

---

## 비목표

- 인라인 코드 (\`...\`) 판별 — 한 줄 안의 백틱 블록은 처리 안 함
- 들여쓰기 코드 블록 (4 칸 이상 들여쓰기) — 펜스만 인식

---

## 사용처

| 위치 | 용도 |
|---|---|
| `page.app.EditorPanel` (`.md` 파일) | `AutoClose` / `Indent` 자동 동작 비활성화 가드 |

> 현재 직접 호출 지점은 향후 `EditorPanel` 에서 추가 예정 — 모듈 자체는 준비 완료

---

- [목차로 돌아가기](https://monkshark.github.io/page-ide/#README_kr.md)
