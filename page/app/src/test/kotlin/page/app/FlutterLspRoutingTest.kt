package page.app

import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FlutterLspRoutingTest {

    @Test
    fun `dart file inside flutter project routes to flutter`(@TempDir root: Path) {
        Files.writeString(
            root.resolve("pubspec.yaml"),
            "name: demo\ndependencies:\n  flutter:\n    sdk: flutter\n",
        )
        val lib = Files.createDirectories(root.resolve("lib"))
        val file = Files.writeString(lib.resolve("main.dart"), "void main() {}\n")
        assertTrue(isFlutterDartFile(file, root))
    }

    @Test
    fun `dart file in plain dart package stays on dart`(@TempDir root: Path) {
        Files.writeString(
            root.resolve("pubspec.yaml"),
            "name: demo\nenvironment:\n  sdk: ^3.0.0\n",
        )
        val lib = Files.createDirectories(root.resolve("lib"))
        val file = Files.writeString(lib.resolve("main.dart"), "void main() {}\n")
        assertFalse(isFlutterDartFile(file, root))
    }

    @Test
    fun `non dart file never routes to flutter`(@TempDir root: Path) {
        Files.writeString(
            root.resolve("pubspec.yaml"),
            "name: demo\ndependencies:\n  flutter:\n    sdk: flutter\n",
        )
        val file = Files.writeString(root.resolve("Main.kt"), "fun main() {}\n")
        assertFalse(isFlutterDartFile(file, root))
    }

    @Test
    fun `dart file without pubspec stays on dart`(@TempDir root: Path) {
        val file = Files.writeString(root.resolve("main.dart"), "void main() {}\n")
        assertFalse(isFlutterDartFile(file, root))
    }
}
