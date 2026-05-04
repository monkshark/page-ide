# Editor fonts

The editor (`page.ui.EditorFontFamily`) loads `D2Coding.ttf` from this directory at
runtime. If the file is missing, it falls back to the system monospace, which does
not maintain consistent widths for Hangul / CJK glyphs.

## Adding D2Coding

1. Download D2Coding from <https://github.com/naver/d2codingfont/releases> (OFL 1.1).
2. Drop `D2Coding.ttf` in this directory:
   `page/ui/src/main/resources/fonts/D2Coding.ttf`
3. Restart the app — the loader picks it up at class init.

## Replacing with a different font

Edit `page/ui/src/main/kotlin/page/ui/EditorFonts.kt` and change
`PRIMARY_FONT_RESOURCE` to the new file name. Place the TTF in this directory.
The replacement font should be a true monospace with full Hangul coverage and
ASCII : Hangul width ratio of 1:2 to preserve column alignment.
