# SyntaxPalette

> `shared-core/src/commonMain/kotlin/page/shared/syntax/SyntaxPalette.kt` — Syntax token color bundle · instances in `page/ui/.../GlassTokens.kt`

Nine color slots mapped to `TokenKind` in `page:editor`. Palette and lexer are decoupled, so each theme just swaps its instance. Every Glass palette carries its own `SyntaxPalette`, and the editor reads the active theme's via `Glass.colors.syntax`.

> 한국어: [syntax_colors.md](https://monkshark.github.io/page-ide/#modules/ui/syntax_colors.md)

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

There is no `PUNCT` slot. Parens, semicolons, and commas are too frequent — coloring them adds noise, so `colorFor` returns `null` and leaves them in the body color.

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

The other themes' syntax colors are on the palette cards in [Glass Design System](https://monkshark.github.io/page-ide/#modules/ui/glass_theme_en.md). `Cool` and `Frost` follow GitHub tones, `Graphite` follows Darcula, and `Warm` and `Sand` match a sepia mood.

---

## Usage

The lexers (`KotlinLexer`, `JavaLexer`, `JsonLexer`) emit `TokenKind`, which `EditorPanel.colorFor(kind, palette)` turns into color.

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

`palette` is `Glass.colors.syntax` — switch the active theme and the syntax colors follow. The uncolored `PUNCT` renders in the body color (`text`).

---

- [Back to contents](https://monkshark.github.io/page-ide/#README.md)
