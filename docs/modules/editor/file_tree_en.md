# FileTree / TreeNode

> 한국어: [file_tree.md](https://monkshark.github.io/PAGE_IDE/#modules/editor/file_tree.md)

> `page/editor/src/main/kotlin/page/editor/FileTree.kt` — Sidebar file-tree flattening

Takes an `expanded` set and produces a depth-first flattened list of nodes. The data shape consumed by `LazyColumn`.

---

## `TreeNode`

```kotlin
data class TreeNode(
    val path: Path,
    val depth: Int,
    val isDirectory: Boolean,
)
```

One row of the flattened list. `depth` is the indent level (`0` = root).

---

## `listTree`

```kotlin
fun listTree(root: Path, expanded: Set<Path>): List<TreeNode>
```

`root` is added as the first node; if expanded, children are recursively flattened. Unexpanded directories appear as headers only — their children are skipped.

| Sort | Priority |
|---|---|
| Primary | Directories first (`isDirectory == true` ahead of files) |
| Secondary | Filename, lowercased, lexicographic |

If `Files.list` fails (`IOException` / permission), that directory shows up empty — the whole tree doesn't die.

---

## Safety helpers

| Function | Behavior |
|---|---|
| `isDirectorySafe(path)` | `Files.isDirectory` with try/catch. `false` on exception |
| `listChildrenSorted(dir)` | `Files.list` + sort. `null` on exception (treated as no children) |

Guards against broken symlinks, denied folders, and other half-readable mounts so the sidebar keeps working.

---

## Usage

| Location | Purpose |
|---|---|
| `page.app.FileTreePanel` | Renders the `listTree(root, expanded)` result in a `LazyColumn` |

---

- [Back to index](https://monkshark.github.io/PAGE_IDE/#README_en.md)
