# Atlas

> `page:atlas` — code graph. Visualizes import · call · module dependencies as nodes and edges

A file tree shows only directory structure. What calls what, and which module depends on where, never surfaces in the tree. Atlas extracts those relations from source and draws them as a graph, so a developer new to a codebase can see where a file sits in the project at a glance.

> 한국어: [main.md](https://monkshark.github.io/page-ide/#modules/atlas/main.md)

---

## Structure

The module splits into four layers.

```mermaid
flowchart LR
    src[source files] --> analyzer
    analyzer[analyzer<br/>extract·resolve imports] --> graph
    graph[graph<br/>graph model·queries] --> render
    render[render<br/>panels·canvas]
    graph --> export[export<br/>snapshot]
```

| Layer | Role |
|---|---|
| `analyzer` | Extracts imports from source with tree-sitter, resolves them to real file paths |
| `graph` | Builds relations into a node/edge model and queries it (cycles, dependent counts, etc.) |
| `render` | Draws and interacts with the graph via Compose canvas and panels |
| `export` | Exports a graph snapshot to JSON (consumed by the docs viewer widget) |

---

## analyzer — extracting and resolving imports

`ImportExtractor` pulls import statements from source using tree-sitter parsers. It detects the language by file extension and supports:

Java · Kotlin · Python · JavaScript · TypeScript · Go · Rust · Dart · C · C++ · Scala · Ruby · PHP

An extracted import is just a string; it has to be linked to a real file before it becomes an edge. `ImportResolver` handles the common cases, and languages with a package manifest get a dedicated resolver.

| Resolver | Basis |
|---|---|
| `GoModResolver` | `go.mod` module path |
| `PubspecResolver` | Dart `pubspec.yaml` package name |
| `TsConfigResolver` | `tsconfig.json` path mappings |

`DeclarationIndex` collects symbol declaration sites, and `StaticCallHierarchySource` gathers static call relations that back the call graph.

---

## graph — model and queries

`CodeGraphProvider` is the interface for graph data. `ImportGraphProvider` implements it, producing per-file and per-project slices.

```kotlin
interface CodeGraphProvider {
    fun nodesForFile(path: Path, text: String): GraphSlice
    fun nodesForProject(activePath: Path?, activeText: String?): GraphSlice
}
```

From the whole-project graph it derives dependency cycles (`projectCycles`), per-file dependent counts (`dependentCountOf`), and a dependency digest (`dependencyDigest`). `ModuleGraph` · `ModuleLayers` · `ModulePath` fold the file graph into modules to form layers, and `SymbolGraph` handles symbol-level relations.

---

## render — two views

The entry composable is `AtlasContent`. A chip at the top switches between two tabs.

| Tab (`AtlasViewTab`) | Content |
|---|---|
| `RELATIONS` | `OverviewCanvas` — the node/edge graph. Zoom, pan, select, open file |
| `ANALYSIS` | `DependencyInsightPanel` — dependencies, dependents, cycles as text insights |

The call graph is drawn by `CallGraphPanel` · `CallsView` as a separate view. `AtlasSearch` · `AtlasSearchBar` find nodes, and `VcsOverlay` overlays change status on the graph. View state lives in `AtlasViewState`.

---

## IDE integration

Atlas opens as an expanded panel (`ExpandedPanel.ATLAS`).

- Editor context menu Show in Atlas — select the current file in the graph
- Shortcut `FOCUS_IN_ATLAS` → `focusInAtlas(path)` — focus the active file in the Relations tab
- Tab switching and panel sizing flow through MVI events (`AtlasViewTabChanged` · `ResizeAtlas` · `FocusInAtlas`)

---

## export — snapshot

`SnapshotExporter` writes the graph to a JSON snapshot. The Atlas widget in the docs viewer reads this snapshot to show the graph without a live IDE.

---

- [Back to index](https://monkshark.github.io/page-ide/#README_en.md)
