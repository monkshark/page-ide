# Dependency Graph

Atlas reads real imports across your workspace and draws them as one graph. No re-analysis runs in the browser — the viewer replays the exact snapshot PAGE produced.

The graph model is intentionally slim. Each node is a source file; each edge is a resolved import. Open a node to walk its neighborhood, or click through to the file in the desktop IDE.

## How resolution works

Imports resolve per language. Names that do not resolve are dropped rather than guessed.

| Language | Resolved by |
| --- | --- |
| Go | `go.mod` module path |
| Python | `__init__.py` packages |
| Dart | `pubspec.yaml` name |

A short walk of the neighborhood algorithm:

```kotlin
fun neighborhood(root: NodeId, depth: Int = 2): GraphSlice {
    val seen = mutableSetOf(root)
    // breadth-first, capped at 100 nodes
    return GraphSlice(seen)
}
```

## Notes

- Imports are **resolved**, not textual.
- Unresolved names are ~~guessed~~ dropped.
- See the [snapshot embed](#snapshot-embed) section.

### Checklist

- [x] Parse imports
- [x] Resolve per language
- [ ] Render in browser

:::info
The viewer swaps the exact Glass palette PAGE ships, so a code block here matches your editor to the hex.
:::

:::warning
The embed replays a build-time analysis. It will **not** re-scan a repo you point it at — that needs the desktop app.
:::

> Atlas favors accuracy over guesses: fewer edges, but every edge is real.

@Render(AtlasDemo, id=page:atlas, depth=2)
