package page.runtime

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path

object InstallerHttp {

    fun download(url: String, target: Path, onProgress: (read: Long, total: Long) -> Unit) {
        val conn = openConnection(url)
        try {
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: -1L
            conn.inputStream.use { input ->
                Files.newOutputStream(target).use { output ->
                    copyWithProgress(input, output, total, onProgress)
                }
            }
        } finally {
            conn.disconnect()
        }
    }

    fun openConnection(url: String, maxHops: Int = 5): HttpURLConnection {
        var current = url
        for (hop in 0..maxHops) {
            val conn = URI(current).toURL().openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout = 60_000
            conn.instanceFollowRedirects = false
            conn.setRequestProperty("User-Agent", "PAGE-IDE/0.1 LspInstaller")
            val code = conn.responseCode
            if (code in 300..399) {
                val loc = conn.getHeaderField("Location") ?: throw IOException("redirect without Location: $code")
                conn.disconnect()
                current = if (loc.startsWith("http")) loc else URI(current).resolve(loc).toString()
                continue
            }
            if (code !in 200..299) {
                conn.disconnect()
                throw IOException("download failed: HTTP $code for $current")
            }
            return conn
        }
        throw IOException("too many redirects starting at $url")
    }

    private fun copyWithProgress(
        input: InputStream,
        output: OutputStream,
        total: Long,
        onProgress: (read: Long, total: Long) -> Unit,
    ) {
        val buf = ByteArray(64 * 1024)
        var read = 0L
        var lastReport = 0L
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            output.write(buf, 0, n)
            read += n
            if (read - lastReport >= 256 * 1024L || (total > 0 && read == total)) {
                onProgress(read, total)
                lastReport = read
            }
        }
        if (lastReport != read) onProgress(read, total)
    }
}
