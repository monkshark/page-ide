# Architecture

> 한국어: [architecture.md](https://monkshark.github.io/page-ide/#guides/architecture.md)

> Module boundaries, dependency direction, and stack choices.

PAGE splits into 16 Gradle modules. The boundaries are drawn so dependencies flow in one direction only, and most modules are already implemented. Only `echo` and `pair` are still at the scaffolding stage.

---

## Stack

| Area | Choice | Note |
|---|---|---|
| Language | Kotlin (JVM 21+) | Reuse JVM libraries directly |
| UI | Compose Multiplatform | Skia on desktop, wasmJs target for the docs viewer |
| Build | Gradle (Kotlin DSL) | 16 modules |
| LSP | LSP4J (Eclipse) | Transport / init layer in the `lsp` module |
| Syntax | Tree-sitter | Import / symbol extraction in `atlas` (JNI). Editor highlighting uses hand-written lexers |
| Git | `git status --porcelain` subprocess | Shells out to system git; no JGit |
| PTY | pty4j | Embedded terminal in `runtime` |
| Local store | SQLite (xerial JDBC) | Echo timeline (planned) |
| AI HTTP | OkHttp | `LLMProvider` interface + adapters (Pair, planned) |

---

## Module structure

`shared-core` and `docs-viewer` are multiplatform (jvm+wasmJs) modules and live at the repo root; the remaining desktop modules live under `page/`.

```
page-ide/
├── shared-core   (Compose MPP: markdown / JSON parsers, graph model, FilePath)
├── docs-viewer   (Compose wasmJs: public docs viewer + widget islands)
└── page/
    ├── core         (app identity, shared domain types)
    ├── perf         (startup instrumentation, UI-freeze watchdog)
    ├── editor       (text buffer, edit history, syntax lexers, fuzzy / quick-open)
    ├── ui           (Compose components, Glass design tokens, syntax palette)
    ├── lsp          (LSP4J client: transport, init, backend registry)
    ├── language     (language-intelligence orchestration: routing, doc sync, completion, diagnostics)
    ├── runtime      (toolchain / language-server install, pty4j execution, embedded terminal)
    ├── workspace    (file tree, file ops, rename refactor, project search)
    ├── atlas-view   (Compose MPP: overview graph model + render)
    ├── atlas        (tree-sitter code graph analysis, IDE panels, snapshot export)
    ├── git          (workspace VCS status from `git status --porcelain`)
    ├── echo         (keystroke recorder + timeline — scaffolding)
    ├── pair         (LLMProvider adapters: chat / observer / agent / tutor — scaffolding)
    └── app          (assembly layer, main entry point)
```

Each module's detailed responsibilities continue in the per-module documents linked from the [table of contents](https://monkshark.github.io/page-ide/#README_en.md).

---

## Dependency direction

The graph below shows how the modules actually depend on each other today. Hover a node to isolate its neighbors; scroll to zoom, drag to pan.

```page-widget
atlas
```

Dependencies flow strictly downward, with no cycles.

- `core` (JVM shared) and `shared-core` (Compose MPP) are the foundation — they depend on nothing.
- `editor` is the text substrate that `ui`, `language`, and `workspace` all build on.
- Feature modules depend only downward on the foundation and substrate modules they need (`core`, `editor`, `lsp`, `runtime`, `ui`) — never sideways on each other.
- `atlas-view` is the overview render pulled out of `atlas` as a multiplatform layer, so desktop `atlas` and wasm `docs-viewer` share the same render code.
- Assembly and wiring live in `app`.

---

## AI provider strategy

The `pair` module is still scaffolding, but the layering is decided. A single `LLMProvider` interface with four swappable adapters.

```kotlin
interface LLMProvider {
    suspend fun complete(prompt: Prompt): Flow<TokenChunk>
    fun supportsTools(): Boolean
}
```

- Ollama — local, default. Code never leaves the user's machine.
- Anthropic Claude — user-supplied API key.
- OpenAI ChatGPT — user-supplied API key.
- OpenAI-compatible endpoint — Together AI / Groq / self-hosted; user supplies the endpoint URL.

API keys are stored in the OS keychain (Windows Credential Manager, macOS Keychain, libsecret on Linux). No plaintext storage.

---

- [Back to overview](https://monkshark.github.io/page-ide/#guides/overview_en.md)
- [Back to index](https://monkshark.github.io/page-ide/#README_en.md)
