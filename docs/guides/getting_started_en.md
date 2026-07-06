# Getting started

> 한국어: [getting_started.md](https://monkshark.github.io/page-ide/#guides/getting_started.md)

> The shortest path from a clone to a running desktop IDE.

PAGE builds with the Gradle wrapper. The JDK is auto-provisioned by the Gradle toolchain (Foojay), so the wrapper script is all you need to build and run.

---

## Prerequisites

| Item | Note |
|---|---|
| Git | To clone the repo |
| Gradle wrapper | Bundled (`gradlew` / `gradlew.bat`), no separate install |
| JDK | The toolchain downloads JDK 21 on its own; an existing install is reused |

Versions are pinned in the repo — Kotlin 2.1.20, Compose Multiplatform 1.7.3, Gradle 8.14, JDK 21 toolchain.

---

## Clone

```bash
git clone https://github.com/monkshark/page-ide.git
cd page-ide
```

---

## Run

Launch the desktop IDE directly.

```bash
./gradlew :page:app:run
```

On Windows PowerShell use `.\gradlew :page:app:run`; on cmd use `gradlew :page:app:run`.

The entry point is `page.app.MainKt`; the `app` module assembles the other modules and opens the window.

---

## Build · test

```bash
# Build + test every module
./gradlew build

# Test a single module
./gradlew :page:runtime:test
```

The CI gate runs the same `./gradlew build` (ubuntu-latest + Temurin 21). The build must pass before a merge.

---

## Previewing the docs viewer locally

This documentation site is built by bundling the markdown under `docs/` into a single self-contained `index.html`.

```bash
cd docs
node build_viewer.js
```

The generated `docs/index.html` opens in a browser with no server. To serve it statically:

```bash
python -m http.server 8090 --directory docs
```

To rebuild the multiplatform widget islands (the Atlas overview, etc.) locally:

```bash
./gradlew :docs-viewer:wasmJsBrowserDevelopmentExecutableDistribution
```

---

## Workflow

- No direct pushes to `main` — every change goes feature branch → PR → CI → squash merge.
- Features that touch real behavior ship with unit tests. Skeleton / scaffolding is exempt.

---

- [Read the architecture](https://monkshark.github.io/page-ide/#guides/architecture_en.md)
- [Back to index](https://monkshark.github.io/page-ide/#README_en.md)
