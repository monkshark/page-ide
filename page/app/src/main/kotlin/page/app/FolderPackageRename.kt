package page.app

import page.runtime.*

import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Collectors

object FolderPackageRename {

    fun computePackageMap(
        oldFolder: Path,
        newFolderName: String,
        readText: (Path) -> String?,
    ): Map<String, String> {
        val oldFolderName = oldFolder.fileName?.toString().orEmpty()
        if (oldFolderName.isEmpty() || newFolderName.isEmpty() || oldFolderName == newFolderName) return emptyMap()
        if (!Files.isDirectory(oldFolder)) return emptyMap()

        val ktFiles = runCatching {
            Files.walk(oldFolder).use { stream ->
                stream
                    .filter { Files.isRegularFile(it) && isKotlin(it) }
                    .map { it.toAbsolutePath().normalize() }
                    .collect(Collectors.toList())
            }
        }.getOrDefault(emptyList())
        if (ktFiles.isEmpty()) return emptyMap()

        val out = LinkedHashMap<String, String>()
        val folderAbs = oldFolder.toAbsolutePath().normalize()
        for (file in ktFiles) {
            val text = readText(file) ?: continue
            val pkg = FileSymbolRename.readPackageDeclaration(text) ?: continue
            val parent = file.parent ?: continue
            val relParent = runCatching { folderAbs.relativize(parent) }.getOrNull() ?: continue
            val relSegments = relParent.iterator().asSequence()
                .map { it.toString() }
                .filter { it.isNotEmpty() && it != "." }
                .toList()

            val expectedTail = buildList {
                add(oldFolderName)
                addAll(relSegments)
            }
            val pkgSegments = pkg.split('.').filter { it.isNotEmpty() }
            if (pkgSegments.size < expectedTail.size) continue
            val tail = pkgSegments.takeLast(expectedTail.size)
            if (tail != expectedTail) continue

            val newTail = buildList {
                add(newFolderName)
                addAll(relSegments)
            }
            val newSegments = pkgSegments.dropLast(expectedTail.size) + newTail
            val newPkg = newSegments.joinToString(".")
            if (newPkg == pkg) continue
            out[pkg] = newPkg
        }
        return out
    }

    fun computePackageMapForMove(
        oldFolder: Path,
        newFolder: Path,
        workspaceRoot: Path?,
        readText: (Path) -> String?,
    ): Map<String, String> {
        if (!Files.isDirectory(oldFolder)) return emptyMap()
        val oldAbs = oldFolder.toAbsolutePath().normalize()
        val newAbs = newFolder.toAbsolutePath().normalize()
        if (oldAbs == newAbs) return emptyMap()

        val newParent = newAbs.parent ?: return emptyMap()
        val newFolderName = newAbs.fileName?.toString().orEmpty()
        if (newFolderName.isEmpty()) return emptyMap()

        val ktFiles = runCatching {
            Files.walk(oldFolder).use { stream ->
                stream
                    .filter { Files.isRegularFile(it) && isKotlin(it) }
                    .map { it.toAbsolutePath().normalize() }
                    .collect(Collectors.toList())
            }
        }.getOrDefault(emptyList())
        if (ktFiles.isEmpty()) return emptyMap()

        val newBasePrefix = inferPackagePrefix(newParent, workspaceRoot, readText)
        val out = LinkedHashMap<String, String>()
        for (file in ktFiles) {
            val text = readText(file) ?: continue
            val pkg = FileSymbolRename.readPackageDeclaration(text) ?: continue
            val parent = file.parent ?: continue
            val relParent = runCatching { oldAbs.relativize(parent) }.getOrNull() ?: continue
            val relSegments = relParent.iterator().asSequence()
                .map { it.toString() }
                .filter { it.isNotEmpty() && it != "." }
                .toList()

            val newTail = buildList {
                if (newBasePrefix.isNotEmpty()) addAll(newBasePrefix.split('.').filter { it.isNotEmpty() })
                add(newFolderName)
                addAll(relSegments)
            }
            val newPkg = newTail.joinToString(".")
            if (newPkg == pkg) continue
            out[pkg] = newPkg
        }
        return out
    }

    private fun inferPackagePrefix(
        startFolder: Path,
        workspaceRoot: Path?,
        readText: (Path) -> String?,
    ): String {
        var current: Path? = startFolder
        val boundary = workspaceRoot?.toAbsolutePath()?.normalize()
        while (current != null) {
            val sample = runCatching {
                Files.list(current).use { stream ->
                    stream
                        .filter { Files.isRegularFile(it) && isKotlin(it) }
                        .findFirst()
                        .orElse(null)
                }
            }.getOrNull()
            if (sample != null) {
                val text = readText(sample) ?: ""
                val pkg = FileSymbolRename.readPackageDeclaration(text)
                if (pkg != null) {
                    val rel = runCatching { current.toAbsolutePath().normalize().relativize(sample.parent.toAbsolutePath().normalize()) }
                        .getOrNull()
                    val sampleRelSegments = rel?.iterator()?.asSequence()
                        ?.map { it.toString() }
                        ?.filter { it.isNotEmpty() && it != "." }
                        ?.toList()
                        .orEmpty()
                    val pkgSegments = pkg.split('.').filter { it.isNotEmpty() }
                    val anchor = if (sampleRelSegments.isEmpty()) pkgSegments
                    else if (pkgSegments.size >= sampleRelSegments.size &&
                        pkgSegments.takeLast(sampleRelSegments.size) == sampleRelSegments) {
                        pkgSegments.dropLast(sampleRelSegments.size)
                    } else pkgSegments
                    return anchor.joinToString(".")
                }
            }
            if (current.toAbsolutePath().normalize() == boundary) break
            current = current.parent
        }
        return ""
    }

