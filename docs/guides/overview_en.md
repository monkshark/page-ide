# PAGE Overview

> 한국어: [overview.md](https://monkshark.github.io/PAGE_IDE/#guides/overview.md)

> Multi-language desktop IDE. The name comes from the initials of four core values — **P**air · **A**tlas · **G**lass · **E**cho.

PAGE puts four dimensions on a single page: code (text), code graph (space), work timeline (time), and an AI companion (conversation). Where existing IDEs solve one of these well, PAGE integrates all four into one screen.

---

## The four core values

### Pair — AI companion

A mode where the AI sits beside you and watches the code. Not the outsourcing model where you issue a command and receive a result, but a companion model that observes your work and chimes in briefly.

- **Observer** — Watches the code you write and offers short, unsolicited comments.
- **Chat** — Question/answer with automatic code-context attachment.
- **Agent** — Tool-calling for file edits and command execution. Every change requires explicit user approval.
- **Tutor** — For learners. Asks about the intent of your code and proposes alternatives.

### Atlas — code graph (space)

The file tree is just directory structure. Atlas visualizes modules, functions, and dependencies as nodes and edges.

- Nodes: files / major symbols
- Edges: imports, calls, inheritance
- Zoom/pan, click to open the file, hover to preview
- "Show only nodes reachable from the current file" filter

### Glass — UI aesthetic

If you stare at a screen eight hours a day, it should be a screen you want to look at. Glass is a glassmorphism-based design system.

- Dark mode by default, light mode toggle
- Translucent panels, smooth motion, neon accents
- Focus mode (everything except the current file dims)
- Design tokens v1 layered on top of Compose Multiplatform Material 3

### Echo — keystroke timeline (time)

A git commit is a result snapshot, not a record of work. Echo stores keystrokes in a local SQLite database so you can rewind the work timeline as it happened.

- Debounced auto-recording of keystrokes (offline, local DB)
- Bottom timeline bar, scrub to navigate (read-only view)
- "Restore to this point" button
- Default 30-day retention, configurable

---

## What we will *not* build

Scope protection is part of the concept.

- Mobile/web versions — desktop first
- Our own language server — LSP client only
- Our own debugger engine — DAP later
- Collaborative editing (Live Share style) — deferred
- Marketplace / plugin SDK — post-v2.0

---

## Stages

Four stages, each ending with something shippable.

1. **Foundation** — a good-looking multi-language editor.
2. **Atlas + Git** — code graph plus version control.
3. **Echo** — keystroke timeline.
4. **Pair** — AI companion (multi-provider).

---

## Next document

- [Architecture](https://monkshark.github.io/PAGE_IDE/#guides/architecture_en.md) — Module structure, dependency direction, and stack choices
- [Back to index](https://monkshark.github.io/PAGE_IDE/#README_en.md)
