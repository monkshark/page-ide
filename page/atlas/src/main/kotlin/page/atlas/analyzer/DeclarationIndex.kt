package page.atlas.analyzer

import java.nio.file.Path

class DeclarationIndex(
    private val workspace: WorkspaceIndex,
    private val analyze: (Path) -> FileAnalysis?,
) {

    private var builtRevision: Long = Long.MIN_VALUE
    private var fqnToFiles: Map<String, List<Path>> = emptyMap()
    private var packageToFiles: Map<String, List<Path>> = emptyMap()
    private var fqnToDecls: Map<String, List<Pair<Path, SymbolDecl>>> = emptyMap()
    private var fileToDecls: Map<Path, List<SymbolDecl>> = emptyMap()

    fun refreshIfStale() {
        workspace.refreshIfStale()
        val revision = workspace.revision()
        if (revision == builtRevision) return
        build(revision)
    }

    fun fileForFqn(fqn: String): Path? = fqnToFiles[fqn]?.singleOrNull()

    fun candidatesForFqn(fqn: String): List<Path> = fqnToFiles[fqn] ?: emptyList()

    fun filesInPackage(packageName: String): List<Path> = packageToFiles[packageName] ?: emptyList()

    fun declarationFor(fqn: String): Pair<Path, Int>? =
        fqnToDecls[fqn]?.singleOrNull()?.let { (file, decl) -> file to decl.line }

    fun declarationsInFile(file: Path): List<SymbolDecl> =
        fileToDecls[file.toAbsolutePath().normalize()] ?: emptyList()

    private fun build(revision: Long) {
        val fqn = HashMap<String, MutableList<Path>>()
        val packages = HashMap<String, MutableList<Path>>()
        val fqnDecls = HashMap<String, MutableList<Pair<Path, SymbolDecl>>>()
        val fileDecls = HashMap<Path, List<SymbolDecl>>()
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
            if (declarations.locations.isNotEmpty()) {
                fileDecls[normalized] = declarations.locations
                for (decl in declarations.locations) {
                    val key =
                        if (declarations.packageName.isEmpty()) decl.name else "${declarations.packageName}.${decl.name}"
                    fqnDecls.getOrPut(key) { mutableListOf() }.add(normalized to decl)
                }
            }
        }
        fqnToFiles = fqn
        packageToFiles = packages
        fqnToDecls = fqnDecls
        fileToDecls = fileDecls
        builtRevision = revision
    }
}
