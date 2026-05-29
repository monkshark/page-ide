package page.editor

import java.nio.file.Path
import java.util.Locale

object SyntaxLexers {
    private const val TREE_SITTER_FLAG = "page.editor.treesitter"

    init {
        val raw = System.getProperty(TREE_SITTER_FLAG) ?: System.getenv("PAGE_EDITOR_TREESITTER")
        if (!raw.isNullOrBlank()) {
            println("[SyntaxLexers] Tree-sitter toggle = '$raw'")
        }
    }

    fun forPath(path: Path): SyntaxLexer? {
        val name = path.fileName?.toString()?.lowercase(Locale.ROOT) ?: return null
        val ext = name.substringAfterLast('.', missingDelimiterValue = "")
        return when {
            ext == "kt" || ext == "kts" -> KotlinLexer
            ext == "java" -> if (treeSitterEnabled("java")) TreeSitterJavaLexer else JavaLexer
            ext == "json" -> JsonLexer
            ext == "py" || ext == "pyi" -> TreeSitterLexers.python
            ext == "js" || ext == "jsx" || ext == "mjs" || ext == "cjs" -> TreeSitterLexers.javascript
            ext == "ts" || ext == "tsx" -> TreeSitterLexers.typescript
            ext == "go" -> TreeSitterLexers.go
            ext == "rs" -> TreeSitterLexers.rust
            ext == "c" || ext == "h" -> TreeSitterLexers.c
            ext == "cc" || ext == "cpp" || ext == "cxx" || ext == "hh" || ext == "hpp" || ext == "hxx" -> TreeSitterLexers.cpp
            ext == "dart" -> TreeSitterLexers.dart
            ext == "sh" || ext == "bash" -> TreeSitterLexers.bash
            ext == "rb" -> TreeSitterLexers.ruby
            ext == "php" -> TreeSitterLexers.php
            ext == "swift" -> TreeSitterLexers.swift
            ext == "yaml" || ext == "yml" -> TreeSitterLexers.yaml
            ext == "html" || ext == "htm" -> TreeSitterLexers.html
            ext == "css" || ext == "scss" || ext == "less" -> TreeSitterLexers.css
            ext == "md" || ext == "markdown" -> TreeSitterLexers.markdown
            ext == "sql" -> TreeSitterLexers.sql
            ext == "vue" -> TreeSitterLexers.vue
            ext == "svelte" -> TreeSitterLexers.svelte
            name == "dockerfile" || ext == "dockerfile" -> TreeSitterLexers.dockerfile
            else -> null
        }
    }

    private fun treeSitterEnabled(language: String): Boolean {
        val v = System.getProperty(TREE_SITTER_FLAG) ?: System.getenv("PAGE_EDITOR_TREESITTER") ?: return false
        if (v.equals("true", ignoreCase = true) || v == "1" || v.equals("all", ignoreCase = true)) return true
        return v.split(',').any { it.trim().equals(language, ignoreCase = true) }
    }
}
