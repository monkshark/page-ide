package page.atlas

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.io.TempDir
import page.atlas.analyzer.ImportResolver
import page.atlas.analyzer.RawImport
import page.atlas.analyzer.TsConfigResolver
import page.atlas.analyzer.WorkspaceIndex

class TsConfigResolverTest {

    private fun write(path: Path, content: String): Path {
        Files.createDirectories(path.parent)
        Files.writeString(path, content)
        return path
    }

    @Test
    fun `wildcard path alias resolves under baseUrl`(@TempDir root: Path) {
        write(
            root.resolve("tsconfig.json"),
            """{ "compilerOptions": { "baseUrl": ".", "paths": { "@/*": ["src/*"] } } }""",
        )
        val target = write(root.resolve("src/components/Button.tsx"), "export const Button = 1")
        val active = write(root.resolve("src/app.tsx"), "")
        assertEquals(target, TsConfigResolver.resolve(active, "@/components/Button"))
    }

    @Test
    fun `alias resolves to index barrel`(@TempDir root: Path) {
        write(
            root.resolve("tsconfig.json"),
            """{ "compilerOptions": { "baseUrl": "src", "paths": { "@components/*": ["components/*"] } } }""",
        )
        val target = write(root.resolve("src/components/forms/index.ts"), "export {}")
        val active = write(root.resolve("src/pages/home.ts"), "")
        assertEquals(target, TsConfigResolver.resolve(active, "@components/forms"))
    }

    @Test
    fun `exact path mapping without wildcard resolves`(@TempDir root: Path) {
        write(
            root.resolve("tsconfig.json"),
            """{ "compilerOptions": { "baseUrl": ".", "paths": { "@env": ["config/env.ts"] } } }""",
        )
        val target = write(root.resolve("config/env.ts"), "export const e = 1")
        val active = write(root.resolve("src/app.ts"), "")
        assertEquals(target, TsConfigResolver.resolve(active, "@env"))
    }

    @Test
    fun `baseUrl resolves bare specifier without alias`(@TempDir root: Path) {
        write(root.resolve("tsconfig.json"), """{ "compilerOptions": { "baseUrl": "src" } }""")
        val target = write(root.resolve("src/lib/util.ts"), "export const u = 1")
        val active = write(root.resolve("src/app.ts"), "")
        assertEquals(target, TsConfigResolver.resolve(active, "lib/util"))
    }

    @Test
    fun `extends chain inherits parent paths`(@TempDir root: Path) {
        write(
            root.resolve("tsconfig.base.json"),
            """{ "compilerOptions": { "baseUrl": ".", "paths": { "@/*": ["src/*"] } } }""",
        )
        write(root.resolve("tsconfig.json"), """{ "extends": "./tsconfig.base.json" }""")
        val target = write(root.resolve("src/shared/api.ts"), "export const api = 1")
        val active = write(root.resolve("src/app.ts"), "")
        assertEquals(target, TsConfigResolver.resolve(active, "@/shared/api"))
    }

    @Test
    fun `comments and trailing commas are tolerated`(@TempDir root: Path) {
        write(
            root.resolve("tsconfig.json"),
            """
            {
              // project config
              "compilerOptions": {
                "baseUrl": ".", /* root */
                "paths": {
                  "@/*": ["src/*"],
                },
              },
            }
            """.trimIndent(),
        )
        val target = write(root.resolve("src/store/index.ts"), "export {}")
        val active = write(root.resolve("src/app.ts"), "")
        assertEquals(target, TsConfigResolver.resolve(active, "@/store"))
    }

    @Test
    fun `jsconfig is discovered when tsconfig absent`(@TempDir root: Path) {
        write(
            root.resolve("jsconfig.json"),
            """{ "compilerOptions": { "baseUrl": ".", "paths": { "~/*": ["app/*"] } } }""",
        )
        val target = write(root.resolve("app/widgets/card.js"), "module.exports = {}")
        val active = write(root.resolve("app/main.js"), "")
        assertEquals(target, TsConfigResolver.resolve(active, "~/widgets/card"))
    }

    @Test
    fun `relative target is left to relative resolution`(@TempDir root: Path) {
        write(root.resolve("tsconfig.json"), """{ "compilerOptions": { "baseUrl": ".", "paths": { "@/*": ["src/*"] } } }""")
        val active = write(root.resolve("src/app.ts"), "")
        assertNull(TsConfigResolver.resolve(active, "./util"))
    }

    @Test
    fun `no config returns null`(@TempDir root: Path) {
        val active = write(root.resolve("src/app.ts"), "")
        assertNull(TsConfigResolver.resolve(active, "@/missing"))
    }

    @Test
    fun `unmatched bare specifier without baseUrl stays external`(@TempDir root: Path) {
        write(root.resolve("tsconfig.json"), """{ "compilerOptions": { "paths": { "@/*": ["src/*"] } } }""")
        val active = write(root.resolve("src/app.ts"), "")
        assertNull(TsConfigResolver.resolve(active, "react"))
    }

    @Test
    fun `import resolver routes non-relative js import through tsconfig`(@TempDir root: Path) {
        write(
            root.resolve("tsconfig.json"),
            """{ "compilerOptions": { "baseUrl": ".", "paths": { "@/*": ["src/*"] } } }""",
        )
        val target = write(root.resolve("src/services/auth.ts"), "export const auth = 1")
        val active = write(root.resolve("src/pages/login.ts"), "")
        assertEquals(
            target,
            ImportResolver.resolve(RawImport("@/services/auth", false), active, WorkspaceIndex(root)),
        )
    }
}