    fun rewritePackageLine(text: String, newPkg: String): String? {
        if (text.isEmpty() || newPkg.isEmpty()) return null
        val lines = text.split('\n').toMutableList()
        for (i in lines.indices) {
            val raw = lines[i]
            val trimmed = raw.trimStart()
            if (trimmed.isEmpty()) continue
            if (trimmed.startsWith("//")) continue
            if (trimmed.startsWith("@") || trimmed.startsWith("/*")) continue
            if (!trimmed.startsWith("package")) return null
            if (trimmed.length > 7 && trimmed[7].isJavaIdentifierPart()) return null
            val leading = raw.substring(0, raw.length - trimmed.length)
            val afterKeyword = trimmed.substring(7)
            val afterSpace = afterKeyword.trimStart()
            val spaceLen = afterKeyword.length - afterSpace.length
            val nameEnd = afterSpace.indexOfFirst { !(it.isJavaIdentifierPart() || it == '.') }
            val tail = if (nameEnd < 0) "" else afterSpace.substring(nameEnd)
            val rebuilt = leading + "package" + afterKeyword.substring(0, spaceLen) + newPkg + tail
            if (rebuilt == raw) return null
            lines[i] = rebuilt
            return lines.joinToString("\n")
        }
        return null
    }

    fun rewriteImports(text: String, packageMap: Map<String, String>): String? {
        if (text.isEmpty() || packageMap.isEmpty()) return null
        val sortedKeys = packageMap.keys.sortedByDescending { it.length }
        val lines = text.split('\n').toMutableList()
        var changed = false
        for (i in lines.indices) {
            val raw = lines[i]
            val trimmed = raw.trimStart()
            if (!trimmed.startsWith("import")) continue
            if (trimmed.length > 6 && trimmed[6].isJavaIdentifierPart()) continue
            val leading = raw.substring(0, raw.length - trimmed.length)
            val afterKeyword = trimmed.substring(6)
            val afterSpace = afterKeyword.trimStart()
            val spaceLen = afterKeyword.length - afterSpace.length
            if (spaceLen == 0) continue
            val fqnEnd = afterSpace.indexOfFirst { !(it.isJavaIdentifierPart() || it == '.') }
            val fqn = if (fqnEnd < 0) afterSpace else afterSpace.substring(0, fqnEnd)
            val tail = if (fqnEnd < 0) "" else afterSpace.substring(fqnEnd)
            val newFqn = remapImportFqn(fqn, sortedKeys, packageMap) ?: continue
            if (newFqn == fqn) continue
            lines[i] = leading + "import" + afterKeyword.substring(0, spaceLen) + newFqn + tail
            changed = true
        }
        return if (changed) lines.joinToString("\n") else null
    }

    fun planSingleFileMove(
        oldFile: Path,
        newParent: Path,
        workspaceRoot: Path?,
        readText: (Path) -> String?,
    ): SingleFileMovePlan? {
        if (!isKotlin(oldFile)) return null
        val text = readText(oldFile) ?: return null
        val oldPkg = FileSymbolRename.readPackageDeclaration(text) ?: return null
        val fileName = oldFile.fileName?.toString().orEmpty()
        val stem = FileSymbolRename.stripKotlinExtension(fileName) ?: return null
        val newBase = inferPackagePrefix(newParent.toAbsolutePath().normalize(), workspaceRoot, readText)
        val newPkg = if (newBase.isEmpty()) "" else newBase
        if (newPkg == oldPkg) return null
        val newSelfText = if (newPkg.isEmpty()) text else rewritePackageLine(text, newPkg) ?: text
        val importKey = if (oldPkg.isEmpty()) stem else "$oldPkg.$stem"
        val importValue = if (newPkg.isEmpty()) stem else "$newPkg.$stem"
        return SingleFileMovePlan(
            oldPackage = oldPkg,
            newPackage = newPkg,
            stem = stem,
            newSelfText = if (newSelfText == text) null else newSelfText,
            importRewriteMap = mapOf(importKey to importValue),
        )
    }

    data class SingleFileMovePlan(
        val oldPackage: String,
        val newPackage: String,
        val stem: String,
        val newSelfText: String?,
        val importRewriteMap: Map<String, String>,
    )

    fun rewriteFileInRenamedFolder(text: String, packageMap: Map<String, String>): String? {
        if (text.isEmpty() || packageMap.isEmpty()) return null
        val currentPkg = FileSymbolRename.readPackageDeclaration(text)
        val afterPackage = if (currentPkg != null) {
            val newPkg = packageMap[currentPkg]
            if (newPkg != null) rewritePackageLine(text, newPkg) ?: text else text
        } else text
        val afterImports = rewriteImports(afterPackage, packageMap) ?: afterPackage
        return if (afterImports != text) afterImports else null
    }

    private fun remapImportFqn(
        fqn: String,
        sortedKeys: List<String>,
        packageMap: Map<String, String>,
    ): String? {
        if (fqn.isEmpty()) return null
        for (key in sortedKeys) {
            if (fqn == key) return packageMap[key]
            if (fqn.startsWith("$key.")) {
                val rest = fqn.substring(key.length)
                return packageMap[key] + rest
            }
        }
        return null
    }

    private fun isKotlin(path: Path): Boolean {
        val name = path.fileName?.toString().orEmpty()
        return name.endsWith(".kt") || name.endsWith(".kts")
    }
}
