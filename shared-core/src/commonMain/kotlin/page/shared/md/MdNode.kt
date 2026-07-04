package page.shared.md

sealed interface MdNode

data class Document(val children: List<MdNode>) : MdNode

data class Heading(val level: Int, val text: List<Inline>, val slug: String) : MdNode

data class Paragraph(val inlines: List<Inline>) : MdNode

data class CodeBlock(val lang: String?, val code: String) : MdNode

data class BulletList(val items: List<List<MdNode>>, val ordered: Boolean) : MdNode

data class TaskList(val items: List<TaskItem>) : MdNode

data class TaskItem(val checked: Boolean, val inlines: List<Inline>)

data class Table(
    val header: List<List<Inline>>,
    val rows: List<List<List<Inline>>>,
) : MdNode

data class BlockQuote(val children: List<MdNode>) : MdNode

data class Callout(val kind: CalloutKind, val children: List<MdNode>) : MdNode

data class WidgetRef(val name: String, val args: Map<String, String>) : MdNode

enum class CalloutKind { INFO, WARNING, NOTE, TIP, DANGER }

sealed interface Inline

data class Text(val value: String) : Inline

data class CodeSpan(val value: String) : Inline

data class Link(val text: String, val href: String) : Inline

data class Emphasis(val inlines: List<Inline>, val strong: Boolean) : Inline

data class Strikethrough(val inlines: List<Inline>) : Inline
