# SyntaxPalette

> `shared-core/src/commonMain/kotlin/page/shared/syntax/SyntaxPalette.kt` — 신택스 토큰 색상 번들 · 인스턴스는 `page/ui/.../GlassTokens.kt`

`page:editor` 의 `TokenKind` 와 매핑되는 아홉 개 색 슬롯. 팔레트와 렉서가 분리돼 있어 테마마다 인스턴스만 갈아끼우면 된다. 각 Glass 팔레트가 자기 `SyntaxPalette` 를 들고 있고, 에디터는 활성 테마의 것을 `Glass.colors.syntax` 로 읽는다

> English: [syntax_colors_en.md](https://monkshark.github.io/page-ide/#modules/ui/syntax_colors_en.md)

---

## `SyntaxPalette`

```kotlin
data class SyntaxPalette(
    val keyword: Color,
    val string: Color,
    val number: Color,
    val comment: Color,
    val docComment: Color,
    val todoTag: Color,
    val annotation: Color,
    val type: Color,
    val identifier: Color,
)
```

`PUNCT` 슬롯은 없다. 괄호·세미콜론·콤마는 너무 자주 나와 색을 입히면 본문이 시끄럽다 → `colorFor` 가 `null` 을 돌려주고 본문색 그대로 둔다

---

## Signature palette

<div class="glassdoc">
<style>
.glassdoc .synx{border:1px solid #232B3A;border-radius:12px;overflow:hidden;background:#131823;}
.glassdoc .synx .r{display:flex;align-items:center;gap:12px;padding:11px 15px;border-bottom:1px solid rgba(255,255,255,.06);font-size:13px;color:#E7EAF3;}
.glassdoc .synx .r:last-child{border-bottom:0;}
.glassdoc .synx i{width:15px;height:15px;border-radius:4px;border:1px solid rgba(255,255,255,.1);flex:none;}
.glassdoc .synx .k{width:96px;flex:none;font-family:ui-monospace,Consolas,monospace;}
.glassdoc .synx .h{width:82px;flex:none;color:#8A92A6;font-family:ui-monospace,Consolas,monospace;font-size:12px;font-variant-numeric:tabular-nums;}
.glassdoc .synx .s{color:#8A92A6;font-family:ui-monospace,Consolas,monospace;}
</style>
<div class="synx">
<div class="r"><i style="background:#9DA8FF"></i><span class="k" style="color:#9DA8FF">keyword</span><span class="h">#9DA8FF</span><span class="s"><b style="color:#9DA8FF">class</b> · fun · val · return</span></div>
<div class="r"><i style="background:#4FD3C7"></i><span class="k" style="color:#4FD3C7">string</span><span class="h">#4FD3C7</span><span class="s"><b style="color:#4FD3C7">"hello"</b> · 'a' · """multi"""</span></div>
<div class="r"><i style="background:#C9B6FF"></i><span class="k" style="color:#C9B6FF">number</span><span class="h">#C9B6FF</span><span class="s"><b style="color:#C9B6FF">42</b> · 3.14f · 0xFF · 1e10</span></div>
<div class="r"><i style="background:#828DA8"></i><span class="k" style="color:#828DA8">comment</span><span class="h">#828DA8</span><span class="s"><b style="color:#828DA8">// line</b> · /* block */</span></div>
<div class="r"><i style="background:#6E8FA8"></i><span class="k" style="color:#6E8FA8">docComment</span><span class="h">#6E8FA8</span><span class="s"><b style="color:#6E8FA8">/** kdoc */</b> · @param</span></div>
<div class="r"><i style="background:#F08FC8"></i><span class="k" style="color:#F08FC8">todoTag</span><span class="h">#F08FC8</span><span class="s"><b style="color:#F08FC8">TODO</b> · FIXME · XXX</span></div>
<div class="r"><i style="background:#B79CFF"></i><span class="k" style="color:#B79CFF">annotation</span><span class="h">#B79CFF</span><span class="s"><b style="color:#B79CFF">@Composable</b> · @Override</span></div>
<div class="r"><i style="background:#7FD6E0"></i><span class="k" style="color:#7FD6E0">type</span><span class="h">#7FD6E0</span><span class="s"><b style="color:#7FD6E0">String</b> · MutableList</span></div>
<div class="r"><i style="background:#C8D0E6"></i><span class="k" style="color:#C8D0E6">identifier</span><span class="h">#C8D0E6</span><span class="s"><b style="color:#C8D0E6">count</b> · userName · items</span></div>
</div>
</div>

다른 테마의 신택스는 [Glass Design System](https://monkshark.github.io/page-ide/#modules/ui/glass_theme.md) 팔레트 카드에서 확인. `Cool`·`Frost` 는 GitHub 계열, `Graphite` 는 Darcula 계열, `Warm`·`Sand` 는 세피아 계열로 각자 매칭된다

---

## 사용처

렉서(`KotlinLexer`·`JavaLexer`·`JsonLexer`)가 뱉는 `TokenKind` 를 `EditorPanel.colorFor(kind, palette)` 가 색으로 변환한다

```kotlin
private fun colorFor(kind: TokenKind, palette: SyntaxPalette) = when (kind) {
    TokenKind.KEYWORD -> palette.keyword
    TokenKind.STRING -> palette.string
    TokenKind.NUMBER -> palette.number
    TokenKind.COMMENT -> palette.comment
    TokenKind.DOC_COMMENT -> palette.docComment
    TokenKind.TODO_TAG -> palette.todoTag
    TokenKind.ANNOTATION -> palette.annotation
    TokenKind.TYPE -> palette.type
    TokenKind.IDENTIFIER -> palette.identifier
    TokenKind.PUNCT -> null
}
```

`palette` 는 `Glass.colors.syntax` — 활성 테마를 바꾸면 신택스도 함께 바뀐다. 색이 없는 `PUNCT` 는 본문색(`text`)으로 렌더된다

---

- [목차로 돌아가기](https://monkshark.github.io/page-ide/#README_kr.md)
