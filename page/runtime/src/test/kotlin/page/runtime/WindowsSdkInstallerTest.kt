package page.runtime

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WindowsSdkInstallerTest {

    private val ucrtModulemap = """
        module ucrt [system] {
          module C {
            module math {
              header "math.h"
              export *
            }
          }
        }

        module corecrt [system] {
          use vcruntime

          header "corecrt.h"
          export *

          module malloc {
            header "corecrt_malloc.h"
            export *
          }

          module math {
            header "corecrt_math.h"
            export *
          }

          module share {
            header "corecrt_share.h"
            export *
          }
        }
    """.trimIndent()

    private fun windows(arch: String = "amd64") = WindowsSdkInstaller(
        archKey = arch,
        isWindows = true,
    )

    @Test
    fun downloadUrlUsesXwinHostTriple() {
        assertEquals(
            "https://github.com/Jake-Shadle/xwin/releases/download/0.9.0/xwin-0.9.0-x86_64-pc-windows-msvc.tar.gz",
            windows("amd64").downloadUrl("0.9.0"),
        )
        assertEquals(
            "https://github.com/Jake-Shadle/xwin/releases/download/0.9.0/xwin-0.9.0-aarch64-pc-windows-msvc.tar.gz",
            windows("arm64").downloadUrl("0.9.0"),
        )
    }

    @Test
    fun splatCommandPinsCrtVersionBeforeSubcommand() {
        val cmd = windows().splatCommand(Path("C:", "xwin", "xwin.exe"), Path("C:", "splat"))
        val crtIdx = cmd.indexOf("--crt-version")
        assertTrue(crtIdx >= 0, cmd.toString())
        assertEquals(WindowsSdkInstaller.PINNED_CRT_VERSION, cmd[crtIdx + 1], cmd.toString())
        assertTrue(crtIdx < cmd.indexOf("splat"), "crt-version must precede the splat subcommand: $cmd")
        assertTrue("--disable-symlinks" in cmd, cmd.toString())
        assertTrue("--output" in cmd, cmd.toString())
    }

    @Test
    fun splatCompleteRequiresPinnedCrtMarker() {
        val splat = Files.createTempDirectory("page-splat-marker")
        Files.createDirectories(splat.resolve("crt").resolve("include"))
        Files.createDirectories(splat.resolve("sdk").resolve("include").resolve("um"))
        val installer = windows()
        assertFalse(installer.splatComplete(splat), "dirs without marker must not count as complete")
        assertFalse(installer.crtMarkerMatches(splat))

        installer.writeCrtMarker(splat)
        assertTrue(installer.crtMarkerMatches(splat))
        assertTrue(installer.splatComplete(splat))
        assertEquals(WindowsSdkInstaller.PINNED_CRT_VERSION, Files.readString(installer.crtMarkerFile(splat)).trim())

        Files.writeString(installer.crtMarkerFile(splat), "14.44.17.14")
        assertFalse(installer.crtMarkerMatches(splat), "stale CRT marker must force re-splat")
        assertFalse(installer.splatComplete(splat))
    }

    @Test
    fun includeDirsCoverCrtAndSdkGroups() {
        val splat = Path("C:", "splat")
        val dirs = windows().includeDirs(splat).map { it.toString().replace('\\', '/') }
        assertTrue(dirs.any { it.endsWith("/crt/include") }, dirs.toString())
        assertTrue(dirs.any { it.endsWith("/sdk/include/ucrt") }, dirs.toString())
        assertTrue(dirs.any { it.endsWith("/sdk/include/shared") }, dirs.toString())
        assertTrue(dirs.any { it.endsWith("/sdk/include/um") }, dirs.toString())
    }

    @Test
    fun libDirsUseTargetArchSubfolders() {
        val splat = Path("C:", "splat")
        val dirs = windows("amd64").libDirs(splat).map { it.toString().replace('\\', '/') }
        assertTrue(dirs.any { it.endsWith("/crt/lib/x86_64") }, dirs.toString())
        assertTrue(dirs.any { it.endsWith("/sdk/lib/ucrt/x86_64") }, dirs.toString())
        assertTrue(dirs.any { it.endsWith("/sdk/lib/um/x86_64") }, dirs.toString())
    }

    @Test
    fun arm64LibDirsUseAarch64() {
        val splat = Path("C:", "splat")
        val dirs = windows("arm64").libDirs(splat).map { it.toString().replace('\\', '/') }
        assertTrue(dirs.all { it.contains("/aarch64") }, dirs.toString())
    }

    @Test
    fun buildEnvJoinsIncludeAndLibWithPathSeparator() {
        val splat: Path = Files.createTempDirectory("page-splat-env")
        val env = windows().buildEnv(splat)
        val sep = File.pathSeparator
        assertEquals(windows().includeDirs(splat).size, env.getValue("INCLUDE").split(sep).size)
        assertEquals(windows().libDirs(splat).size, env.getValue("LIB").split(sep).size)
        assertTrue(env.getValue("INCLUDE").replace('\\', '/').contains("/crt/include"))
        assertTrue(env.getValue("LIB").replace('\\', '/').contains("/sdk/lib/um/x86_64"))
    }

    @Test
    fun patchRemovesCorecrtMathSubmoduleOnly() {
        val patched = windows().patchMissingCorecrtMath(ucrtModulemap)
        assertFalse(patched.contains("corecrt_math.h"), patched)
        assertTrue(patched.contains("\"math.h\""), patched)
        assertTrue(patched.contains("corecrt_malloc.h"), patched)
        assertTrue(patched.contains("corecrt_share.h"), patched)
        assertFalse(patched.contains("module math {\n    header \"corecrt_math.h\""), patched)
    }

    @Test
    fun deployPatchesUcrtWhenHeaderMissingAndKeepsItWhenPresent() {
        val share = Files.createTempDirectory("page-share")
        Files.writeString(share.resolve("ucrt.modulemap"), ucrtModulemap)
        Files.writeString(share.resolve("winsdk.modulemap"), "module WinSDK [system] {}")
        Files.writeString(share.resolve("vcruntime.modulemap"), "module vcruntime [system] {}")

        val missing = Files.createTempDirectory("page-splat-missing")
        Files.createDirectories(missing.resolve("sdk").resolve("include").resolve("ucrt"))
        Files.createDirectories(missing.resolve("sdk").resolve("include").resolve("um"))
        Files.createDirectories(missing.resolve("crt").resolve("include"))
        assertTrue(windows().deployModulemaps(share, missing))
        val missingUcrt = Files.readString(missing.resolve("sdk").resolve("include").resolve("ucrt").resolve("module.modulemap"))
        assertFalse(missingUcrt.contains("corecrt_math.h"), missingUcrt)
        assertTrue(Files.exists(missing.resolve("sdk").resolve("include").resolve("um").resolve("module.modulemap")))
        assertTrue(Files.exists(missing.resolve("crt").resolve("include").resolve("module.modulemap")))

        val present = Files.createTempDirectory("page-splat-present")
        val presentUcrtDir = present.resolve("sdk").resolve("include").resolve("ucrt")
        Files.createDirectories(presentUcrtDir)
        Files.createDirectories(present.resolve("sdk").resolve("include").resolve("um"))
        Files.createDirectories(present.resolve("crt").resolve("include"))
        Files.writeString(presentUcrtDir.resolve("corecrt_math.h"), "#pragma once\n")
        assertTrue(windows().deployModulemaps(share, present))
        val presentUcrt = Files.readString(presentUcrtDir.resolve("module.modulemap"))
        assertTrue(presentUcrt.contains("corecrt_math.h"), presentUcrt)
    }
}
