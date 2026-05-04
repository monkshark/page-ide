# GlassTheme

> `page/ui/src/main/kotlin/page/ui/GlassTheme.kt` — Material3 다크 컬러 스킴 + 래퍼 Composable

PAGE 의 다크 팔레트. 메인 윈도우와 모든 다이얼로그가 이 함수로 콘텐츠를 감싸야 같은 색이 적용된다

> English: [glass_theme_en.md](https://monkshark.github.io/PAGE_IDE/#modules/ui/glass_theme_en.md)

---

## 컬러 스킴

```kotlin
private val GlassDark = darkColorScheme(
    primary = Color(0xFF8AB4FF),
    onPrimary = Color(0xFF0A1126),
    secondary = Color(0xFFB8C5E0),
    background = Color(0xFF0D1117),
    onBackground = Color(0xFFE6EDF3),
    surface = Color(0xFF161B22),
    onSurface = Color(0xFFE6EDF3),
    surfaceVariant = Color(0xFF1C2128),
    onSurfaceVariant = Color(0xFF8B949E),
    outline = Color(0xFF30363D),
)
```

| 토큰 | Hex | 용도 |
|---|---|---|
| `primary` | `#8AB4FF` | 캐럿, 활성 매치, 토글 칩 |
| `onPrimary` | `#0A1126` | primary 위 텍스트 |
| `secondary` | `#B8C5E0` | 보조 텍스트 |
| `background` | `#0D1117` | 에디터 캔버스 |
| `onBackground` | `#E6EDF3` | 본문 텍스트 |
| `surface` | `#161B22` | 타이틀바, 탭바, 상태바, 검색바 |
| `onSurface` | `#E6EDF3` | surface 위 텍스트 |
| `surfaceVariant` | `#1C2128` | 사이드바 (파일 트리) |
| `onSurfaceVariant` | `#8B949E` | 보조 라벨, 라인 번호 거터 |
| `outline` | `#30363D` | 리사이즈 핸들 가이드 |

GitHub Dark 톤. 본문 가독성과 신택스 색이 같이 살아있는 명도

---

## `GlassTheme`

```kotlin
@Composable
fun GlassTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = GlassDark, content = content)
}
```

`MaterialTheme` 래핑 한 줄. `colorScheme` 만 덮어쓰고 `Typography` / `Shapes` 는 Material3 기본값. 폰트는 `EditorFontFamily` 가 별도 처리

`Window` 와 `DialogWindow` (`UnsavedChangesDialog`) 양쪽이 이 함수로 감싸야 한다. 누락된 윈도우는 Material3 기본 다크 (회색톤) 로 보임

---

## 향후

| 토큰 | 용도 |
|---|---|
| `GlassLight` | 라이트 컬러 스킴 |
| `Theme(content)` | 다크/라이트 자동 분기 래퍼 |

---

- [목차로 돌아가기](https://monkshark.github.io/PAGE_IDE/#README.md)
