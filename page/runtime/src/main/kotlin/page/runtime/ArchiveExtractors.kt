package page.runtime

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.zip.GZIPInputStream
import java.util.zip.ZipInputStream
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.tukaani.xz.XZInputStream

object ArchiveExtractors {

    fun extractZip(zipFile: Path, target: Path, flatten: Int = 0) {
        val staging = stagingFor(target)
        try {
            ZipInputStream(Files.newInputStream(zipFile)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val segments = entry.name.split('/', '\\').filter { it.isNotEmpty() }
                    val flattened = segments.drop(flatten)
                    if (flattened.isEmpty()) {
                        zip.closeEntry()
                        continue
                    }
                    val outPath = flattened.fold(staging) { acc, s -> acc.resolve(s) }
                    enforceInside(outPath, staging, entry.name)
                    if (entry.isDirectory) {
                        Files.createDirectories(outPath)
                    } else {
                        Files.createDirectories(outPath.parent)
                        Files.newOutputStream(outPath).use { out -> zip.copyTo(out) }
                        runCatching { outPath.toFile().setExecutable(true, false) }
                    }
                    zip.closeEntry()
                }
            }
            commitStaging(staging, target)
        } catch (t: Throwable) {
            runCatching { deleteRecursively(staging) }
            throw t
        }
    }

    fun extractTarGz(tarGz: Path, target: Path, flatten: Int = 0) {
        val staging = stagingFor(target)
        try {
            GZIPInputStream(Files.newInputStream(tarGz)).use { gz ->
                TarReader(gz).forEach { entry, stream ->
                    val segments = entry.name.split('/', '\\').filter { it.isNotEmpty() }
                    val flattened = segments.drop(flatten)
                    if (flattened.isEmpty()) return@forEach
                    val outPath = flattened.fold(staging) { acc, s -> acc.resolve(s) }
                    enforceInside(outPath, staging, entry.name)
                    if (entry.isDirectory) {
                        Files.createDirectories(outPath)
                    } else {
                        Files.createDirectories(outPath.parent)
                        Files.newOutputStream(outPath).use { out -> stream.copyTo(out) }
                        if (entry.executable) runCatching { outPath.toFile().setExecutable(true, false) }
                    }
                }
            }
            commitStaging(staging, target)
        } catch (t: Throwable) {
            runCatching { deleteRecursively(staging) }
            throw t
        }
    }

    fun extractTarXz(tarXz: Path, target: Path, flatten: Int = 0) {
        val staging = stagingFor(target)
        try {
            XZInputStream(Files.newInputStream(tarXz)).use { xz ->
                TarReader(xz).forEach { entry, stream ->
                    val segments = entry.name.split('/', '\\').filter { it.isNotEmpty() }
                    val flattened = segments.drop(flatten)
                    if (flattened.isEmpty()) return@forEach
                    val outPath = flattened.fold(staging) { acc, s -> acc.resolve(s) }
                    enforceInside(outPath, staging, entry.name)
                    if (entry.isDirectory) {
                        Files.createDirectories(outPath)
                    } else {
                        Files.createDirectories(outPath.parent)
                        Files.newOutputStream(outPath).use { out -> stream.copyTo(out) }
                        if (entry.executable) runCatching { outPath.toFile().setExecutable(true, false) }
                    }
                }
            }
            commitStaging(staging, target)
        } catch (t: Throwable) {
            runCatching { deleteRecursively(staging) }
            throw t
        }
    }

    fun extract7z(file: Path, target: Path, flatten: Int = 0) {
        val staging = stagingFor(target)
        try {
            SevenZFile.builder().setFile(file.toFile()).get().use { sz ->
                while (true) {
                    val entry = sz.nextEntry ?: break
                    val segments = entry.name.split('/', '\\').filter { it.isNotEmpty() }
                    val flattened = segments.drop(flatten)
                    if (flattened.isEmpty()) continue
                    val outPath = flattened.fold(staging) { acc, s -> acc.resolve(s) }
                    enforceInside(outPath, staging, entry.name)
                    if (entry.isDirectory) {
                        Files.createDirectories(outPath)
                    } else {
                        Files.createDirectories(outPath.parent)
                        Files.newOutputStream(outPath).use { out ->
                            val buf = ByteArray(64 * 1024)
                            while (true) {
                                val read = sz.read(buf)
                                if (read < 0) break
                                out.write(buf, 0, read)
                            }
                        }
                        runCatching { outPath.toFile().setExecutable(true, false) }
                    }
                }
            }
            commitStaging(staging, target)
        } catch (t: Throwable) {
            runCatching { deleteRecursively(staging) }
            throw t
        }
    }

    fun extractGzBinary(gz: Path, target: Path, executableRelative: String) {
        val staging = stagingFor(target)
        try {
            val outPath = staging.resolve(executableRelative)
            Files.createDirectories(outPath.parent ?: staging)
            GZIPInputStream(Files.newInputStream(gz)).use { input ->
                Files.newOutputStream(outPath).use { out -> input.copyTo(out) }
            }
            runCatching { outPath.toFile().setExecutable(true, false) }
            commitStaging(staging, target)
        } catch (t: Throwable) {
            runCatching { deleteRecursively(staging) }
            throw t
        }
    }

    fun placeRawBinary(file: Path, target: Path, executableRelative: String) {
        val staging = stagingFor(target)
        try {
            val outPath = staging.resolve(executableRelative)
            Files.createDirectories(outPath.parent ?: staging)
            Files.copy(file, outPath, StandardCopyOption.REPLACE_EXISTING)
            runCatching { outPath.toFile().setExecutable(true, false) }
            commitStaging(staging, target)
        } catch (t: Throwable) {
            runCatching { deleteRecursively(staging) }
            throw t
        }
    }

    private fun stagingFor(target: Path): Path {
        val parent = target.parent ?: throw IOException("target has no parent: $target")
        Files.createDirectories(parent)
        return Files.createTempDirectory(parent, "lsp-staging-")
    }

    private fun commitStaging(staging: Path, target: Path) {
        if (Files.exists(target)) deleteRecursively(target)
        runCatching { Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE) }
            .recoverCatching { Files.move(staging, target) }
            .getOrThrow()
    }

    private fun enforceInside(outPath: Path, root: Path, entryName: String) {
        val normalizedOut = outPath.normalize()
        val normalizedRoot = root.normalize()
        if (!normalizedOut.startsWith(normalizedRoot)) {
            throw IOException("zip slip detected: $entryName")
        }
    }

    fun deleteRecursively(path: Path) {
        if (!Files.exists(path)) return
        Files.walk(path).use { stream ->
            stream.sorted(Comparator.reverseOrder()).forEach { p ->
                runCatching { Files.delete(p) }
            }
        }
    }
}
