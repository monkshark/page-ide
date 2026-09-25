package page.lsp

import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@EnabledOnOs(OS.WINDOWS)
class WindowsProcessOwnershipTest {
    object Worker {
        @JvmStatic
        fun main(args: Array<String>) {
            if (args.firstOrNull() == "leaf") {
                Thread.sleep(60_000)
            } else {
                System.`in`.read()
                val child = spawn(Worker::class.java, "leaf")
                println(child.pid())
                System.out.flush()
                Thread.sleep(60_000)
            }
        }
    }

    object Owner {
        @JvmStatic
        fun main(args: Array<String>) {
            val worker = spawn(Worker::class.java)
            println(worker.pid())
            val existingChild = args.firstOrNull() == "existing"
            val transport = if (existingChild) null else ProcessTransport(worker) { }
            worker.outputStream.write(1)
            worker.outputStream.flush()
            val child = worker.inputStream.bufferedReader().readLine()
            val ownedTransport = transport ?: ProcessTransport(worker) { }
            println(child)
            System.out.flush()
            System.`in`.read()
            if (args.firstOrNull() == "halt") Runtime.getRuntime().halt(0)
            ownedTransport.close()
        }
    }

    @Test
    fun `force killing owner also terminates server and its descendant`() = verifyTree("kill")

    @Test
    fun `owner halt without shutdown hooks also terminates server tree`() = verifyTree("halt")

    @Test
    fun `closing transport also terminates server tree`() = verifyTree("close")

    @Test
    fun `children created before transport ownership are also terminated`() = verifyTree("existing")

    private fun verifyTree(mode: String) {
        val owner = spawn(Owner::class.java, mode)
        var server: ProcessHandle? = null
        var child: ProcessHandle? = null
        try {
            val reader = owner.inputStream.bufferedReader()
            val ids = CompletableFuture.supplyAsync {
                listOf(reader.readLine().toLong(), reader.readLine().toLong())
            }.get(15, TimeUnit.SECONDS)
            server = ProcessHandle.of(ids[0]).orElseThrow()
            child = ProcessHandle.of(ids[1]).orElseThrow()
            assertTrue(server.isAlive)
            assertTrue(child.isAlive)
            if (mode == "kill" || mode == "existing") owner.destroyForcibly()
            else {
                owner.outputStream.write(1)
                owner.outputStream.flush()
            }
            assertTrue(owner.waitFor(15, TimeUnit.SECONDS))
            server.onExit().get(10, TimeUnit.SECONDS)
            child.onExit().get(10, TimeUnit.SECONDS)
            assertFalse(server.isAlive)
            assertFalse(child.isAlive)
        } finally {
            owner.destroyForcibly()
            server?.destroyForcibly()
            child?.destroyForcibly()
        }
    }

    companion object {
        private fun spawn(entry: Class<*>, vararg args: String): Process = ProcessBuilder(
            listOf(
                File(System.getProperty("java.home"), "bin/java.exe").absolutePath,
                "-Xmx64m", "-cp", System.getProperty("java.class.path"), entry.name,
            ) + args,
        ).redirectError(ProcessBuilder.Redirect.INHERIT).start()
    }
}
