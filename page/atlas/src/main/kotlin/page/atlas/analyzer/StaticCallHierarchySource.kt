package page.atlas.analyzer

import java.net.URI
import java.nio.file.Path
import page.atlas.graph.CallHierarchySource
import page.atlas.graph.SymbolSpec

class StaticCallHierarchySource(
    private val workspace: WorkspaceIndex,
    private val analyze: (Path) -> FileAnalysis?,
) : CallHierarchySource {

    private data class CallerRef(val file: Path, val name: String, val line: Int)

    private var builtRevision: Long = Long.MIN_VALUE
    private var declsByFile: Map<Path, List<SymbolDecl>> = emptyMap()
    private var declsByName: Map<String, List<Pair<Path, SymbolDecl>>> = emptyMap()
    private var callsByFile: Map<Path, List<CallSite>> = emptyMap()
    private var callersByCallee: Map<String, List<CallerRef>> = emptyMap()

    fun refreshIfStale() {
        workspace.refreshIfStale()
        val revision = workspace.revision()
        if (revision == builtRevision) return
        build(revision)
    }

    fun rootAt(file: Path, line: Int): SymbolSpec? {
        refreshIfStale()
        val normalized = file.toAbsolutePath().normalize()
        val enclosing = declsByFile[normalized]
            ?.filter { it.line <= line }
            ?.maxByOrNull { it.line }
            ?: return null
        return specOf(normalized, enclosing)
    }

    override fun outgoing(symbol: SymbolSpec): List<SymbolSpec> {
        refreshIfStale()
        val file = fileOf(symbol) ?: return emptyList()
        return (callsByFile[file] ?: emptyList()).asSequence()
            .filter { it.callerName == symbol.name }
            .mapNotNull { resolveCallee(it.calleeName, file) }
            .distinctBy { it.uri to it.line }
            .toList()
    }

    override fun incoming(symbol: SymbolSpec): List<SymbolSpec> {
        refreshIfStale()
        val target = fileOf(symbol) ?: return emptyList()
        return (callersByCallee[symbol.name] ?: emptyList()).asSequence()
            .filter { caller ->
                val resolved = resolveCallee(symbol.name, caller.file)
                resolved != null && fileOf(resolved) == target && resolved.line == symbol.line
            }
            .mapNotNull { callerSpec(it) }
            .distinctBy { it.uri to it.line }
            .toList()
    }

    private fun resolveCallee(name: String, fromFile: Path): SymbolSpec? {
        declsByFile[fromFile]?.firstOrNull { it.name == name }?.let { return specOf(fromFile, it) }
        val unique = declsByName[name]?.singleOrNull() ?: return null
        return specOf(unique.first, unique.second)
    }

    private fun callerSpec(caller: CallerRef): SymbolSpec? =
        declsByFile[caller.file]?.firstOrNull { it.name == caller.name }?.let { specOf(caller.file, it) }

    private fun specOf(file: Path, decl: SymbolDecl): SymbolSpec =
        SymbolSpec(name = decl.name, detail = null, uri = file.toUri().toString(), line = decl.line)

    private fun fileOf(symbol: SymbolSpec): Path? = try {
        Path.of(URI(symbol.uri)).toAbsolutePath().normalize()
    } catch (e: Exception) {
        null
    }

    private fun build(revision: Long) {
        val byFile = HashMap<Path, List<SymbolDecl>>()
        val byName = HashMap<String, MutableList<Pair<Path, SymbolDecl>>>()
        val calls = HashMap<Path, List<CallSite>>()
        val callers = HashMap<String, MutableList<CallerRef>>()
        for (file in workspace.files()) {
            if (!ImportExtractor.supports(file)) continue
            val analysis = analyze(file) ?: continue
            val normalized = file.toAbsolutePath().normalize()
            val locations = analysis.declarations.locations
            if (locations.isNotEmpty()) {
                byFile[normalized] = locations
                for (decl in locations) {
                    byName.getOrPut(decl.name) { mutableListOf() }.add(normalized to decl)
                }
            }
            if (analysis.calls.isNotEmpty()) {
                calls[normalized] = analysis.calls
                for (call in analysis.calls) {
                    callers.getOrPut(call.calleeName) { mutableListOf() }
                        .add(CallerRef(normalized, call.callerName, call.line))
                }
            }
        }
        declsByFile = byFile
        declsByName = byName
        callsByFile = calls
        callersByCallee = callers
        builtRevision = revision
    }
}
