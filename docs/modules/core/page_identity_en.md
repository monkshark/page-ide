# PageIdentity

> 한국어: [page_identity.md](https://monkshark.github.io/PAGE_IDE/#modules/core/page_identity.md)

> `page/core/src/main/kotlin/page/core/Identity.kt` — App identity constants

The only file in `page:core`. Sits at the root of the dependency graph, so no domain logic lives here.

---

## Definition

```kotlin
object PageIdentity {
    const val NAME = "PAGE"
    const val ACRONYM = "Pair · Atlas · Glass · Echo"
    const val VERSION = "0.1.0"
}
```

`object` + `const val`. Inlined as a JVM constant — zero runtime cost.

---

## Fields

| Field | Value | Purpose |
|---|---|---|
| `NAME` | `"PAGE"` | Window title, dialogs, About |
| `ACRONYM` | `"Pair · Atlas · Glass · Echo"` | Title-bar subtitle |
| `VERSION` | `"0.1.0"` | Title-bar version, update check |

`VERSION` follows SemVer (`MAJOR.MINOR.PATCH`). `MINOR` bumps each milestone (Pair / Atlas / Glass / Echo) close.

---

## Usage

| Location | Purpose |
|---|---|
| `page.app.Main.TitleBar` | Renders `"${NAME} · v${VERSION}"` |
| `Window(title = ...)` | Title shown to the OS window manager |

---

- [Back to index](https://monkshark.github.io/PAGE_IDE/#README_en.md)
