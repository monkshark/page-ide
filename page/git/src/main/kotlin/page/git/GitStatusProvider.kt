package page.git

import java.nio.file.Path
import java.util.concurrent.TimeUnit

class GitStatusProvider(
    private val root: Path,
    private val ttlMs: Long = 30_000,
    private val clock: () -> Long = System::currentTimeMillis,
    private val runGit: (Path) -> String? = ::gitStatusOutput,
) : VcsStatusProvider {

    private var cached: Map<Path, GitStatusKind> = emptyMap()
    private var fetchedAt = Long.MIN_VALUE

    @Synchronized
    override fun statuses(): Map<Path, GitStatusKind> {
        val now = clock()
        if (fetchedAt == Long.MIN_VALUE || now - fetchedAt > ttlMs) {
            cached = runGit(root)?.let { parseGitStatus(it, root) } ?: emptyMap()
            fetchedAt = now
        }
        return cached
    }

    @Synchronized
    override fun refresh() {
        fetchedAt = Long.MIN_VALUE
    }
}

private fun gitStatusOutput(root: Path): String? = runCatching {
    val process = ProcessBuilder("git", "status", "--porcelain=v1", "-z")
        .directory(root.toFile())
        .start()
    process.outputStream.close()
    val out = process.inputStream.readBytes().toString(Charsets.UTF_8)
    if (!process.waitFor(10, TimeUnit.SECONDS)) {
        process.destroyForcibly()
        return null
    }
    if (process.exitValue() == 0) out else null
}.getOrNull()
