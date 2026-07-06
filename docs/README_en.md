# PAGE IDE Docs

> 한국어: [README_kr.md](https://monkshark.github.io/page-ide/#README_kr.md)

> Multi-language desktop IDE — Pair · Atlas · Glass · Echo

Public documentation entry point for PAGE IDE. This page only serves as a table of contents. Each item below links to the actual content.

Build the viewer with `node build_viewer.js` to produce `index.html`, which opens in a browser with no server required.

---

## Table of contents

### Guides
- [Getting started](https://monkshark.github.io/page-ide/#guides/getting_started_en.md) — The shortest path from a clone to a running desktop IDE
- [PAGE overview](https://monkshark.github.io/page-ide/#guides/overview_en.md) — The four core values (Pair · Atlas · Glass · Echo) and what we will not build
- [Architecture](https://monkshark.github.io/page-ide/#guides/architecture_en.md) — Module structure, dependency direction, and stack choices

### core module
- [PageIdentity](https://monkshark.github.io/page-ide/#modules/core/page_identity_en.md) — Single home for app name / acronym / tagline

### shared-core module
- [Shared Core](https://monkshark.github.io/page-ide/#modules/shared-core/main_en.md) — Multiplatform foundation shared by the desktop IDE and wasm viewer (markdown / JSON parsers, graph model, FilePath)

### editor module
- [TextBuffer](https://monkshark.github.io/page-ide/#modules/editor/text_buffer_en.md) — `StringBuilder` wrapper + line/column coordinates
- [TabBook](https://monkshark.github.io/page-ide/#modules/editor/tab_book_en.md) — Tab collection + active index
- [EditHistory](https://monkshark.github.io/page-ide/#modules/editor/edit_history_en.md) — Undo/redo stack
- [FileDocument](https://monkshark.github.io/page-ide/#modules/editor/file_document_en.md) — Body / saved body / dirty bundle
- [FileKind](https://monkshark.github.io/page-ide/#modules/editor/file_kind_en.md) — Text / image / SVG classification
- [FileTree](https://monkshark.github.io/page-ide/#modules/editor/file_tree_en.md) — Sidebar tree node builder
- [SearchState](https://monkshark.github.io/page-ide/#modules/editor/search_state_en.md) — Query + matches + active index
- [Replace](https://monkshark.github.io/page-ide/#modules/editor/replace_en.md) — Replace match ranges
- [AutoClose](https://monkshark.github.io/page-ide/#modules/editor/auto_close_en.md) — Bracket / quote auto-pairing
- [BracketMatch](https://monkshark.github.io/page-ide/#modules/editor/bracket_match_en.md) — Find the matching bracket
- [Indent](https://monkshark.github.io/page-ide/#modules/editor/indent_en.md) — Tab / Enter auto-indent
- [LineMove](https://monkshark.github.io/page-ide/#modules/editor/line_move_en.md) — Line move / duplicate
- [WordBoundary](https://monkshark.github.io/page-ide/#modules/editor/word_boundary_en.md) — Word-wise motion / deletion boundaries
- [FoldRegions](https://monkshark.github.io/page-ide/#modules/editor/fold_regions_en.md) — Code-fold region detection + offset mapping
- [FuzzyMatcher](https://monkshark.github.io/page-ide/#modules/editor/fuzzy_matcher_en.md) — Subsequence fuzzy match + score
- [QuickOpen](https://monkshark.github.io/page-ide/#modules/editor/quick_open_en.md) — Quick Open result ranking
- [ProjectFileIndex](https://monkshark.github.io/page-ide/#modules/editor/project_file_index_en.md) — Flattening walker over the project root
- [MarkdownFence](https://monkshark.github.io/page-ide/#modules/editor/markdown_fence_en.md) — Inside-code-fence detection
- [SyntaxLexer](https://monkshark.github.io/page-ide/#modules/editor/syntax_lexer_en.md) — Lexer interface
- [SyntaxLexers](https://monkshark.github.io/page-ide/#modules/editor/syntax_lexers_en.md) — Extension → lexer dispatch
- [JvmLexer](https://monkshark.github.io/page-ide/#modules/editor/jvm_lexer_en.md) — Shared Java/Kotlin scanner
- [KotlinLexer](https://monkshark.github.io/page-ide/#modules/editor/kotlin_lexer_en.md) — Kotlin keyword set
- [JavaLexer](https://monkshark.github.io/page-ide/#modules/editor/java_lexer_en.md) — Java keyword set
- [JsonLexer](https://monkshark.github.io/page-ide/#modules/editor/json_lexer_en.md) — JSON tokenizer

### ui module
- [GlassTheme](https://monkshark.github.io/page-ide/#modules/ui/glass_theme_en.md) — Glass design tokens / `MaterialTheme` application
- [EditorFonts](https://monkshark.github.io/page-ide/#modules/ui/editor_fonts_en.md) — Body font family
- [SyntaxColors](https://monkshark.github.io/page-ide/#modules/ui/syntax_colors_en.md) — Syntax palette

### app module
- [Main](https://monkshark.github.io/page-ide/#modules/app/main_en.md) — `application` entry point + window state
- [EditorPanel](https://monkshark.github.io/page-ide/#modules/app/editor_panel_en.md) — Body + gutter + highlights
- [TabBar](https://monkshark.github.io/page-ide/#modules/app/tab_bar_en.md) — Tab strip + drag-reorder
- [SearchBar](https://monkshark.github.io/page-ide/#modules/app/search_bar_en.md) — Find/replace strip
- [FileTreePanel](https://monkshark.github.io/page-ide/#modules/app/file_tree_panel_en.md) — Left sidebar
- [LineNumberGutter](https://monkshark.github.io/page-ide/#modules/app/line_number_gutter_en.md) — Line-number gutter
- [PreviewPanel](https://monkshark.github.io/page-ide/#modules/app/preview_panel_en.md) — Image / SVG preview
- [UnsavedChangesDialog](https://monkshark.github.io/page-ide/#modules/app/unsaved_changes_dialog_en.md) — Save-confirmation modal
- [QuickOpenDialog](https://monkshark.github.io/page-ide/#modules/app/quick_open_dialog_en.md) — `Ctrl+P` Quick Open dialog
- [PendingClose](https://monkshark.github.io/page-ide/#modules/app/pending_close_en.md) — Close-request identifier
- [FileDialogs](https://monkshark.github.io/page-ide/#modules/app/file_dialogs_en.md) — `JFileChooser` wrappers

### atlas module
- [Atlas](https://monkshark.github.io/page-ide/#modules/atlas/main_en.md) — code graph (import · call · module dependencies)

### atlas-view module
- [Atlas View](https://monkshark.github.io/page-ide/#modules/atlas-view/main_en.md) — overview graph model / render (multiplatform, shared by desktop and the docs viewer)

### lsp module
- [LSP](https://monkshark.github.io/page-ide/#modules/lsp/main_en.md) — Language server client layer (transport · init · backend registry)

### language module
- [Language](https://monkshark.github.io/page-ide/#modules/language/main_en.md) — Language-intelligence orchestration (routing · document sync · completion · diagnostics)

### runtime module
- [Runtime](https://monkshark.github.io/page-ide/#modules/runtime/main_en.md) — Toolchain / language-server install, program execution, embedded terminal

### workspace module
- [Workspace](https://monkshark.github.io/page-ide/#modules/workspace/main_en.md) — File tree, file operations, rename refactors, project search

### git module
- [Git](https://monkshark.github.io/page-ide/#modules/git/main_en.md) — Workspace VCS status (change kinds from git status)

### perf module
- [Perf](https://monkshark.github.io/page-ide/#modules/perf/main_en.md) — Startup instrumentation and a UI-freeze watchdog

### docs-viewer module
- [Docs Viewer](https://monkshark.github.io/page-ide/#modules/docs-viewer/main_en.md) — The Compose wasmJs app that renders this docs site, plus its widget islands

> `pair`, `echo` modules will be added in their respective milestones.

---

## External links

- GitHub: <https://github.com/monkshark/page-ide>
- Devlog series (Korean): <https://monkshark.github.io/categories/page-개발기/>
