# Shared Core

> `shared-core` — multiplatform foundation shared by the desktop IDE and the wasm docs viewer

PAGE needs the same logic in two places: the JVM desktop IDE and the wasm docs viewer that runs in a browser. To avoid writing markdown parsing, graph handling, and path math twice, the parts that are pure Kotlin and Compose live in `shared-core`. This module compiles to both `jvm` and `wasmJs` targets and never touches `java.*`.

> 한국어: [main.md](https://monkshark.github.io/page-ide/#modules/shared-core/main.md)

---

## Structure

| Package | Role |
|---|---|
| `md` | Markdown parser (`MdParser` → `MdNode` tree) |
| `json` | Dependency-free small JSON parser (`Json`) |
| `syntax` | Lexer interface plus Kotlin / Java / JSON lexers |
| `graph` | Slim graph model / queries / snapshot codec |
| `widget` | Compose graph canvas (`GraphCanvas`) |
| `path` | String-backed path (`FilePath`, a `java.nio.Path` replacement) |
| `docs` | Doc routing / index / language variants |

---

## md — markdown parser

`MdParser.parse(src)` turns markdown into an `MdNode` tree. It handles headings, paragraphs, code fences, lists, task lists, tables, block quotes, callouts, and inlines (links, emphasis, code spans). Headings carry a slug used by the table of contents and anchors.

One node is special. When a fence's language is `page-widget`, the parser emits `WidgetRef(name, args)`. On encountering it, the docs viewer mounts a Compose widget island in place instead of prose.

---

## json — small parser

`Json.parse(text)` parses text into a `JsonValue` (`JsonObject`, `JsonArray`, `JsonString`, `JsonNumber`, `JsonBool`, `JsonNull`). The `asString()`, `asArray()`, and `asObject()` extensions pull values out. It has no external dependency, so it runs as-is on the wasm target — used to read snapshot JSON and the document index.

---

## syntax — lexers

`SyntaxLexer.tokenize(text)` returns a list of `Token`s (a `TokenKind` plus a range). `CodeLexers.forLang(lang)` routes a language name to a lexer.

| Language | Lexer |
|---|---|
| `kotlin` · `kt` · `kts` | `KotlinLexer` |
| `java` | `JavaLexer` |
| `json` | `JsonLexer` |

`SyntaxHighlight` and `SyntaxPalette` map tokens to colors. The docs viewer's code-block highlighting builds on this layer.

---

## graph — model and queries

`GraphNode`, `GraphEdge`, and `GraphSlice` form a slim graph. `GraphInsights` computes a node's neighborhood (`neighborhood`), in-degrees (`indegrees`), and cycles (`cycles`, via strongly-connected components). `GraphSnapshot.parse(json)` reads an exported JSON snapshot back into a `GraphSlice`.

---

## widget — graph canvas

`GraphCanvas` is a Compose composable that takes a `GraphSlice` and draws its nodes and edges. Hover a node to isolate its neighbors; scroll to zoom, drag to pan. Colors are injected via `GraphColors`, so it works regardless of theme.

---

## path — FilePath

`FilePath` wraps a single string as a path value. It offers `parent`, `fileName`, `segments`, `startsWith`, `relativize`, and `resolve`, and normalizes input on `/`. It is the replacement for `java.nio.Path` where wasm has none, so Atlas's graph model can do the same hierarchy math on both targets.

---

## docs — routing

The docs viewer's path rules live here. `parseDocIndex` reads the document index JSON; `parseDocHash` and `buildDocHash` move between the `path#heading` URL hash and its parts. `DocLang` pairs Korean and English variants by the `_en` suffix, and `buildDocTree` folds a flat path list into the sidebar tree.

---

- [Back to index](https://monkshark.github.io/page-ide/#README_en.md)
