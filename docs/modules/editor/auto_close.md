# AutoClose / TextEdit

> `page/editor/src/main/kotlin/page/editor/AutoClose.kt` — 괄호·따옴표 자동 닫기 + 편집 단위

`onValueChange` 단계에서 호출되는 순수 함수. 직전 상태 (`old`) 와 직후 상태 (`new`) 를 받아 변환된 새 상태를 돌려준다

> English: [auto_close_en.md](https://monkshark.github.io/page-ide/#modules/editor/auto_close_en.md)

---

## `TextEdit`

```kotlin
data class TextEdit(
    val text: String,
    val selectionStart: Int,
    val selectionEnd: Int = selectionStart,
) {
    constructor(text: String, caret: Int) : this(text, caret, caret)
    val caret: Int get() = selectionStart
}
```

편집 한 단위. `selectionStart == selectionEnd` 면 캐럿만 있고 선택 범위는 없는 상태. `caret` 은 `selectionStart` 의 별칭

---

## `AutoClose.apply`

```kotlin
fun apply(old: TextEdit, new: TextEdit): TextEdit
```

세 가지 변환을 순서대로 시도한다. 모두 해당 안 되면 `new` 를 그대로 반환

| 분기 | 조건 | 동작 |
|---|---|---|
| 선택 감싸기 | `old` 가 선택 범위, `new` 는 그 자리에 한 글자 삽입 | 선택을 닫는 짝과 함께 감싸기 (`(text)`, `"text"` 등) |
| 짝 동시 삭제 | 캐럿 앞뒤가 `(`/`)` 같은 짝, 백스페이스로 여는 글자만 지움 | 닫는 짝까지 같이 제거해 빈 짝이 남지 않게 |
| 닫는 짝 자동 삽입 | 한 글자 삽입, 그 글자가 여는 짝 | 다음 위치에 닫는 짝 자동 추가 후 캐럿은 사이에 |

---

## 짝 정의

```kotlin
private val pairs = mapOf(
    '(' to ')',
    '[' to ']',
    '{' to '}',
    '"' to '"',
    '\'' to '\'',
)
```

추가 짝 (예: 마크다운 ``` 펜스, 한국어 따옴표) 이 필요해지면 이 맵에 추가하고 가드만 더 보강

---

## 가드

| 가드 | 동작 |
|---|---|
| 캐럿 다음이 영숫자/`_` | 자동 닫기 안 함 (식별자 한가운데 따옴표를 칠 때 `myVar"foo"` 가 깨지지 않게) |
| 따옴표 직전이 영숫자/`_` | 자동 닫기 안 함 (`don't`, `it's` 같은 평문 처리) |
| 닫는 짝을 입력했는데 캐럿 위치가 같은 닫는 짝 | 새로 삽입하지 않고 캐럿만 한 칸 이동 (오버타이핑) |

---

## 사용처

| 위치 | 용도 |
|---|---|
| `page.app.EditorPanel` | `BasicTextField.onValueChange` 의 첫 번째 변환 단계 |

---

- [목차로 돌아가기](https://monkshark.github.io/page-ide/#README_kr.md)
