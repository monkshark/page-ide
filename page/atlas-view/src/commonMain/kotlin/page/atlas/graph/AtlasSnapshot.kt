package page.atlas.graph

import page.shared.json.Json
import page.shared.json.asArray
import page.shared.json.asObject
import page.shared.json.asString
import page.shared.path.FilePath

object AtlasSnapshot {

    fun parse(json: String): GraphSlice {
        val root = Json.parse(json).asObject() ?: return GraphSlice.EMPTY
        val nodes = root["nodes"].asArray().mapNotNull { raw ->
            val o = raw.asObject() ?: return@mapNotNull null
            val id = o["id"].asString() ?: return@mapNotNull null
            val kind = nodeKind(o["kind"].asString())
            val path = if (kind == NodeKind.EXTERNAL) null else FilePath.of("/$id")
            GraphNode(id, o["label"].asString() ?: id.substringAfterLast('/'), path, kind)
        }
        val edges = root["edges"].asArray().mapNotNull { raw ->
            val o = raw.asObject() ?: return@mapNotNull null
            val from = o["from"].asString() ?: return@mapNotNull null
            val to = o["to"].asString() ?: return@mapNotNull null
            GraphEdge(from, to, edgeKind(o["kind"].asString()))
        }
        return GraphSlice(nodes, edges)
    }

    private fun nodeKind(name: String?): NodeKind = when (name) {
        "ACTIVE" -> NodeKind.ACTIVE
        "EXTERNAL" -> NodeKind.EXTERNAL
        "SYMBOL" -> NodeKind.SYMBOL
        else -> NodeKind.WORKSPACE_FILE
    }

    private fun edgeKind(name: String?): EdgeKind = when (name) {
        "IMPLEMENTS" -> EdgeKind.IMPLEMENTS
        "EXTENDS" -> EdgeKind.EXTENDS
        "CALLS" -> EdgeKind.CALLS
        else -> EdgeKind.IMPORT
    }
}
