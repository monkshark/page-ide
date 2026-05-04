# core

> `page/core/` — shared domain types and constants

A **common base** that breaks direct cross-module dependencies. Every other module depends on `page:core` only, or on nothing at all (see architecture dependency direction)

> 한국어: [core.md](https://monkshark.github.io/PAGE_IDE/#modules/core.md)

---

## Dependencies

| Kind | Content |
|---|---|
| External | none (Kotlin stdlib only) |
| Internal | none |

No UI / Compose / IO. The moment `core` depends on any other module, a cycle appears — so this rule is hard

---

## `PageIdentity`

```kotlin
object PageIdentity {
    const val NAME = "PAGE"
    const val ACRONYM = "Pair · Atlas · Glass · Echo"
    const val VERSION = "0.1.0"
}
```

App name / version / acronym. The window title bar (`TitleBar` in `page:app`) and the (planned) About dialog read from this single source. Version is hard-coded in source rather than fed from the build script — milestone scheme is in [Architecture](https://monkshark.github.io/PAGE_IDE/#guides/architecture_en.md)

---

## Planned

| Type | Purpose |
|---|---|
| `EventBus` | One-way cross-module messaging (avoids `editor` ↔ `pair` direct deps) |
| `WorkspaceEvent` / `EditorEvent` etc. | Event payloads as sealed interfaces |
| `Identity<T>` | Type-safe identifier wrapper at module boundaries |

Filled in as code lands

---

- [Back to index](https://monkshark.github.io/PAGE_IDE/#README_en.md)
- [Architecture](https://monkshark.github.io/PAGE_IDE/#guides/architecture_en.md)
