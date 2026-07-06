# Shared Core

> `shared-core` — 데스크톱 IDE와 wasm 문서 뷰어가 함께 쓰는 멀티플랫폼 기반 코드

PAGE는 두 곳에서 같은 로직이 필요하다. JVM 데스크톱 IDE와, 브라우저에서 도는 wasm 문서 뷰어다. 마크다운을 파싱하고, 그래프를 다루고, 경로를 계산하는 코드를 양쪽에 두 번 짜지 않으려고, 순수 Kotlin과 Compose로만 된 부분을 `shared-core` 로 뽑았다. 이 모듈은 `jvm` 과 `wasmJs` 두 타깃으로 컴파일되고, `java.*` 를 전혀 쓰지 않는다.

> English: [main_en.md](https://monkshark.github.io/page-ide/#modules/shared-core/main_en.md)

---

## 구성

| 패키지 | 역할 |
|---|---|
| `md` | 마크다운 파서 (`MdParser` → `MdNode` 트리) |
| `json` | 의존성 없는 소형 JSON 파서 (`Json`) |
| `syntax` | 렉서 인터페이스와 Kotlin·Java·JSON 렉서 |
| `graph` | 슬림 그래프 모델·질의·스냅샷 코덱 |
| `widget` | Compose 그래프 캔버스 (`GraphCanvas`) |
| `path` | 문자열 기반 경로 (`FilePath`, `java.nio.Path` 대체) |
| `docs` | 문서 라우팅·인덱스·언어 변형 |

---

## md — 마크다운 파서

`MdParser.parse(src)` 는 마크다운을 `MdNode` 트리로 바꾼다. 헤딩·문단·코드 펜스·목록·태스크 목록·표·인용·콜아웃·인라인(링크·강조·코드 스팬)을 다룬다. 헤딩에는 슬러그가 붙어 목차와 앵커에 쓰인다.

특별한 노드가 하나 있다. 펜스 언어가 `page-widget` 이면 파서는 `WidgetRef(name, args)` 를 낸다. 문서 뷰어는 이걸 만나면 산문 대신 Compose 위젯 섬을 그 자리에 마운트한다.

---

## json — 소형 파서

`Json.parse(text)` 는 텍스트를 `JsonValue`(`JsonObject`·`JsonArray`·`JsonString`·`JsonNumber`·`JsonBool`·`JsonNull`) 로 파싱한다. `asString()`·`asArray()`·`asObject()` 확장으로 값을 꺼낸다. 외부 라이브러리 의존이 없어 wasm 타깃에서도 그대로 돈다 — 스냅샷 JSON과 문서 인덱스를 읽는 데 쓴다.

---

## syntax — 렉서

`SyntaxLexer.tokenize(text)` 는 `Token`(`TokenKind` + 범위) 목록을 낸다. `CodeLexers.forLang(lang)` 이 언어 이름을 렉서로 라우팅한다.

| 언어 | 렉서 |
|---|---|
| `kotlin` · `kt` · `kts` | `KotlinLexer` |
| `java` | `JavaLexer` |
| `json` | `JsonLexer` |

`SyntaxHighlight` 와 `SyntaxPalette` 가 토큰을 색으로 옮긴다. 문서 뷰어 코드 블록 하이라이팅이 이 층 위에 선다.

---

## graph — 모델과 질의

`GraphNode`·`GraphEdge`·`GraphSlice` 가 슬림 그래프를 이룬다. `GraphInsights` 는 한 노드의 이웃(`neighborhood`), 진입 차수(`indegrees`), 순환(`cycles`, 강결합 요소 기반)을 계산한다. `GraphSnapshot.parse(json)` 은 내보낸 JSON 스냅샷을 다시 `GraphSlice` 로 읽어들인다.

---

## widget — 그래프 캔버스

`GraphCanvas` 는 `GraphSlice` 를 받아 노드·엣지를 그리는 Compose 컴포저블이다. 노드에 마우스를 올리면 이웃만 남고, 휠로 확대·드래그로 이동한다. 색은 `GraphColors` 로 주입받아 테마와 무관하게 동작한다.

---

## path — FilePath

`FilePath` 는 문자열 하나를 감싼 경로 값이다. `parent`·`fileName`·`segments`·`startsWith`·`relativize`·`resolve` 를 제공하고, 입력은 `/` 로 정규화한다. `java.nio.Path` 가 없는 wasm 에서도 같은 계층 연산을 하려고 만든 대체물로, Atlas의 그래프 모델이 이 위에 선다.

---

## docs — 라우팅

문서 뷰어의 경로 규칙이 여기 모인다. `parseDocIndex` 는 문서 인덱스 JSON을 읽고, `parseDocHash`·`buildDocHash` 는 `path#heading` 형태의 URL 해시를 오간다. `DocLang` 은 `_en` 접미어로 한국어·영어 변형을 짝짓고, `buildDocTree` 는 평면 경로 목록을 사이드바 트리로 접는다.

---

- [목차로 돌아가기](https://monkshark.github.io/page-ide/#README_kr.md)
