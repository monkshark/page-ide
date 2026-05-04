# core

> `page/core/` — 공용 도메인 타입과 상수

모듈 간 직접 의존을 차단하기 위한 **공통 베이스**. 다른 모든 모듈은 `page:core` 만 의존하거나 아예 의존하지 않는다 (아키텍처 의존 방향 참조)

> English: [core_en.md](https://monkshark.github.io/PAGE_IDE/#modules/core_en.md)

---

## 의존성

| 종류 | 내용 |
|---|---|
| 외부 | 없음 (Kotlin stdlib only) |
| 내부 | 없음 |

UI / Compose / IO 의존 없음. `core` 가 다른 모듈을 의존하기 시작하면 그 즉시 의존 사이클이 생기므로 절대 금지

---

## `PageIdentity`

```kotlin
object PageIdentity {
    const val NAME = "PAGE"
    const val ACRONYM = "Pair · Atlas · Glass · Echo"
    const val VERSION = "0.1.0"
}
```

앱 이름·버전·약어. 윈도우 타이틀바 (`page:app` 의 `TitleBar`) 와 About 다이얼로그(예정) 에서 단일 출처로 참조한다. 버전 문자열은 빌드 스크립트가 아니라 코드에 박혀 있다 — 단계적 릴리스 기준은 [Architecture](https://monkshark.github.io/PAGE_IDE/#guides/architecture.md) 참조

---

## 향후 추가 예정

| 타입 | 용도 |
|---|---|
| `EventBus` | 모듈 간 단방향 통신 (`editor` ↔ `pair` 직접 의존 회피) |
| `WorkspaceEvent` / `EditorEvent` 등 | 이벤트 페이로드 sealed 인터페이스 |
| `Identity<T>` | 모듈 경계용 타입 안전 식별자 래퍼 |

코드가 들어오면 채운다

---

- [목차로 돌아가기](https://monkshark.github.io/PAGE_IDE/#README.md)
- [아키텍처](https://monkshark.github.io/PAGE_IDE/#guides/architecture.md)
