package page.atlas.analyzer

import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

class WorkspaceIndex(private val root: Path) {

    private var files: List<Path> = emptyList()
    private var lastScan = 0L

    fun refreshIfStale(ttlMs: Long = 30_000) {
        val now = System.currentTimeMillis()
        if (files.isNotEmpty() && now - lastScan < ttlMs) return
        lastScan = now
        files = scan()
    }

    fun files(): List<Path> = files

    fun revision(): Long = lastScan

    private fun scan(): List<Path> {
        if (!Files.isDirectory(root)) return emptyList()
        val collected = ArrayList<Path>()
        Files.walkFileTree(root, object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                val name = dir.fileName?.toString() ?: return FileVisitResult.CONTINUE
                if (dir != root && (name in SKIP_DIRS || name.startsWith("."))) return FileVisitResult.SKIP_SUBTREE
                return FileVisitResult.CONTINUE
            }

            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                if (collected.size >= MAX_FILES) return FileVisitResult.TERMINATE
                val ext = file.fileName.toString().substringAfterLast('.', "").lowercase()
                if (ext in SOURCE_EXTS) collected.add(file)
                return FileVisitResult.CONTINUE
            }

            override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult = FileVisitResult.CONTINUE
        })
        collected.sortBy { it.toString() }
        return collected
    }

    private companion object {
        const val MAX_FILES = 20_000
        val SKIP_DIRS = setOf(".git", "build", "out", "node_modules", "target", ".gradle")
        val SOURCE_EXTS = setOf(
            "java", "kt", "kts", "py", "pyi", "js", "jsx", "mjs", "cjs", "ts", "tsx", "go", "rs", "dart",
        )
    }
}

object ImportResolver {

    fun resolve(
        raw: RawImport,
        activeFile: Path,
        index: WorkspaceIndex,
        declIndex: DeclarationIndex? = null,
    ): Path? {
        return when (extOf(activeFile)) {
            "js", "jsx", "mjs", "cjs", "ts", "tsx" -> resolveJsRelative(raw, activeFile)
            "py", "pyi" ->
                if (raw.relative) resolvePythonRelative(raw, activeFile)
                else resolveDotted(raw, activeFile, index, listOf("py", "pyi"), declIndex)
            "java", "kt", "kts" -> resolveDotted(raw, activeFile, index, listOf("java", "kt", "kts"), declIndex)
            "go" -> resolveGo(raw, activeFile, index)
            "rs" -> resolveRust(raw, index)
            "dart" -> resolveDart(raw, activeFile, index)
            else -> null
        }
    }

    private fun resolveJsRelative(raw: RawImport, activeFile: Path): Path? {
        if (!raw.relative) return null
        val parent = activeFile.parent ?: return null
        val base = parent.resolve(raw.target).normalize()
        val name = base.fileName?.toString() ?: return null
        val probes = buildList {
            add(base)
            for (suffix in listOf(".ts", ".tsx", ".js", ".jsx")) add(base.resolveSibling(name + suffix))
            for (idx in listOf("index.ts", "index.tsx", "index.js", "index.jsx")) add(base.resolve(idx))
        }
        return probes.firstOrNull { Files.isRegularFile(it) }
    }

    private fun resolveDotted(
        raw: RawImport,
        activeFile: Path,
        index: WorkspaceIndex,
        exts: List<String>,
        declIndex: DeclarationIndex?,
    ): Path? {
        index.refreshIfStale()
        if (declIndex != null) {
            declIndex.refreshIfStale()
            declIndex.fileForFqn(raw.target)?.let { if (extOf(it) in exts) return it }
            val enclosing = raw.target.substringBeforeLast('.', "")
            if (enclosing.isNotEmpty()) {
                declIndex.fileForFqn(enclosing)?.let { if (extOf(it) in exts) return it }
            }
        }
        val segments = raw.target.split('.').filter { it.isNotEmpty() }
        if (segments.isEmpty()) return null
        val active = normalized(activeFile)
        for (depth in segments.size downTo 1) {
            val rel = segments.take(depth).joinToString("/")
            val candidates = index.files().filter { file ->
                val s = normalized(file)
                exts.any { ext -> s.endsWith("/$rel.$ext") }
            }
            if (candidates.isNotEmpty()) {
                return candidates.maxByOrNull { commonPrefixLength(normalized(it), active) }
            }
        }
        return null
    }

    private fun resolvePythonRelative(raw: RawImport, activeFile: Path): Path? {
        val dots = raw.target.takeWhile { it == '.' }.length
        if (dots == 0) return null
        val rest = raw.target.drop(dots)
        var dir = activeFile.parent ?: return null
        repeat(dots - 1) { dir = dir.parent ?: return null }
        val base = if (rest.isEmpty()) dir else dir.resolve(rest.replace('.', '/'))
        val name = base.fileName?.toString() ?: return null
        val probes = listOf(
            base.resolveSibling("$name.py"),
            base.resolveSibling("$name.pyi"),
            base.resolve("__init__.py"),
        )
        return probes.firstOrNull { Files.isRegularFile(it) }
    }

    private fun resolveGo(raw: RawImport, activeFile: Path, index: WorkspaceIndex): Path? {
        index.refreshIfStale()
        GoModResolver.resolve(activeFile, raw.target, index)?.let { return it }
        val segment = raw.target.substringAfterLast('/')
        if (segment.isEmpty()) return null
        return index.files().firstOrNull { file ->
            file.fileName.toString().endsWith(".go") && file.parent?.fileName?.toString() == segment
        }
    }

    private fun resolveRust(raw: RawImport, index: WorkspaceIndex): Path? {
        if (!raw.relative) return null
        index.refreshIfStale()
        val segments = raw.target.split("::").filter { it.isNotEmpty() }.drop(1)
        for (depth in segments.size - 1 downTo 1) {
            val rel = segments.take(depth).joinToString("/")
            val hit = index.files().firstOrNull { file ->
                val s = normalized(file)
                s.endsWith("src/$rel.rs") || s.endsWith("src/$rel/mod.rs")
            }
            if (hit != null) return hit
        }
        return null
    }

    private fun resolveDart(raw: RawImport, activeFile: Path, index: WorkspaceIndex): Path? {
        val target = raw.target
        if (target.startsWith("dart:")) return null
        if (target.startsWith("package:")) {
            val rest = target.removePrefix("package:").substringAfter('/', "")
            if (rest.isEmpty()) return null
            index.refreshIfStale()
            val active = normalized(activeFile)
            val candidates = index.files().filter { normalized(it).endsWith("/lib/$rest") }
            return candidates.maxByOrNull { commonPrefixLength(normalized(it), active) }
        }
        val parent = activeFile.parent ?: return null
        return parent.resolve(target).normalize().takeIf { Files.isRegularFile(it) }
    }

    private fun normalized(path: Path): String = path.toString().replace('\\', '/')

    private fun commonPrefixLength(a: String, b: String): Int {
        val max = minOf(a.length, b.length)
        var i = 0
        while (i < max && a[i] == b[i]) i++
        return i
    }

    private fun extOf(path: Path): String =
        path.fileName?.toString()?.substringAfterLast('.', "")?.lowercase() ?: ""
}
