package page.runtime

import java.nio.file.Files
import java.nio.file.Path

object PubDependencySync {

    private val topLevelName = Regex("(?m)^name:\\s*\\S+")

    data class Plan(val command: List<String>, val flutter: Boolean) {
        val label: String get() = if (flutter) "flutter pub get" else "dart pub get"
    }

    fun packageConfigFor(projectDir: Path): Path =
        projectDir.resolve(".dart_tool").resolve("package_config.json")

    fun needsSync(pubspec: Path): Boolean {
        if (!Files.isRegularFile(pubspec)) return false
        val projectDir = pubspec.toAbsolutePath().normalize().parent ?: return false
        val config = packageConfigFor(projectDir)
        if (!Files.isRegularFile(config)) return true
        val pubspecTime = runCatching { Files.getLastModifiedTime(pubspec).toMillis() }.getOrNull() ?: return false
        val configTime = runCatching { Files.getLastModifiedTime(config).toMillis() }.getOrNull() ?: return true
        return pubspecTime > configTime
    }

    fun isSanePubspec(content: String): Boolean = topLevelName.containsMatchIn(content)

    fun plan(content: String, flutterExecutable: String?, dartExecutable: String?): Plan {
        val flutter = FlutterProjectDetector.isFlutterPubspec(content)
        val executable = if (flutter) flutterExecutable ?: "flutter" else dartExecutable ?: "dart"
        return Plan(listOf(executable, "pub", "get"), flutter)
    }
}
