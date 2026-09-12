<div align="center">

<img src="assets/logo_transparent.svg" alt="PAGE" width="128" />

# PAGE

[![CI](https://github.com/monkshark/page-ide/actions/workflows/ci.yml/badge.svg)](https://github.com/monkshark/page-ide/actions/workflows/ci.yml)
[![Design docs](https://img.shields.io/badge/design%20docs-monkshark.github.io-4C5AB8)](https://monkshark.github.io/page-ide/)
![Kotlin](https://img.shields.io/badge/Kotlin-2.1.20-7F52FF?logo=kotlin&logoColor=white)
![Status](https://img.shields.io/badge/status-pre--alpha-orange)
[![License](https://img.shields.io/badge/license-MIT-green)](LICENSE)

**A multi-language desktop IDE, written in Kotlin with Compose Multiplatform.**

[Design docs](https://monkshark.github.io/page-ide/) · [한국어](README_KR.md)

<!-- demo: record it, then drop the comment and point this at assets/demo.gif
<img src="assets/demo.gif" alt="PAGE in use" width="880" />
-->

</div>

PAGE edits, navigates, and runs code across 24 languages, and installs the language servers and
toolchains it needs by itself. The name is what it is built around — **P**air, **A**tlas,
**G**lass, **E**cho: an AI companion, a code graph, a design system, and a timeline of the work.

> Pre-alpha. It builds itself daily; it is not ready to be your only editor.

## Features

- Language server routing across 24 languages — completion, diagnostics, definitions, rename
- A project-wide symbol index that finds usages, dead code, and unused imports without a server
- Single-file run for 13 languages, with an incremental build cache and a built-in terminal
- Toolchains installed in-app — JDK, Node, Python, Go, Rust, .NET, Dart, Swift, Clang, MSVC
- Code actions with an inline diff, and a review panel for edits that span several files
- Import, call, and module graphs drawn from tree-sitter rather than build metadata
- File dependency exploration with expandable and collapsible branches, source previews, visit history, cycles, and potential impact
- Split panes, folding, inlay hints, nine themes, and one action table behind every shortcut

<sub>Kotlin · Java · Python · TypeScript/JavaScript · Go · Rust · C/C++ · Swift · Dart/Flutter ·
Ruby · PHP · C# · Vue · Svelte · Bash · JSON · YAML · HTML · CSS · SQL · Markdown · Dockerfile</sub>

## Building

There are no releases yet. Gradle provisions the JDK, so Git is the only prerequisite.

```bash
git clone https://github.com/monkshark/page-ide.git
cd page-ide
./gradlew :page:app:run
```

`./gradlew build` compiles every module and runs the tests.

## Design docs

There is no user manual yet — nothing is released to use. What exists is written for whoever
works on PAGE.

- [Overview](https://monkshark.github.io/page-ide/#guides/overview_en.md) — what PAGE is for, and what it will not do
- [Architecture](https://monkshark.github.io/page-ide/#guides/architecture_en.md) — 16 modules, one-way dependencies, stack decisions
- [Getting started](https://monkshark.github.io/page-ide/#guides/getting_started_en.md) — running and debugging PAGE itself
- [Internals](https://monkshark.github.io/page-ide/#README_en.md) — per-module design notes

## Contributing

`main` is protected: branch, open a pull request, and let CI go green before it merges. Code that
does real work comes with tests. See the [architecture guide](https://monkshark.github.io/page-ide/#guides/architecture.md)
before adding a module.

## License

[MIT](LICENSE)

## Contact

[GitHub Issues](https://github.com/monkshark/page-ide/issues) · justinchoo0814@gmail.com · IG@[void___main](https://www.instagram.com/void___main)
