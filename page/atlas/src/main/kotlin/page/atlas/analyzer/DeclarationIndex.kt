package page.atlas.analyzer

import java.nio.file.Path

class DeclarationIndex(
    private val workspace: WorkspaceIndex,
    private val analyze: (Path) -> FileAnalysis?,
) {

    private var builtRevision: Long = Long.MIN_VALUE
    private var fqnToFiles: Map<String, List<Path>> = emptyMap()
    private var packageToFiles: Map<String, List<Path>> = emptyMap()

    fun refreshIfStale() {
        workspace.refreshIfStale()
        val revision = workspace.revision()
        if (revision == builtRevision) return
        build(revision)
    }

    fun fileForFqn(fqn: String): Path? = fqnToFiles[fqn]?.singleOrNull()

    fun candidatesForFqn(fqn: String): List<Path> = fqnToFiles[fqn] ?: emptyList()

    fun filesInPackage(packageName: String): List<Path> = packageToFiles[packageName] ?: emptyList()

    private fun build(revision: Long) {
        val fqn = HashMap<String, MutableList<Path>>()
        val packages = HashMap<String, MutableList<Path>>()
        for (file in workspace.files()) {
            if (!ImportExtractor.supports(file)) continue
            val declarations = analyze(file)?.declarations ?: continue
            if (declarations.symbols.isEmpty()) continue
            val normalized = file.toAbsolutePath().normalize()
            if (declarations.packageName.isNotEmpty()) {
                packages.getOrPut(declarations.packageName) { mutableListOf() }.add(normalized)
            }
            for (symbol in declarations.symbols) {
                val key =
                    if (declarations.packageName.isEmpty()) symbol else "${declarations.packageName}.$symbol"
                fqn.getOrPut(key) { mutableListOf() }.add(normalized)
            }
        }
        fqnToFiles = fqn
        packageToFiles = packages
        builtRevision = revision
    }
}
