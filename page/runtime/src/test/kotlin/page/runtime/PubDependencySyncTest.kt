package page.runtime

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PubDependencySyncTest {

    private fun tempProject(): Path = Files.createTempDirectory("page-pub-sync-")

    private fun writePubspec(dir: Path, content: String = "name: sample\n"): Path =
        dir.resolve("pubspec.yaml").also { it.writeText(content) }

    private fun writePackageConfig(dir: Path): Path {
        val config = PubDependencySync.packageConfigFor(dir)
        config.parent.createDirectories()
        config.writeText("{}")
        return config
    }

    @Test
    fun needsSyncWhenPackageConfigMissing() {
        val dir = tempProject()
        val pubspec = writePubspec(dir)
        assertTrue(PubDependencySync.needsSync(pubspec))
    }

    @Test
    fun noSyncWhenPackageConfigFresh() {
        val dir = tempProject()
        val pubspec = writePubspec(dir)
        val config = writePackageConfig(dir)
        Files.setLastModifiedTime(pubspec, FileTime.fromMillis(1_000))
        Files.setLastModifiedTime(config, FileTime.fromMillis(2_000))
        assertFalse(PubDependencySync.needsSync(pubspec))
    }

    @Test
    fun needsSyncWhenPubspecNewerThanPackageConfig() {
        val dir = tempProject()
        val pubspec = writePubspec(dir)
        val config = writePackageConfig(dir)
        Files.setLastModifiedTime(config, FileTime.fromMillis(1_000))
        Files.setLastModifiedTime(pubspec, FileTime.fromMillis(2_000))
        assertTrue(PubDependencySync.needsSync(pubspec))
    }

    @Test
    fun noSyncWithoutPubspec() {
        assertFalse(PubDependencySync.needsSync(tempProject().resolve("pubspec.yaml")))
    }

    @Test
    fun sanityRequiresTopLevelName() {
        assertTrue(PubDependencySync.isSanePubspec("name: app\ndescription: x\n"))
        assertFalse(PubDependencySync.isSanePubspec("description: x\n"))
        assertFalse(PubDependencySync.isSanePubspec("dependencies:\n  name: nested\n"))
    }

    @Test
    fun planUsesFlutterForFlutterPubspec() {
        val content = "name: app\ndependencies:\n  flutter:\n    sdk: flutter\n"
        val plan = PubDependencySync.plan(content, "C:/sdk/flutter.bat", "C:/sdk/dart.exe")
        assertEquals(listOf("C:/sdk/flutter.bat", "pub", "get"), plan.command)
        assertTrue(plan.flutter)
        assertEquals("flutter pub get", plan.label)
    }

    @Test
    fun planUsesDartForPlainPubspec() {
        val plan = PubDependencySync.plan("name: app\n", "C:/sdk/flutter.bat", "C:/sdk/dart.exe")
        assertEquals(listOf("C:/sdk/dart.exe", "pub", "get"), plan.command)
        assertFalse(plan.flutter)
        assertEquals("dart pub get", plan.label)
    }

    @Test
    fun planFallsBackToPathBinaries() {
        assertEquals(listOf("dart", "pub", "get"), PubDependencySync.plan("name: app\n", null, null).command)
        val flutterContent = "name: app\nflutter:\n"
        assertEquals(listOf("flutter", "pub", "get"), PubDependencySync.plan(flutterContent, null, null).command)
    }
}
