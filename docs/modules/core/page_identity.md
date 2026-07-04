# PageIdentity

> `page/core/src/main/kotlin/page/core/Identity.kt` — 앱 식별자 상수

`page:core` 의 유일한 파일. 모든 모듈이 의존하는 루트라 도메인 로직은 들어오지 않는다

> English: [page_identity_en.md](https://monkshark.github.io/page-ide/#modules/core/page_identity_en.md)

---

## 정의

```kotlin
object PageIdentity {
    const val NAME = "PAGE"
    const val ACRONYM = "Pair · Atlas · Glass · Echo"
    const val VERSION = "0.1.0"
}
```

`object` + `const val`. JVM 상수로 인라인되어 런타임 비용 없음

---

## 필드

| 필드 | 값 | 용도 |
|---|---|---|
| `NAME` | `"PAGE"` | 윈도우 타이틀, 다이얼로그, About |
| `ACRONYM` | `"Pair · Atlas · Glass · Echo"` | 타이틀바 보조 라벨 |
| `VERSION` | `"0.1.0"` | 타이틀바 버전, 업데이트 체크 |

`VERSION` 은 시맨틱 버저닝 (`MAJOR.MINOR.PATCH`). 단계 (Pair / Atlas / Glass / Echo) 가 한 번씩 끝날 때 `MINOR` 가 올라간다

---

## 사용처

| 위치 | 용도 |
|---|---|
| `page.app.Main.TitleBar` | `"${NAME} · v${VERSION}"` 형식으로 윈도우 타이틀 출력 |
| `Window(title = ...)` | OS 윈도우 매니저에 보이는 타이틀 |

---

- [목차로 돌아가기](https://monkshark.github.io/page-ide/#README_kr.md)
