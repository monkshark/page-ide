package page.atlas.export

import java.nio.file.Files
import java.nio.file.Path
import page.atlas.analyzer.ImportGraphProvider
import page.atlas.graph.GraphSlice
import page.atlas.graph.NodeKind

object SnapshotExporter {

    fun export(root: Path, output: Path) {
        val slice = ImportGraphProvider(root).nodesForProject(null, null)
        val json = toJson(slice, root)
        Files.createDirectories(output.parent)
        Files.writeString(output, json)
    }

    private fun toJson(slice: GraphSlice, root: Path): String {
        val idMap = HashMap<String, String>()
        for (node in slice.nodes) {
            idMap[node.id] = if (node.kind == NodeKind.EXTERNAL) node.id else relativize(node.id, root)
        }
        val sb = StringBuilder()
        sb.append("{\"nodes\":[")
        slice.nodes.forEachIndexed { i, node ->
            if (i > 0) sb.append(',')
            sb.append("{\"id\":").append(quote(idMap.getValue(node.id)))
                .append(",\"label\":").append(quote(node.label))
                .append(",\"kind\":").append(quote(node.kind.name))
                .append('}')
        }
        sb.append("],\"edges\":[")
        slice.edges.forEachIndexed { i, edge ->
            val from = idMap[edge.from] ?: return@forEachIndexed
            val to = idMap[edge.to] ?: return@forEachIndexed
            if (i > 0) sb.append(',')
            sb.append("{\"from\":").append(quote(from))
                .append(",\"to\":").append(quote(to))
                .append(",\"kind\":").append(quote(edge.kind.name))
                .append('}')
        }
        sb.append("]}")
        return sb.toString()
    }

    private fun relativize(id: String, root: Path): String = try {
        root.relativize(Path.of(id)).toString().replace('\\', '/')
    } catch (e: Exception) {
        id
    }

    private fun quote(value: String): String {
        val sb = StringBuilder(value.length + 2)
        sb.append('"')
        for (c in value) when (c) {
            '"' -> sb.append("\\\"")
            '\\' -> sb.append("\\\\")
            '\n' -> sb.append("\\n")
            '\r' -> sb.append("\\r")
            '\t' -> sb.append("\\t")
            else -> if (c < ' ') sb.append("\\u").append(c.code.toString(16).padStart(4, '0')) else sb.append(c)
        }
        sb.append('"')
        return sb.toString()
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val root = Path.of(args.getOrElse(0) { "." }).toAbsolutePath().normalize()
        val output = Path.of(args.getOrElse(1) { "atlas-snapshot.json" }).toAbsolutePath().normalize()
        export(root, output)
    }
}
