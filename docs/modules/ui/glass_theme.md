# Glass Design System

> `page/ui/src/main/kotlin/page/ui/GlassTokens.kt` · `GlassTheme.kt` — 토큰 정의 + Material3 래퍼

Glass 는 PAGE 의 미감 기둥이다. "보기에 즐거운가, 매일 열고 싶은가" 에 답하는 층. 색·타이포·간격·라운딩·그림자·모션을 한 벌의 토큰으로 묶고, 모든 화면이 이 토큰을 통해 그려진다. 아래 스와치는 실제 토큰 값에서 온 것이다

> English: [glass_theme_en.md](https://monkshark.github.io/page-ide/#modules/ui/glass_theme_en.md)

---

## 팔레트

아홉 개 테마. `Signature` 가 기본 아이덴티티이고, 나머지는 무드와 라이트/다크 취향을 덮는다

<div class="glassdoc">
<style>
.glassdoc{--g-bg:#0A0D14;--g-l1:#0E121A;--g-surf:#131823;--g-raised:#1F2735;--g-outline:#232B3A;--g-sep:rgba(255,255,255,.06);--g-edge:rgba(255,255,255,.08);--g-pri:#7D8EDB;--g-soft:rgba(125,142,219,.14);--g-onpri:#0A0D14;--g-acc:#67B9BA;--g-text:#E7EAF3;--g-muted:#8A92A6;--g-faint:#4A5366;--g-err:#F2727F;--g-warn:#E7B45C;--g-ok:#5BD6A0;color:var(--g-text);}
.glassdoc .gm{font-family:ui-monospace,"JetBrains Mono","Cascadia Code",Consolas,monospace;font-variant-numeric:tabular-nums;}
.glassdoc .g-h{font-size:12px;letter-spacing:.16em;text-transform:uppercase;color:var(--g-acc);margin:26px 0 13px;font-family:ui-monospace,Consolas,monospace;}
.glassdoc .g-themes{display:grid;grid-template-columns:repeat(auto-fill,minmax(188px,1fr));gap:12px;}
.glassdoc .g-th{border:1px solid var(--g-outline);border-radius:12px;overflow:hidden;background:var(--g-surf);}
.glassdoc .g-th.def{border-color:var(--g-pri);box-shadow:0 0 0 1px var(--g-pri);}
.glassdoc .g-sw{height:60px;display:flex;}
.glassdoc .g-sw i{flex:1;}
.glassdoc .g-meta{display:flex;align-items:center;justify-content:space-between;padding:10px 13px 3px;}
.glassdoc .g-nm{font-size:13.5px;font-weight:600;}
.glassdoc .g-tag{font-size:10px;letter-spacing:.14em;text-transform:uppercase;color:var(--g-faint);}
.glassdoc .g-th.def .g-tag{color:var(--g-acc);}
.glassdoc .g-hx{display:flex;gap:8px;padding:0 13px 11px;}
.glassdoc .g-hx span{font-size:10.5px;color:var(--g-muted);display:inline-flex;align-items:center;gap:5px;}
.glassdoc .g-hx b{width:8px;height:8px;border-radius:2px;border:1px solid var(--g-edge);display:inline-block;}
.glassdoc .g-roles{display:grid;grid-template-columns:repeat(auto-fill,minmax(180px,1fr));gap:10px;}
.glassdoc .g-role{border:1px solid var(--g-outline);border-radius:10px;background:var(--g-surf);padding:11px;display:flex;gap:11px;align-items:center;}
.glassdoc .g-role i{width:38px;height:38px;border-radius:8px;border:1px solid var(--g-edge);flex:none;}
.glassdoc .g-rn{font-size:12.5px;font-weight:600;}
.glassdoc .g-rh{font-size:11.5px;color:var(--g-muted);margin-top:2px;}
.glassdoc .g-scales{display:grid;grid-template-columns:1fr 1fr;gap:30px;}
.glassdoc .g-srow{display:flex;align-items:center;gap:13px;margin-bottom:10px;}
.glassdoc .g-srow .l{width:70px;flex:none;font-size:11.5px;color:var(--g-muted);}
.glassdoc .g-bar{height:13px;background:var(--g-pri);border-radius:3px;}
.glassdoc .g-radius{display:flex;flex-wrap:wrap;gap:13px;align-items:flex-end;}
.glassdoc .g-rb{height:42px;width:68px;background:var(--g-soft);border:1px solid var(--g-pri);flex:none;display:flex;align-items:flex-end;justify-content:flex-end;padding:5px;font-size:10.5px;color:var(--g-pri);}
.glassdoc .g-elevs{display:grid;grid-template-columns:repeat(3,1fr);gap:15px;}
.glassdoc .g-el{background:var(--g-raised);border:1px solid var(--g-outline);border-radius:12px;padding:16px;min-height:88px;}
.glassdoc .g-el.r{box-shadow:0 8px 24px rgba(0,0,0,.35);}
.glassdoc .g-el.o{box-shadow:0 12px 32px rgba(0,0,0,.45);}
.glassdoc .g-el .en{font-size:13px;font-weight:600;}
.glassdoc .g-el .ed{font-size:11.5px;color:var(--g-muted);margin-top:4px;}
.glassdoc .g-demo{background:var(--g-l1);border:1px solid var(--g-outline);border-radius:16px;padding:20px;display:grid;gap:20px;}
.glassdoc .g-clu{display:flex;flex-wrap:wrap;gap:10px;align-items:center;}
.glassdoc .g-btn{display:inline-flex;align-items:center;gap:7px;height:32px;padding:0 15px;font-size:12.5px;font-weight:560;border-radius:9px;border:1px solid transparent;color:var(--g-text);}
.glassdoc .g-btn.p{color:var(--g-onpri);background:linear-gradient(180deg,#8f9ee2 0%,#7D8EDB 48%,#6e7cc4 100%);box-shadow:inset 0 1px 0 var(--g-edge),0 6px 18px var(--g-soft);}
.glassdoc .g-btn.s{background:var(--g-soft);color:var(--g-pri);border-color:rgba(125,142,219,.24);}
.glassdoc .g-btn.g{color:var(--g-muted);border-color:var(--g-outline);}
.glassdoc .g-tabs{display:flex;gap:2px;background:var(--g-surf);border:1px solid var(--g-outline);border-radius:10px;padding:4px;width:fit-content;}
.glassdoc .g-tab{font-size:12.5px;padding:6px 13px;border-radius:7px;color:var(--g-muted);}
.glassdoc .g-tab.on{background:var(--g-soft);color:var(--g-pri);font-weight:600;}
.glassdoc .g-pills{display:flex;flex-wrap:wrap;gap:8px;}
.glassdoc .g-pill{font-size:11.5px;padding:4px 10px;border-radius:999px;display:inline-flex;align-items:center;gap:6px;}
.glassdoc .g-pill i{width:7px;height:7px;border-radius:50%;display:inline-block;}
.glassdoc .g-pill.e{background:rgba(242,114,127,.15);color:var(--g-err);}
.glassdoc .g-pill.w{background:rgba(231,180,92,.15);color:var(--g-warn);}
.glassdoc .g-pill.k{background:rgba(91,214,160,.15);color:var(--g-ok);}
.glassdoc .g-pill.e i{background:var(--g-err);}.glassdoc .g-pill.w i{background:var(--g-warn);}.glassdoc .g-pill.k i{background:var(--g-ok);}
.glassdoc .g-rows{border:1px solid var(--g-outline);border-radius:10px;overflow:hidden;}
.glassdoc .g-lr{display:flex;align-items:center;gap:11px;padding:9px 13px;font-size:13px;border-bottom:1px solid var(--g-sep);background:var(--g-surf);}
.glassdoc .g-lr:last-child{border-bottom:0;}
.glassdoc .g-lr.sel{background:var(--g-soft);}
.glassdoc .g-lr .ic{width:14px;height:14px;border-radius:3px;background:var(--g-acc);flex:none;}
.glassdoc .g-lr .su{margin-left:auto;color:var(--g-faint);font-size:12px;}
.glassdoc .g-code{background:var(--g-surf);border:1px solid var(--g-outline);border-radius:10px;padding:15px 17px;font-size:13px;line-height:1.7;overflow-x:auto;white-space:pre;}
.glassdoc .g-code .kw{color:#9DA8FF;}.glassdoc .g-code .st{color:#4FD3C7;}.glassdoc .g-code .nu{color:#C9B6FF;}.glassdoc .g-code .co{color:#828DA8;}.glassdoc .g-code .ty{color:#7FD6E0;}.glassdoc .g-code .id{color:#C8D0E6;}
.glassdoc .g-types{margin:0;}
.glassdoc .g-trow{display:flex;align-items:baseline;gap:18px;padding:13px 0;border-bottom:1px solid var(--g-sep);}
.glassdoc .g-trow:last-child{border-bottom:0;}
.glassdoc .g-tk{width:70px;flex:none;color:var(--g-acc);font-size:12px;}
.glassdoc .g-tu{flex:1;min-width:0;}
.glassdoc .g-ts{width:70px;flex:none;text-align:right;color:var(--g-muted);font-size:12px;}
</style>

<div class="g-themes">
<div class="g-th def"><div class="g-sw"><i style="background:#0A0D14"></i><i style="background:#131823"></i><i style="background:#7D8EDB"></i><i style="background:#67B9BA"></i></div><div class="g-meta"><span class="g-nm">Signature</span><span class="g-tag gm">default</span></div><div class="g-hx gm"><span><b style="background:#0A0D14"></b>#0A0D14</span><span><b style="background:#7D8EDB"></b>#7D8EDB</span></div></div>
<div class="g-th"><div class="g-sw"><i style="background:#F4F6FB"></i><i style="background:#F0F3F9"></i><i style="background:#5566C0"></i><i style="background:#2F948A"></i></div><div class="g-meta"><span class="g-nm">Signature Light</span><span class="g-tag gm">light</span></div><div class="g-hx gm"><span><b style="background:#F4F6FB"></b>#F4F6FB</span><span><b style="background:#5566C0"></b>#5566C0</span></div></div>
<div class="g-th"><div class="g-sw"><i style="background:#1E1F22"></i><i style="background:#26282B"></i><i style="background:#6897BB"></i><i style="background:#6A8759"></i></div><div class="g-meta"><span class="g-nm">Graphite</span><span class="g-tag gm">dark</span></div><div class="g-hx gm"><span><b style="background:#1E1F22"></b>#1E1F22</span><span><b style="background:#6897BB"></b>#6897BB</span></div></div>
<div class="g-th"><div class="g-sw"><i style="background:#0E1418"></i><i style="background:#161D24"></i><i style="background:#6AA9FF"></i><i style="background:#79D4B8"></i></div><div class="g-meta"><span class="g-nm">Cool</span><span class="g-tag gm">dark</span></div><div class="g-hx gm"><span><b style="background:#0E1418"></b>#0E1418</span><span><b style="background:#6AA9FF"></b>#6AA9FF</span></div></div>
<div class="g-th"><div class="g-sw"><i style="background:#1A1612"></i><i style="background:#221C16"></i><i style="background:#E8C691"></i><i style="background:#C99A6B"></i></div><div class="g-meta"><span class="g-nm">Warm</span><span class="g-tag gm">dark</span></div><div class="g-hx gm"><span><b style="background:#1A1612"></b>#1A1612</span><span><b style="background:#E8C691"></b>#E8C691</span></div></div>
<div class="g-th"><div class="g-sw"><i style="background:#F2F4F8"></i><i style="background:#FFFFFF"></i><i style="background:#2D5BFF"></i><i style="background:#00A88E"></i></div><div class="g-meta"><span class="g-nm">Frost</span><span class="g-tag gm">light</span></div><div class="g-hx gm"><span><b style="background:#F2F4F8"></b>#F2F4F8</span><span><b style="background:#2D5BFF"></b>#2D5BFF</span></div></div>
<div class="g-th"><div class="g-sw"><i style="background:#101714"></i><i style="background:#17211D"></i><i style="background:#7CC4A1"></i><i style="background:#C8D58A"></i></div><div class="g-meta"><span class="g-nm">Forest</span><span class="g-tag gm">dark</span></div><div class="g-hx gm"><span><b style="background:#101714"></b>#101714</span><span><b style="background:#7CC4A1"></b>#7CC4A1</span></div></div>
<div class="g-th"><div class="g-sw"><i style="background:#05070C"></i><i style="background:#0B0F18"></i><i style="background:#42E2D6"></i><i style="background:#B388FF"></i></div><div class="g-meta"><span class="g-nm">Midnight</span><span class="g-tag gm">dark</span></div><div class="g-hx gm"><span><b style="background:#05070C"></b>#05070C</span><span><b style="background:#42E2D6"></b>#42E2D6</span></div></div>
<div class="g-th"><div class="g-sw"><i style="background:#F5EEDF"></i><i style="background:#FAF4E6"></i><i style="background:#B85C38"></i><i style="background:#6B7E3F"></i></div><div class="g-meta"><span class="g-nm">Sand</span><span class="g-tag gm">light</span></div><div class="g-hx gm"><span><b style="background:#F5EEDF"></b>#F5EEDF</span><span><b style="background:#B85C38"></b>#B85C38</span></div></div>
</div>

<div class="g-h">Color roles · Signature</div>
<div class="g-roles">
<div class="g-role"><i style="background:#0A0D14"></i><div><div class="g-rn">background</div><div class="g-rh gm">#0A0D14</div></div></div>
<div class="g-role"><i style="background:#0E121A"></i><div><div class="g-rn">surface L1</div><div class="g-rh gm">#0E121A</div></div></div>
<div class="g-role"><i style="background:#131823"></i><div><div class="g-rn">surface</div><div class="g-rh gm">#131823</div></div></div>
<div class="g-role"><i style="background:#1F2735"></i><div><div class="g-rn">raised</div><div class="g-rh gm">#1F2735</div></div></div>
<div class="g-role"><i style="background:#232B3A"></i><div><div class="g-rn">outline</div><div class="g-rh gm">#232B3A</div></div></div>
<div class="g-role"><i style="background:#7D8EDB"></i><div><div class="g-rn">primary</div><div class="g-rh gm">#7D8EDB</div></div></div>
<div class="g-role"><i style="background:#67B9BA"></i><div><div class="g-rn">accent</div><div class="g-rh gm">#67B9BA</div></div></div>
<div class="g-role"><i style="background:#E7EAF3"></i><div><div class="g-rn">text</div><div class="g-rh gm">#E7EAF3</div></div></div>
<div class="g-role"><i style="background:#8A92A6"></i><div><div class="g-rn">muted</div><div class="g-rh gm">#8A92A6</div></div></div>
<div class="g-role"><i style="background:#4A5366"></i><div><div class="g-rn">faint</div><div class="g-rh gm">#4A5366</div></div></div>
<div class="g-role"><i style="background:#F2727F"></i><div><div class="g-rn">error</div><div class="g-rh gm">#F2727F</div></div></div>
<div class="g-role"><i style="background:#E7B45C"></i><div><div class="g-rn">warn</div><div class="g-rh gm">#E7B45C</div></div></div>
<div class="g-role"><i style="background:#5BD6A0"></i><div><div class="g-rn">success</div><div class="g-rh gm">#5BD6A0</div></div></div>
<div class="g-role"><i style="background:#67B9BA"></i><div><div class="g-rn">accent</div><div class="g-rh gm">syntax base</div></div></div>
</div>

<div class="g-h">Type scale</div>
<div class="g-types">
<div class="g-trow"><span class="g-tk gm">label</span><span class="g-tu" style="font-size:15px">Meta, gutter numbers, chips</span><span class="g-ts gm">11 sp</span></div>
<div class="g-trow"><span class="g-tk gm">ui</span><span class="g-tu" style="font-size:16.5px">Buttons, tabs, tree rows</span><span class="g-ts gm">12 sp</span></div>
<div class="g-trow"><span class="g-tk gm">body</span><span class="g-tu" style="font-size:18px">Panels, descriptions, dialogs</span><span class="g-ts gm">13 sp</span></div>
<div class="g-trow"><span class="g-tk gm">code</span><span class="g-tu gm" style="font-size:18px">Editor, terminal, monospace</span><span class="g-ts gm">13 sp</span></div>
<div class="g-trow"><span class="g-tk gm">title</span><span class="g-tu" style="font-size:19.5px">Section and panel headers</span><span class="g-ts gm">14 sp</span></div>
</div>

<div class="g-h">Spacing &amp; radius</div>
<div class="g-scales">
<div>
<div class="g-srow"><span class="l gm">xs · 4</span><div class="g-bar" style="width:14px"></div></div>
<div class="g-srow"><span class="l gm">sm · 8</span><div class="g-bar" style="width:27px"></div></div>
<div class="g-srow"><span class="l gm">md · 12</span><div class="g-bar" style="width:41px"></div></div>
<div class="g-srow"><span class="l gm">lg · 16</span><div class="g-bar" style="width:54px"></div></div>
<div class="g-srow"><span class="l gm">xl · 24</span><div class="g-bar" style="width:82px"></div></div>
</div>
<div class="g-radius">
<div class="g-rb gm" style="border-radius:6px">6</div>
<div class="g-rb gm" style="border-radius:8px">8</div>
<div class="g-rb gm" style="border-radius:12px">12</div>
<div class="g-rb gm" style="border-radius:16px">16</div>
</div>
</div>

<div class="g-h">Elevation</div>
<div class="g-elevs">
<div class="g-el"><div class="en">Flat</div><div class="ed">Inline surfaces, editor gutter</div></div>
<div class="g-el r"><div class="en">Raised</div><div class="ed">Panels, cards · blur 24 · y 8</div></div>
<div class="g-el o"><div class="en">Overlay</div><div class="ed">Menus, dialogs · blur 32 · y 12</div></div>
</div>

<div class="g-h">Components</div>
<div class="g-demo">
<div class="g-clu">
<span class="g-btn p">Run</span>
<span class="g-btn s">Open project</span>
<span class="g-btn g">Cancel</span>
</div>
<div class="g-clu" style="gap:20px">
<div class="g-tabs"><span class="g-tab on">Main.kt</span><span class="g-tab">GlassTheme.kt</span><span class="g-tab">build.gradle.kts</span></div>
<div class="g-pills"><span class="g-pill e"><i></i>3 errors</span><span class="g-pill w"><i></i>7 warnings</span><span class="g-pill k"><i></i>Build passed</span></div>
</div>
<div class="g-rows">
<div class="g-lr sel"><span class="ic"></span>GlassTokens.kt<span class="su gm">page.ui</span></div>
<div class="g-lr"><span class="ic"></span>WelcomeScreen.kt<span class="su gm">page.app</span></div>
<div class="g-lr"><span class="ic"></span>SettingsDialog.kt<span class="su gm">page.app</span></div>
</div>
<div class="g-code gm"><span class="co">// syntax palette, live</span>
<span class="kw">fun</span> <span class="id">glassTokensFor</span>(palette: <span class="ty">GlassPalette</span>): <span class="ty">GlassTokens</span> = <span class="ty">GlassTokens</span>(
    radius = <span class="ty">GlassRadius</span>(xs = <span class="nu">6</span>.dp, sm = <span class="nu">8</span>.dp),
    accent = <span class="st">"#67B9BA"</span>,
)</div>
</div>
</div>

---

## 사용법

`GlassTheme` 로 콘텐츠를 감싸면 `LocalGlassTokens` 가 제공되고 Material3 `colorScheme` 이 함께 채워진다. 메인 윈도우와 모든 `DialogWindow` 가 이 함수로 감싸야 같은 팔레트가 적용된다

```kotlin
@Composable
fun GlassTheme(palette: GlassPalette = GlassPalette.Signature, content: @Composable () -> Unit) {
    val tokens = remember(palette) { glassTokensFor(palette) }
    CompositionLocalProvider(LocalGlassTokens provides tokens) {
        MaterialTheme(colorScheme = /* tokens → scheme */, content = content)
    }
}
```

토큰은 컴포저블에서 `Glass.colors`, `Glass.type`, `Glass.space`, `Glass.radius`, `Glass.elevation`, `Glass.motion`, `Glass.palette` 로 읽는다

```kotlin
Text(
    "Run",
    color = Glass.colors.onPrimary,
    fontSize = Glass.type.ui,
    modifier = Modifier
        .background(Glass.colors.primary, RoundedCornerShape(Glass.radius.sm))
        .padding(horizontal = Glass.space.md, vertical = Glass.space.sm),
)
```

| 토큰 그룹 | 필드 |
|---|---|
| `color` | `background` · `surfaceL1..L3` · `surface` · `raised` · `outline` · `primary` · `accent` · `text` · `muted` · `faint` · `error` · `warn` · `success` · `syntax` |
| `type` | `label 11` · `ui 12` · `body 13` · `code 13` · `title 14` (sp) |
| `space` | `xs 4` · `sm 8` · `md 12` · `lg 16` · `xl 24` (dp) |
| `radius` | `xs 6` · `sm 8` · `md 12` · `lg 16` (dp) |
| `elevation` | `flat` · `raised` (blur 24, y 8) · `overlay` (blur 32, y 12) |
| `motion` | `fast 100` · `base 200` · `slow 320` (ms) · standard easing |

---

- [목차로 돌아가기](https://monkshark.github.io/page-ide/#README_kr.md)
