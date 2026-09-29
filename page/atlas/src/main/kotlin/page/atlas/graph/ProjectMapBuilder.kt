package page.atlas.graph

import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale

fun interface PartDescriber {
    fun describe(dir: Path?, name: String, files: List<Path>): String
}

object ProjectMapBuilder {

    const val MAX_PARTS = 12
    const val OTHER_ID = "other"
    private const val LINK_WEIGHT = 12

    private val TEST_DIRS = setOf("test", "tests", "__tests__", "androidTest", "testFixtures", "jvmTest", "commonTest", "spec", "specs")

    private val MANIFESTS = setOf(
        "build.gradle.kts", "build.gradle", "pom.xml", "package.json", "Cargo.toml", "go.mod",
        "pyproject.toml", "setup.py", "pubspec.yaml", "Package.swift", "Gemfile", "composer.json",
        "CMakeLists.txt", "mix.exs", "deps.edn", "project.clj",
    )

    fun build(
        root: Path,
        files: List<Path>,
        imports: List<Pair<Path, Path>>,
        describer: PartDescriber = StatsDescriber,
        title: String = root.fileName?.toString() ?: root.toString(),
        moduleLinks: List<Pair<Path, Path>> = emptyList(),
    ): ProjectMap {
        val base = root.toAbsolutePath().normalize()
        val sources = files.map { it.toAbsolutePath().normalize() }
            .filter { it.startsWith(base) && !isTest(base, it) && !isBuildScript(it) }
            .distinct()
        if (sources.isEmpty()) return ProjectMap(title, emptyList(), emptyList())

        val all = groupsOf(base, sources)
        val groups = capped(all, linksOf(all, imports, moduleLinks))
        val links = linksOf(groups, imports, moduleLinks)
        val layers = layersOf(groups.map { it.id }.filter { it != OTHER_ID }, links.filter { it.from != OTHER_ID && it.to != OTHER_ID }) + (OTHER_ID to 0)

        val parts = groups.map { g ->
            MapPart(
                id = g.id,
                name = g.name,
                description = describer.describe(g.dir, g.name, g.files),
                dir = g.dir,
                files = g.files.sorted(),
                layer = layers.getValue(g.id),
            )
        }
        return ProjectMap(title, parts, links)
    }

    fun inside(
        map: ProjectMap,
        partId: String,
        root: Path,
        imports: List<Pair<Path, Path>>,
        describer: PartDescriber = StatsDescriber,
    ): ProjectMap {
        val part = map.part(partId) ?: return ProjectMap.EMPTY
        val base = part.dir ?: root
        val mine = part.files.toSet()
        val local = imports.filter { (a, b) ->
            a.toAbsolutePath().normalize() in mine && b.toAbsolutePath().normalize() in mine
        }
        return build(base, part.files, local, describer, title = part.name)
    }

    private fun linksOf(
        groups: List<Group>,
        imports: List<Pair<Path, Path>>,
        moduleLinks: List<Pair<Path, Path>>,
    ): List<MapLink> {
        val owner = HashMap<Path, String>()
        for (g in groups) for (f in g.files) owner[f] = g.id
        val byDir = HashMap<Path, String?>()
        fun ownerOfDir(dir: Path): String? = byDir.getOrPut(dir) {
            groups.firstOrNull { g -> g.files.any { it.startsWith(dir) } }?.id
        }
        val weights = LinkedHashMap<Pair<String, String>, Int>()
        for ((from, to) in imports) {
            val a = owner[from.toAbsolutePath().normalize()] ?: continue
            val b = owner[to.toAbsolutePath().normalize()] ?: continue
            if (a != b) weights.merge(a to b, 1, Int::plus)
        }
        for ((fromDir, toDir) in moduleLinks) {
            val a = ownerOfDir(fromDir.toAbsolutePath().normalize()) ?: continue
            val b = ownerOfDir(toDir.toAbsolutePath().normalize()) ?: continue
            if (a != b) weights.putIfAbsent(a to b, 0)
        }
        return weights.map { (k, w) -> MapLink(k.first, k.second, w) }
    }

    private fun isBuildScript(file: Path): Boolean {
        val name = file.fileName.toString()
        return name.endsWith(".gradle.kts") || name.endsWith(".gradle")
    }

    private fun isTest(base: Path, file: Path): Boolean =
        base.relativize(file).any { it.toString() in TEST_DIRS }

    private class Group(val id: String, val name: String, val dir: Path?, val files: List<Path>)

