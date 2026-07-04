# Architecture

> 한국어: [architecture.md](https://monkshark.github.io/page-ide/#guides/architecture.md)

> Module boundaries, dependency direction, and stack choices.

This document grows as code lands. For now, only the decided skeleton is captured.

---

## Stack

| Area | Choice | Note |
|---|---|---|
| Language | Kotlin (JVM 21+) | Reuse JVM libraries directly |
| UI | Compose Multiplatform Desktop | Same stack as JetBrains Fleet, Skia-based |
| Build | Gradle (Kotlin DSL) | Multi-module |
| LSP | LSP4J (Eclipse) | Multi-language standard |
| Syntax | Tree-sitter | JNI bindings |
| Git | JGit | Avoid reimplementing |
| Local store | SQLite (xerial JDBC) | Echo timeline |
| AI HTTP | OkHttp | `LLMProvider` interface + 4 adapters |
| PTY | JediTerm or pty4j | Decide after prototyping |

---

## Module structure (planned)

```
page/
├── core         (shared utilities, domain types, event bus)
├── editor       (text buffer, syntax highlighting, key input)
├── language     (LSP client, language-definition JSON loader)
├── workspace    (file tree, multi-tab, split panes, project model)
├── ui           (Compose components, Glass design tokens)
├── atlas        (code graph analysis + render)
├── echo         (keystroke recorder + timeline UI)
├── pair         (LLMProvider, adapters, chat/observer/agent/tutor)
├── runtime      (PTY, build/run, output panel)
├── git          (JGit wrapper, diff/stage/commit UI)
└── app          (assembly layer, main entry point)
```

> Each module's detailed responsibilities will move to its own document as code lands.

---

## Dependency direction

The graph below shows how the modules actually depend on each other today. Hover a node to isolate its neighbors; scroll to zoom, drag to pan.

```page-widget
atlas
```

Dependencies flow strictly downward, with no cycles.

- `core` and `shared-core` are the foundation — they depend on nothing.
- `editor` is the text substrate that `ui`, `language`, and `workspace` all build on.
- Feature modules depend only downward on the foundation and substrate modules they need (`core`, `editor`, `lsp`, `runtime`, `ui`) — never sideways on each other.
- Assembly and wiring live in `app`.

---

## AI provider strategy

A single `LLMProvider` interface with four swappable adapters.

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
