package page.atlas

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.io.TempDir
import page.atlas.analyzer.ImportResolver
import page.atlas.analyzer.RawImport
import page.atlas.analyzer.WorkspaceIndex

class PubspecResolverTest {

    private fun write(path: Path, content: String): Path {
        Files.createDirectories(path.parent)
        Files.writeString(path, content)
        return path
    }

    private fun resolve(root: Path, active: Path, target: String): Path? =
        ImportResolver.resolve(RawImport(target, false), active, WorkspaceIndex(root))

    @Test
    fun `package import resolves under pubspec matching name`(@TempDir root: Path) {
        write(root.resolve("pubspec.yaml"), "name: my_app\nversion: 1.0.0\n")
        val target = write(root.resolve("lib/widgets/button.dart"), "class Button {}")
        val active = write(root.resolve("lib/main.dart"), "")
        assertEquals(target, resolve(root, active, "package:my_app/widgets/button.dart"))
    }

    @Test
    fun `third-party package stays external even with same-named local file`(@TempDir root: Path) {
        write(root.resolve("pubspec.yaml"), "name: my_app\n")
        write(root.resolve("lib/http.dart"), "class Decoy {}")
        val active = write(root.resolve("lib/main.dart"), "")
        assertNull(resolve(root, active, "package:http/http.dart"))
    }

    @Test
    fun `monorepo cross-package import resolves to named package not active package`(@TempDir root: Path) {
        write(root.resolve("packages/app/pubspec.yaml"), "name: app\n")
        write(root.resolve("packages/core/pubspec.yaml"), "name: core\n")
        write(root.resolve("packages/app/lib/util.dart"), "class Decoy {}")
        val target = write(root.resolve("packages/core/lib/util.dart"), "class Util {}")
        val active = write(root.resolve("packages/app/lib/main.dart"), "")
        assertEquals(target, resolve(root, active, "package:core/util.dart"))
    }

    @Test
    fun `package import without pubspec stays external`(@TempDir root: Path) {
        val target = write(root.resolve("lib/widgets/button.dart"), "class Button {}")
        val active = write(root.resolve("lib/main.dart"), "")
        assertNull(resolve(root, active, "package:my_app/widgets/button.dart"))
    }

    @Test
    fun `pubspec name tolerates quotes and trailing comment`(@TempDir root: Path) {
        write(root.resolve("pubspec.yaml"), "name: \"my_app\" # primary package\n")
        val target = write(root.resolve("lib/models/user.dart"), "class User {}")
        val active = write(root.resolve("lib/main.dart"), "")
        assertEquals(target, resolve(root, active, "package:my_app/models/user.dart"))
    }
}