    private fun groupsOf(base: Path, files: List<Path>): List<Group> {
        val modules = moduleRoots(base, files)
        if (modules.size >= 2) {
            val byModule = LinkedHashMap<Path, MutableList<Path>>()
            val loose = ArrayList<Path>()
            for (f in files) {
                val home = modules.filter { f.startsWith(it) }.maxByOrNull { it.nameCount }
                if (home == null) loose.add(f) else byModule.getOrPut(home) { ArrayList() }.add(f)
            }
            val named = byModule.map { (dir, fs) -> dir to fs.toList() }
            val modular = named.map { (dir, fs) -> Group(idFor(base, dir), nameFor(base, dir, named.map { it.first }), dir, fs) }
            return if (loose.isEmpty()) modular else modular + Group(OTHER_ID, "Other", null, loose.toList())
        }
        return folderGroups(base, files)
    }

    private fun folderGroups(base: Path, files: List<Path>): List<Group> {
        if (files.isEmpty()) return emptyList()
        val top = commonDir(base, files)
        val buckets = LinkedHashMap<Path, MutableList<Path>>()
        for (f in files) {
            val rel = top.relativize(f)
            val key = if (rel.nameCount <= 1) top else top.resolve(rel.getName(0))
            buckets.getOrPut(key) { ArrayList() }.add(f)
        }
        val dirs = buckets.keys.toList()
        return buckets.map { (dir, fs) ->
            val name = if (dir == top) (top.fileName?.toString() ?: "root") else nameFor(base, dir, dirs)
            Group(idFor(base, dir), name, dir, fs.toList())
        }
    }

    private fun commonDir(base: Path, files: List<Path>): Path {
        var dir = base
        while (true) {
            val children = files.map { dir.relativize(it) }
            if (children.any { it.nameCount <= 1 }) return dir
            val first = children.map { it.getName(0) }.toSet()
            if (first.size != 1) return dir
            dir = dir.resolve(first.single())
        }
    }

    private fun moduleRoots(base: Path, files: List<Path>): List<Path> {
        val candidates = HashSet<Path>()
        for (f in files) {
            var dir = f.parent
            while (dir != null && dir.startsWith(base) && dir != base) {
                candidates.add(dir)
                dir = dir.parent
            }
        }
        return candidates.filter { dir -> MANIFESTS.any { Files.isRegularFile(dir.resolve(it)) } }
    }

    private fun capped(groups: List<Group>, links: List<MapLink>): List<Group> {
        val neighbours = HashMap<String, MutableSet<String>>()
        for (l in links) {
            neighbours.getOrPut(l.from) { HashSet() }.add(l.to)
            neighbours.getOrPut(l.to) { HashSet() }.add(l.from)
        }
        fun weight(g: Group): Int = g.files.size + LINK_WEIGHT * (neighbours[g.id]?.size ?: 0)
        val sorted = groups.sortedWith(compareByDescending<Group> { weight(it) }.thenBy { it.name })
        if (sorted.size <= MAX_PARTS) return sorted
        val real = sorted.filter { it.id != OTHER_ID }
        val kept = real.take(MAX_PARTS - 1)
        val rest = real.drop(MAX_PARTS - 1) + sorted.filter { it.id == OTHER_ID }
        return kept + Group(OTHER_ID, "Other", null, rest.flatMap { it.files })
    }

    private fun layersOf(ids: List<String>, links: List<MapLink>): Map<String, Int> {
        val out = links.groupBy({ it.from }, { it.to })
        val component = stronglyConnected(ids, out)
        val componentOut = HashMap<Int, MutableSet<Int>>()
        for (link in links) {
            val a = component.getValue(link.from)
            val b = component.getValue(link.to)
            if (a != b) componentOut.getOrPut(a) { HashSet() }.add(b)
        }
        val depth = HashMap<Int, Int>()
        fun depthOf(c: Int): Int = depth.getOrPut(c) {
            val below = componentOut[c].orEmpty()
            if (below.isEmpty()) 0 else below.maxOf { depthOf(it) } + 1
        }
        return ids.associateWith { depthOf(component.getValue(it)) }
    }

    private fun stronglyConnected(ids: List<String>, out: Map<String, List<String>>): Map<String, Int> {
        val index = HashMap<String, Int>()
        val low = HashMap<String, Int>()
        val onStack = HashSet<String>()
        val stack = ArrayDeque<String>()
        val component = HashMap<String, Int>()
        var counter = 0
        var components = 0
        fun visit(v: String) {
            index[v] = counter
            low[v] = counter
            counter++
            stack.addLast(v)
            onStack.add(v)
            for (w in out[v].orEmpty()) {
                if (w !in index) {
                    visit(w)
                    low[v] = minOf(low.getValue(v), low.getValue(w))
                } else if (w in onStack) {
                    low[v] = minOf(low.getValue(v), index.getValue(w))
                }
            }
            if (low[v] == index[v]) {
                while (true) {
                    val w = stack.removeLast()
                    onStack.remove(w)
                    component[w] = components
                    if (w == v) break
                }
                components++
            }
        }
        for (id in ids) if (id !in index) visit(id)
        return component
    }

