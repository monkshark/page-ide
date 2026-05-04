# ui

> `page/ui/` — Glass 디자인 토큰, 폰트, 신택스 팔레트

PAGE 의 시각 베이스. 색·폰트·하이라이팅 색상을 한 곳에서 관리해 모든 패널·다이얼로그가 같은 톤을 공유하도록 만든다. 화면을 그리는 Composable 자체는 `page:app` 에 있고, 이 모듈은 *값* 만 제공

> English: [ui_en.md](https://monkshark.github.io/PAGE_IDE/#modules/ui_en.md)

---

## 의존성

| 종류 | 내용 |
|---|---|
| 외부 | Compose Material3, Compose Foundation |
| 내부 | 없음 |

`page:core` 도 의존하지 않음 — 토큰만 다루는 모듈이라 도메인 타입이 들어올 일이 없다

---

## `GlassTheme`

```kotlin
@Composable
fun GlassTheme(content: @Composable () -> Unit)
```

Material3 다크 컬러 스킴을 PAGE 톤으로 덮어 적용하는 래퍼. 메인 윈도우와 다이얼로그(`UnsavedChangesDialog`) 모두 이걸로 감싼다

### 컬러 팔레트

| 토큰 | hex | 용도 |
|---|---|---|
| `primary` | `#8AB4FF` | 캐럿, 활성 매치, 토글 칩 |
| `onPrimary` | `#0A1126` | primary 위 텍스트 |
| `secondary` | `#B8C5E0` | 보조 텍스트 / 강조 |
| `background` | `#0D1117` | 에디터 본문 배경 |
| `onBackground` | `#E6EDF3` | 본문 텍스트 |
| `surface` | `#161B22` | 타이틀바, 탭바, 상태바, 검색바 |
| `onSurface` | `#E6EDF3` | surface 위 텍스트 |
| `surfaceVariant` | `#1C2128` | 사이드바 (파일 트리) |
| `onSurfaceVariant` | `#8B949E` | 보조 라벨, 라인 번호 거터 |
| `outline` | `#30363D` | 리사이즈 핸들 안내선 |

GitHub Dark 와 비슷한 명도 — 코드 색을 가리지 않으면서도 IDE 톤을 유지한다

---

## `EditorFontFamily`

```kotlin
val EditorFontFamily: FontFamily
```

에디터 본문 / 라인 번호 거터에 쓰이는 모노스페이스 폰트. `fonts/D2Coding.ttf` 가 리소스에 있으면 그걸 쓰고, 없으면 `FontFamily.Monospace` 로 폴백 — 폰트 누락이 빌드 에러로 이어지지 않는다

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

val GlassDarkSyntax: SyntaxPalette
```

`page:editor` 의 `TokenKind` 와 1:1 매핑되는 색 묶음. `EditorPanel` 의 `CombinedHighlightTransformation` 이 이 팔레트를 받아 토큰별 색을 입힌다

| 토큰 | hex | 비고 |
|---|---|---|
| keyword | `#FF7B72` | `class`, `fun`, `val` 등 |
| string | `#A5D6FF` | 문자열 리터럴 |
| number | `#79C0FF` | 정수·실수 리터럴 |
| comment | `#8B949E` | 라인·블록 주석 (회색 톤으로 시야에서 빠지게) |
| annotation | `#D2A8FF` | `@Override`, `@Composable` 등 |
| type | `#FFA657` | 타입 식별자 (대문자로 시작) |

`PUNCT` 는 색을 입히지 않고 본문 색 그대로 — 기호가 너무 많아 색이 들어가면 가독성이 오히려 떨어진다

---

## 향후 추가 예정

| 토큰 | 용도 |
|---|---|
| `GlassLight` | 라이트 테마 컬러 스킴 (Glass 가치: 사용자 환경 존중) |
| `GlassLightSyntax` | 라이트 테마용 신택스 팔레트 |
| `Spacing` / `Radius` | 간격·라운드 토큰 (현재 패널마다 `dp` 직박이) |
| `Typography` | Material3 `Typography` 오버라이드 |

---

- [목차로 돌아가기](https://monkshark.github.io/PAGE_IDE/#README.md)
- [아키텍처](https://monkshark.github.io/PAGE_IDE/#guides/architecture.md)
