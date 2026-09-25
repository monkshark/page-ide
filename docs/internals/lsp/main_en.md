# LSP

> `page:lsp` — the Language Server Protocol client layer. Spawns servers, speaks the protocol, and registers per-language backends

Completion, go-to-definition, and diagnostics are produced by a different server for each language. This module is the client layer that talks to those servers over LSP. It divides into the transport that spawns the server process, the client that handles initialization and requests, the backend that binds a language to a server, and per-feature request builders for completion, hover, definition, and the rest.

The higher-level IDE orchestration (controller lifecycle, routing, caching) sits on top of this layer in [`page:language`](https://monkshark.github.io/page-ide/#internals/language/main_en.md).

> 한국어: [main.md](https://monkshark.github.io/page-ide/#internals/lsp/main.md)

---

## Structure

```mermaid
flowchart TB
    reg["LspBackends · LanguageRegistry<br/>language→server registry"] --> backend["LanguageBackend<br/>resolve executable · spawn"]
    backend --> client["LspClient<br/>init · capabilities · listeners"]
    client --> transport["LspTransport<br/>process stdio"]
    client --> features["per-feature requests<br/>Completion · Hover · …"]
```

| Layer | Role |
|---|---|
| Registry | `LanguageBackend`/`LspBackends`, `LanguageRegistry`/`LanguageDefinition` — which extension is served by which server |
| Client | `LspClient` — lsp4j `LanguageClient` implementation: init, capabilities, receiving server notifications |
| Transport | `LspTransport` (`StreamTransport` · `ProcessTransport`) — the server process's stdin/stdout |
| Features | `Completion` · `Hover` · `Definition` · `References` · `Rename` · `SignatureHelp` · `Symbols` · `CallHierarchy` · `InlayHints` · `CodeActions` · `Diagnostic` request builders |
| Augmentation | `CompletionProfile` · `CompletionAugmentor` · `PageQuickFixes` — add keywords, imports, and quick fixes to server responses |

---

## Registering backends

`LanguageBackend` is the interface that binds one language to a server.

```kotlin
interface LanguageBackend {
    val id: String
    val displayName: String
    fun supports(extension: String?): Boolean
    fun resolveExecutable(env: Map<String, String>): Resolution
    fun spawn(executable: Path, workspaceRoot: Path?, ...): LspClient
}
```

`resolveExecutable` locates the server executable and returns `Found`/`NotFound`; `spawn` launches a process from that executable and produces a connected `LspClient`. `LspBackends` collects registered backends and picks the right one for a file path (`forFile`). When routing needs to be overridden, `routingInterceptor` injects a decision ahead of the default extension rule.

A language's static metadata (extensions, server binary names, per-OS install guidance, launch args) comes from `LanguageRegistry`, which reads `languages.json` from resources into a list of `LanguageDefinition`. This is where languages can be added as data rather than code.

---

## LspClient — the client and initialization

`LspClient` implements the lsp4j `LanguageClient`. `start()` builds a launcher, obtains the server proxy, and announces client capabilities via `initialize`. It declares completion snippet/resolve, code-action literal/resolve, call hierarchy, diagnostic tags (`Unnecessary` · `Deprecated`), `applyEdit`, and work-done progress, to draw on as much server capability as possible.

Asynchronous notifications from the server flow to listeners: subscribe with `onDiagnostics` · `onLogMessage` · `onShowMessage` · `onProgress` · `onApplyEdit`. State is tracked with `LspState`, and shutdown has two paths — a graceful `shutdown()` and a forced `forceClose()`. JDT-LS's non-standard notifications (`language/status`, etc.) are quietly accepted and ignored.

---

## LspTransport — process stdio

`ProcessTransport` wires the server process's stdin/stdout to the client, and pumps stderr on a daemon thread into the log. On Windows, the server and its descendants belong to a dedicated Job Object with `KILL_ON_JOB_CLOSE`. The operating system terminates the server tree even when PAGE is forcibly stopped without running cleanup code. Descendants created before assignment are attached to the same job. Normal close releases the job first, then uses `taskkill /F /T` for any remaining process. Other systems destroy descendant processes.

`LspClient.shutdown()` waits at most two seconds for the server response, then closes the transport regardless of the response. A Kotlin server waiting for analysis to finish cannot leave PAGE waiting indefinitely for its reply. Shutdown before initialization also closes the transport that has already been created.

The order matters. The process dies first, the pipes close second. The other way round means closing a stdout that the lsp4j listener is parked on, and on Windows that `close()` never returns — every path that stops a server (removal, restart, shutdown) stalls on that one line. The close itself also runs on a daemon thread with a two-second bound, so it can never hold its caller.

---

## Per-feature requests and augmentation

Completion, hover, definition, references, rename, signature help, symbols, call hierarchy, inlay hints, code actions, and diagnostics each live in their own file, building the lsp4j request and translating the response into a form the IDE can use.

Where the server response alone is not enough, this layer augments it. `CompletionProfile` holds the per-language keyword set and whether auto-import is supported; `CompletionAugmentor` folds keywords and import candidates (found via workspace symbols) into the completion list. `PageQuickFixes` synthesizes quick fixes the server does not provide. `CompletionFrecency` promotes frequently and recently chosen items.


---

## Measuring startup

`LspStartupBench` times how long each language's server takes to come up. Point it at a directory holding one project per language and it picks each project's representative source file, resolves the backend, spawns it, and records the time to the `initialize` response.

```
./gradlew :page:app:test --tests 'page.app.LspStartupBench' --rerun-tasks -Dpage.lsp.bench=/path/to/lsp-bench -Dpage.lsp.bench.runs=2
```

Without `page.lsp.bench` it passes silently, printing how to use it.

| Property | Meaning |
|---|---|
| `page.lsp.bench` | Root holding one project per language (absent means it does not run) |
| `page.lsp.bench.only` | Comma-separated projects to measure |
| `page.lsp.bench.install` | Installer ids to install first if missing |
| `page.lsp.bench.runs` | Repeat count — 2 or more to see cold and warm together |
| `page.lsp.bench.timeout` | Seconds to wait for `initialize` (default 180) |
| `page.lsp.bench.out` | Report path (default `<root>/lsp-startup.txt`) |

A project's language comes from its directory name; when no backend matches that name, it falls back to whichever backend most of the source files use. Config files such as `json` and `yaml` are left out of that vote.

Repeating matters because of caches. solargraph, sourcekit-lsp, and the Dart Analysis Server build a type map or toolchain index on first start, so the first run and the second can differ by more than tenfold.

---

- [Back to index](https://monkshark.github.io/page-ide/#README_en.md)