    private fun idFor(base: Path, dir: Path): String {
        val rel = base.relativize(dir).toString().replace('\\', '/')
        return rel.ifEmpty { "." }
    }

    private fun nameFor(base: Path, dir: Path, siblings: List<Path>): String {
        val simple = dir.fileName?.toString() ?: return "root"
        val clash = siblings.count { it.fileName?.toString() == simple } > 1
        return if (clash) base.relativize(dir).toString().replace('\\', '/') else simple
    }
}

object StatsDescriber : PartDescriber {
    override fun describe(dir: Path?, name: String, files: List<Path>): String {
        val languages = files.groupingBy { languageOf(it) }.eachCount()
            .filterKeys { it != null }
            .entries.sortedByDescending { it.value }
            .take(2).map { it.key }
        val count = if (files.size == 1) "1 file" else "${files.size} files"
        return if (languages.isEmpty()) count else "$count · ${languages.joinToString(", ")}"
    }

    private fun languageOf(file: Path): String? =
        when (file.fileName.toString().substringAfterLast('.', "").lowercase(Locale.ROOT)) {
            "kt", "kts" -> "Kotlin"
            "java" -> "Java"
            "ts", "tsx" -> "TypeScript"
            "js", "jsx", "mjs", "cjs" -> "JavaScript"
            "py" -> "Python"
            "go" -> "Go"
            "rs" -> "Rust"
            "swift" -> "Swift"
            "dart" -> "Dart"
            "rb" -> "Ruby"
            "php" -> "PHP"
            "c", "h" -> "C"
            "cc", "cpp", "cxx", "hpp", "hh" -> "C++"
            "cs" -> "C#"
            "scala" -> "Scala"
            else -> null
        }
}

class DocsDescriber(
    private val root: Path,
    private val fallback: PartDescriber = StatsDescriber,
) : PartDescriber {

    override fun describe(dir: Path?, name: String, files: List<Path>): String =
        fromReadme(dir) ?: fromInternals(name) ?: fallback.describe(dir, name, files)

    private fun fromReadme(dir: Path?): String? {
        if (dir == null) return null
        val readme = listOf("README.md", "readme.md", "Readme.md").map { dir.resolve(it) }.firstOrNull { Files.isRegularFile(it) }
            ?: return null
        val text = runCatching { Files.readAllLines(readme) }.getOrNull() ?: return null
        val prose = ArrayList<String>()
        var fenced = false
        for (raw in text) {
            val line = raw.trim()
            if (line.startsWith("```") || line.startsWith("~~~")) {
                fenced = !fenced
                continue
            }
            if (!fenced) prose.add(line)
        }
        return prose.asSequence()
            .dropWhile { it.isEmpty() || it.startsWith("#") || it.startsWith("!") || it.startsWith("[!") || it.startsWith("<") || it.startsWith(">") || it.startsWith("|") || it.startsWith("---") }
            .takeWhile { it.isNotEmpty() }
            .joinToString(" ")
            .let(::firstSentence)
    }

    private fun fromInternals(name: String): String? {
        val doc = root.resolve("docs").resolve("internals").resolve(name).resolve("main.md")
        if (!Files.isRegularFile(doc)) return null
        val quote = runCatching { Files.readAllLines(doc) }.getOrNull()
            ?.map { it.trim() }
            ?.firstOrNull { it.startsWith(">") && !it.contains("English:", ignoreCase = true) }
            ?: return null
        val body = quote.removePrefix(">").trim()
        val afterDash = body.substringAfter(" — ", body).trim()
        return firstSentence(afterDash)
    }

    private fun firstSentence(text: String): String? {
        val cleaned = text
            .replace(Regex("`([^`]*)`"), "$1")
            .replace(Regex("\\[([^]]*)]\\([^)]*\\)"), "$1")
            .replace(Regex("(\\*\\*|__)(.+?)\\1"), "$2")
            .replace(Regex("<[^>]+>"), "")
            .trim()
        if (cleaned.isEmpty()) return null
        val end = Regex("(?<=[^.][.!?。])\\s").find(cleaned)?.range?.first
        return (if (end != null) cleaned.substring(0, end) else cleaned).trim()
    }
}
