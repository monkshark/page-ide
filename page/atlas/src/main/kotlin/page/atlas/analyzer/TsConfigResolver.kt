package page.atlas.analyzer

import java.nio.file.Files
import java.nio.file.Path

object TsConfigResolver {

    private data class Config(val paths: Map<String, List<String>>, val bareBase: Path?)

    private data class Cached(val mtime: Long, val config: Config?)

    private val cache = HashMap<String, Cached>()

    private val PROBE_SUFFIXES = listOf(".ts", ".tsx", ".d.ts", ".js", ".jsx", ".mjs", ".cjs", ".vue", ".svelte")
    private val INDEX_FILES = listOf("index.ts", "index.tsx", "index.js", "index.jsx", "index.mjs")

    fun resolve(activeFile: Path, rawTarget: String): Path? {
        if (rawTarget.isEmpty() || rawTarget.startsWith("./") || rawTarget.startsWith("../") || rawTarget.startsWith("/")) {
            return null
        }
        val configFile = findConfig(activeFile) ?: return null
        val config = load(configFile, 0) ?: return null
        for ((pattern, templates) in config.paths) {
            val matched = matchPattern(pattern, rawTarget) ?: continue
            for (template in templates) {
                val candidate = Path.of(template.replace("*", matched)).normalize()
                probe(candidate)?.let { return it }
            }
        }
        config.bareBase?.let { base -> probe(base.resolve(rawTarget).normalize())?.let { return it } }
        return null
    }

    private fun probe(base: Path): Path? {
        val name = base.fileName?.toString() ?: return null
        val probes = buildList {
            add(base)
            for (suffix in PROBE_SUFFIXES) add(base.resolveSibling(name + suffix))
            for (index in INDEX_FILES) add(base.resolve(index))
        }
        return probes.firstOrNull { Files.isRegularFile(it) }
    }

    private fun matchPattern(pattern: String, target: String): String? {
        val star = pattern.indexOf('*')
        if (star < 0) return if (pattern == target) "" else null
        val prefix = pattern.substring(0, star)
        val suffix = pattern.substring(star + 1)
        if (target.length < prefix.length + suffix.length) return null
        if (!target.startsWith(prefix) || !target.endsWith(suffix)) return null
        return target.substring(prefix.length, target.length - suffix.length)
    }

    private fun findConfig(activeFile: Path): Path? {
        var dir = activeFile.parent
        var hops = 0
        while (dir != null && hops < 40) {
            for (name in listOf("tsconfig.json", "jsconfig.json")) {
                val candidate = dir.resolve(name)
                if (Files.isRegularFile(candidate)) return candidate
            }
            dir = dir.parent
            hops++
        }
        return null
    }

    private fun load(configFile: Path, depth: Int): Config? {
        if (depth > 5) return null
        val mtime = try {
            Files.getLastModifiedTime(configFile).toMillis()
        } catch (e: Exception) {
            return null
        }
        if (depth == 0) {
            cache[configFile.toString()]?.let { if (it.mtime == mtime) return it.config }
        }
        val parsed = parse(configFile, depth)
        if (depth == 0) cache[configFile.toString()] = Cached(mtime, parsed)
        return parsed
    }

    private fun parse(configFile: Path, depth: Int): Config? {
        val text = try {
            stripComments(Files.readString(configFile))
        } catch (e: Exception) {
            return null
        }
        val configDir = configFile.parent ?: return null
        val baseUrl = extractString(text, "baseUrl")
        val pathsBase = if (baseUrl != null) configDir.resolve(baseUrl).normalize() else configDir
        val paths = LinkedHashMap<String, List<String>>()
        val baseString = pathsBase.toString().replace('\\', '/').trimEnd('/')
        for ((pattern, targets) in extractPaths(text)) {
            paths[pattern] = targets.map { "$baseString/${it.trimStart('/')}" }
        }
        var bareBase = if (baseUrl != null) pathsBase else null

        val extends = extractString(text, "extends")
        if (extends != null) {
            val parentFile = resolveExtends(configDir, extends)
            val parent = parentFile?.let { load(it, depth + 1) }
            if (parent != null) {
                val merged = LinkedHashMap(parent.paths)
                merged.putAll(paths)
                return Config(merged, bareBase ?: parent.bareBase)
            }
        }
        return Config(paths, bareBase)
    }

    private fun resolveExtends(configDir: Path, extends: String): Path? {
        if (extends.startsWith("./") || extends.startsWith("../") || extends.startsWith("/")) {
            val base = configDir.resolve(extends).normalize()
            val withExt = if (base.fileName.toString().endsWith(".json")) base else Path.of("$base.json")
            return withExt.takeIf { Files.isRegularFile(it) }
        }
        return null
    }

    private fun stripComments(raw: String): String {
        val out = StringBuilder(raw.length)
        var i = 0
        var inString = false
        while (i < raw.length) {
            val c = raw[i]
            if (inString) {
                out.append(c)
                if (c == '\\' && i + 1 < raw.length) {
                    out.append(raw[i + 1])
                    i += 2
                    continue
                }
                if (c == '"') inString = false
                i++
                continue
            }
            if (c == '"') {
                inString = true
                out.append(c)
                i++
                continue
            }
            if (c == '/' && i + 1 < raw.length && raw[i + 1] == '/') {
                while (i < raw.length && raw[i] != '\n') i++
                continue
            }
            if (c == '/' && i + 1 < raw.length && raw[i + 1] == '*') {
                i += 2
                while (i + 1 < raw.length && !(raw[i] == '*' && raw[i + 1] == '/')) i++
                i += 2
                continue
            }
            out.append(c)
            i++
        }
        return out.toString()
    }

    private fun extractString(json: String, key: String): String? {
        val regex = Regex("\"" + Regex.escape(key) + "\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"")
        val match = regex.find(json) ?: return null
        return unescape(match.groupValues[1])
    }

    private fun extractPaths(json: String): Map<String, List<String>> {
        val anchor = Regex("\"paths\"\\s*:\\s*\\{").find(json) ?: return emptyMap()
        val open = anchor.range.last
        var depth = 0
        var end = -1
        var inString = false
        var i = open
        while (i < json.length) {
            val c = json[i]
            if (inString) {
                if (c == '\\') {
                    i += 2
                    continue
                }
                if (c == '"') inString = false
            } else {
                when (c) {
                    '"' -> inString = true
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) {
                            end = i
                            break
                        }
                    }
                }
            }
            i++
        }
        if (end < 0) return emptyMap()
        val body = json.substring(open + 1, end)
        val entry = Regex("\"((?:\\\\.|[^\"\\\\])*)\"\\s*:\\s*\\[([^\\]]*)\\]")
        val targetString = Regex("\"((?:\\\\.|[^\"\\\\])*)\"")
        val result = LinkedHashMap<String, List<String>>()
        for (m in entry.findAll(body)) {
            val pattern = unescape(m.groupValues[1])
            val targets = targetString.findAll(m.groupValues[2]).map { unescape(it.groupValues[1]) }.toList()
            if (targets.isNotEmpty()) result[pattern] = targets
        }
        return result
    }

    private fun unescape(value: String): String =
        value.replace("\\\\", "\\").replace("\\\"", "\"").replace("\\/", "/")
}
