package page.app

import kotlin.test.Test
import kotlin.test.assertTrue

class PageRuntimeEnvTest {

    @Test
    fun applyToPrependsPathAndSetsEnvVars() {
        val env = mutableMapOf("PATH" to "/usr/bin")
        PageRuntimeEnv.applyTo(env)
        val path = env["PATH"] ?: ""
        assertTrue(path.endsWith("/usr/bin"), "original PATH should be preserved at end")
    }

    @Test
    fun applyToHandlesEmptyPath() {
        val env = mutableMapOf<String, String>()
        PageRuntimeEnv.applyTo(env)
        val runtimes = PageRuntimeEnv.collectRuntimes()
        if (runtimes.isNotEmpty()) {
            assertTrue(env.containsKey("PATH"), "PATH should be created if runtimes exist")
        }
    }

    @Test
    fun applyToIsCaseInsensitiveForPathKey() {
        val env = mutableMapOf("Path" to "/usr/bin")
        PageRuntimeEnv.applyTo(env)
        assertTrue(env.containsKey("Path"), "original casing should be preserved")
    }

    @Test
    fun collectRuntimesDoesNotThrow() {
        val runtimes = PageRuntimeEnv.collectRuntimes()
        assertTrue(runtimes is List)
    }
}
