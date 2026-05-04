# PAGE IDE Docs

> 한국어: [README.md](https://monkshark.github.io/PAGE_IDE/#README.md)

> Multi-language desktop IDE — Pair · Atlas · Glass · Echo

Public documentation entry point for PAGE IDE. This page only serves as a table of contents. Each item below links to the actual content.

Build the viewer with `node build_viewer.js` to produce `index.html`, which opens in a browser with no server required.

---

## Table of contents

### Guides
- [PAGE overview](https://monkshark.github.io/PAGE_IDE/#guides/overview_en.md) — The four core values (Pair · Atlas · Glass · Echo) and what we will not build
- [Architecture](https://monkshark.github.io/PAGE_IDE/#guides/architecture_en.md) — Module structure, dependency direction, and stack choices

### core module
- [PageIdentity](https://monkshark.github.io/PAGE_IDE/#modules/core/page_identity_en.md) — Single home for app name / acronym / tagline

### editor module
- [TextBuffer](https://monkshark.github.io/PAGE_IDE/#modules/editor/text_buffer_en.md) — `StringBuilder` wrapper + line/column coordinates
- [TabBook](https://monkshark.github.io/PAGE_IDE/#modules/editor/tab_book_en.md) — Tab collection + active index
- [EditHistory](https://monkshark.github.io/PAGE_IDE/#modules/editor/edit_history_en.md) — Undo/redo stack
- [FileDocument](https://monkshark.github.io/PAGE_IDE/#modules/editor/file_document_en.md) — Body / saved body / dirty bundle
- [FileKind](https://monkshark.github.io/PAGE_IDE/#modules/editor/file_kind_en.md) — Text / image / SVG classification
- [FileTree](https://monkshark.github.io/PAGE_IDE/#modules/editor/file_tree_en.md) — Sidebar tree node builder
- [SearchState](https://monkshark.github.io/PAGE_IDE/#modules/editor/search_state_en.md) — Query + matches + active index
- [Replace](https://monkshark.github.io/PAGE_IDE/#modules/editor/replace_en.md) — Replace match ranges
- [AutoClose](https://monkshark.github.io/PAGE_IDE/#modules/editor/auto_close_en.md) — Bracket / quote auto-pairing
- [BracketMatch](https://monkshark.github.io/PAGE_IDE/#modules/editor/bracket_match_en.md) — Find the matching bracket
- [Indent](https://monkshark.github.io/PAGE_IDE/#modules/editor/indent_en.md) — Tab / Enter auto-indent
- [LineMove](https://monkshark.github.io/PAGE_IDE/#modules/editor/line_move_en.md) — Line move / duplicate
- [WordBoundary](https://monkshark.github.io/PAGE_IDE/#modules/editor/word_boundary_en.md) — Word-wise motion / deletion boundaries
- [MarkdownFence](https://monkshark.github.io/PAGE_IDE/#modules/editor/markdown_fence_en.md) — Inside-code-fence detection
- [SyntaxLexer](https://monkshark.github.io/PAGE_IDE/#modules/editor/syntax_lexer_en.md) — Lexer interface
- [SyntaxLexers](https://monkshark.github.io/PAGE_IDE/#modules/editor/syntax_lexers_en.md) — Extension → lexer dispatch
- [JvmLexer](https://monkshark.github.io/PAGE_IDE/#modules/editor/jvm_lexer_en.md) — Shared Java/Kotlin scanner
- [KotlinLexer](https://monkshark.github.io/PAGE_IDE/#modules/editor/kotlin_lexer_en.md) — Kotlin keyword set
- [JavaLexer](https://monkshark.github.io/PAGE_IDE/#modules/editor/java_lexer_en.md) — Java keyword set
- [JsonLexer](https://monkshark.github.io/PAGE_IDE/#modules/editor/json_lexer_en.md) — JSON tokenizer

### ui module
- [GlassTheme](https://monkshark.github.io/PAGE_IDE/#modules/ui/glass_theme_en.md) — Glass design tokens / `MaterialTheme` application
- [EditorFonts](https://monkshark.github.io/PAGE_IDE/#modules/ui/editor_fonts_en.md) — Body font family
- [SyntaxColors](https://monkshark.github.io/PAGE_IDE/#modules/ui/syntax_colors_en.md) — Syntax palette

### app module
- [Main](https://monkshark.github.io/PAGE_IDE/#modules/app/main_en.md) — `application` entry point + window state
- [EditorPanel](https://monkshark.github.io/PAGE_IDE/#modules/app/editor_panel_en.md) — Body + gutter + highlights
- [TabBar](https://monkshark.github.io/PAGE_IDE/#modules/app/tab_bar_en.md) — Tab strip + drag-reorder
- [SearchBar](https://monkshark.github.io/PAGE_IDE/#modules/app/search_bar_en.md) — Find/replace strip
- [FileTreePanel](https://monkshark.github.io/PAGE_IDE/#modules/app/file_tree_panel_en.md) — Left sidebar
- [LineNumberGutter](https://monkshark.github.io/PAGE_IDE/#modules/app/line_number_gutter_en.md) — Line-number gutter
- [PreviewPanel](https://monkshark.github.io/PAGE_IDE/#modules/app/preview_panel_en.md) — Image / SVG preview
- [UnsavedChangesDialog](https://monkshark.github.io/PAGE_IDE/#modules/app/unsaved_changes_dialog_en.md) — Save-confirmation modal
- [PendingClose](https://monkshark.github.io/PAGE_IDE/#modules/app/pending_close_en.md) — Close-request identifier
- [FileDialogs](https://monkshark.github.io/PAGE_IDE/#modules/app/file_dialogs_en.md) — `JFileChooser` wrappers

> `pair`, `atlas`, `echo`, `language`, `runtime`, `git`, `workspace` modules will be added in their respective milestones.

### Features
> Pair / Atlas / Glass / Echo feature docs will be added at the close of each milestone.

---

## External links

- GitHub: <https://github.com/Monkshark/PAGE_IDE>
- Devlog series (Korean): <https://monkshark.github.io/categories/page-개발기/>
