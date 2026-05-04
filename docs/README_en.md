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

### Modules
- [core](https://monkshark.github.io/PAGE_IDE/#modules/core_en.md) — Shared domain types and constants (`PageIdentity`)
- [editor](https://monkshark.github.io/PAGE_IDE/#modules/editor_en.md) — Text buffer, edit operations, syntax highlighting, tab model (pure logic, no UI dependency)
- [ui](https://monkshark.github.io/PAGE_IDE/#modules/ui_en.md) — Glass design tokens, fonts, syntax palette
- [app](https://monkshark.github.io/PAGE_IDE/#modules/app_en.md) — Composition layer / entry point, Compose panels, shortcuts, dialogs

> `pair`, `atlas`, `echo`, `language`, `runtime`, `git`, `workspace` modules will be added in their respective milestones.

### Features
> Pair / Atlas / Glass / Echo feature docs will be added at the close of each milestone.

---

## External links

- GitHub: <https://github.com/Monkshark/PAGE_IDE>
- Devlog series (Korean): <https://monkshark.github.io/categories/page-개발기/>
