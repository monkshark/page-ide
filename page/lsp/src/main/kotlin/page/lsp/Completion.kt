package page.lsp

import org.eclipse.lsp4j.InsertTextFormat as LspInsertTextFormat

enum class CompletionItemKind {
    TEXT, METHOD, FUNCTION, CONSTRUCTOR, FIELD, VARIABLE, CLASS, INTERFACE,
    MODULE, PROPERTY, UNIT, VALUE, ENUM, KEYWORD, SNIPPET, COLOR, FILE,
    REFERENCE, FOLDER, ENUM_MEMBER, CONSTANT, STRUCT, EVENT, OPERATOR,
    TYPE_PARAMETER, OTHER;

    companion object {
        fun fromLsp(k: org.eclipse.lsp4j.CompletionItemKind?): CompletionItemKind = when (k) {
            org.eclipse.lsp4j.CompletionItemKind.Text -> TEXT
            org.eclipse.lsp4j.CompletionItemKind.Method -> METHOD
            org.eclipse.lsp4j.CompletionItemKind.Function -> FUNCTION
            org.eclipse.lsp4j.CompletionItemKind.Constructor -> CONSTRUCTOR
            org.eclipse.lsp4j.CompletionItemKind.Field -> FIELD
            org.eclipse.lsp4j.CompletionItemKind.Variable -> VARIABLE
            org.eclipse.lsp4j.CompletionItemKind.Class -> CLASS
            org.eclipse.lsp4j.CompletionItemKind.Interface -> INTERFACE
            org.eclipse.lsp4j.CompletionItemKind.Module -> MODULE
            org.eclipse.lsp4j.CompletionItemKind.Property -> PROPERTY
            org.eclipse.lsp4j.CompletionItemKind.Unit -> UNIT
            org.eclipse.lsp4j.CompletionItemKind.Value -> VALUE
            org.eclipse.lsp4j.CompletionItemKind.Enum -> ENUM
            org.eclipse.lsp4j.CompletionItemKind.Keyword -> KEYWORD
            org.eclipse.lsp4j.CompletionItemKind.Snippet -> SNIPPET
            org.eclipse.lsp4j.CompletionItemKind.Color -> COLOR
            org.eclipse.lsp4j.CompletionItemKind.File -> FILE
            org.eclipse.lsp4j.CompletionItemKind.Reference -> REFERENCE
            org.eclipse.lsp4j.CompletionItemKind.Folder -> FOLDER
            org.eclipse.lsp4j.CompletionItemKind.EnumMember -> ENUM_MEMBER
            org.eclipse.lsp4j.CompletionItemKind.Constant -> CONSTANT
            org.eclipse.lsp4j.CompletionItemKind.Struct -> STRUCT
            org.eclipse.lsp4j.CompletionItemKind.Event -> EVENT
            org.eclipse.lsp4j.CompletionItemKind.Operator -> OPERATOR
            org.eclipse.lsp4j.CompletionItemKind.TypeParameter -> TYPE_PARAMETER
            null -> OTHER
        }
    }
}

data class CompletionEdit(
    val startLine: Int,
    val startCharacter: Int,
    val endLine: Int,
    val endCharacter: Int,
    val newText: String,
)

data class CompletionItem(
    val label: String,
    val kind: CompletionItemKind,
    val detail: String? = null,
    val documentation: String? = null,
    val insertText: String,
    val isSnippet: Boolean,
    val edit: CompletionEdit? = null,
    val filterText: String = label,
    val sortText: String = label,
) {
    companion object {
        fun fromLsp(item: org.eclipse.lsp4j.CompletionItem): CompletionItem {
            val labelText = item.label ?: ""
            val explicitInsert = item.insertText
            val edit = item.textEdit?.left?.let { te ->
                CompletionEdit(
                    startLine = te.range.start.line,
                    startCharacter = te.range.start.character,
                    endLine = te.range.end.line,
                    endCharacter = te.range.end.character,
                    newText = te.newText ?: "",
                )
            }
            val resolvedInsert = edit?.newText ?: explicitInsert ?: labelText
            val docText = item.documentation?.let { docHolder ->
                if (docHolder.isLeft) docHolder.left
                else docHolder.right?.value
            }
            return CompletionItem(
                label = labelText,
                kind = CompletionItemKind.fromLsp(item.kind),
                detail = item.detail,
                documentation = docText,
                insertText = resolvedInsert,
                isSnippet = item.insertTextFormat == LspInsertTextFormat.Snippet,
                edit = edit,
                filterText = item.filterText ?: labelText,
                sortText = item.sortText ?: labelText,
            )
        }
    }
}

data class CompletionList(
    val isIncomplete: Boolean,
    val items: List<CompletionItem>,
) {
    companion object {
        val EMPTY: CompletionList = CompletionList(isIncomplete = false, items = emptyList())

        fun fromLsp(result: org.eclipse.lsp4j.CompletionList): CompletionList = CompletionList(
            isIncomplete = result.isIncomplete,
            items = result.items.orEmpty().map(CompletionItem::fromLsp),
        )

        fun fromLspItems(items: List<org.eclipse.lsp4j.CompletionItem>): CompletionList = CompletionList(
            isIncomplete = false,
            items = items.map(CompletionItem::fromLsp),
        )
    }
}
