# EditorFontFamily

> `page/ui/src/main/kotlin/page/ui/EditorFonts.kt` — 에디터 본문 폰트 (D2Coding fallback Monospace)

리소스에 `D2Coding.ttf` 가 있으면 쓰고, 없으면 `FontFamily.Monospace`. 폰트가 빠져도 빌드는 깨지지 않음

> English: [editor_fonts_en.md](https://monkshark.github.io/PAGE_IDE/#modules/ui/editor_fonts_en.md)

---

## 정의

```kotlin
private const val PRIMARY_FONT_RESOURCE = "fonts/D2Coding.ttf"

val EditorFontFamily: FontFamily = run {
    val cl = Thread.currentThread().contextClassLoader
        ?: object {}.javaClass.classLoader
    if (cl?.getResource(PRIMARY_FONT_RESOURCE) != null) {
        FontFamily(Font(resource = PRIMARY_FONT_RESOURCE))
    } else {
        FontFamily.Monospace
    }
}
```

`run { ... }` 으로 톱레벨 `val` 에 한 번만 평가. 클래스로더 조회는 부팅 시 단 한 번

| 조건 | 결과 |
|---|---|
| `fonts/D2Coding.ttf` 가 클래스패스에 있음 | D2Coding 사용 |
| 리소스 없음 | `FontFamily.Monospace` (JDK 기본 모노스페이스) |

`Thread.currentThread().contextClassLoader` 가 `null` 인 환경 (일부 IDE 테스트 러너) 대비로 `object {}.javaClass.classLoader` 폴백. 이 한 줄이 없으면 NPE 로 앱이 시작 못 함

---

## 사용처

| 위치 | 용도 |
|---|---|
| `page.app.EditorPanel` | `BasicTextField` 의 `textStyle.fontFamily` |
| `page.app.LineNumberGutter` | 라인 번호 텍스트 — 본문과 같은 폰트로 줄 높이가 정확히 맞아야 거터가 흔들리지 않음 |

---

- [목차로 돌아가기](https://monkshark.github.io/PAGE_IDE/#README.md)
