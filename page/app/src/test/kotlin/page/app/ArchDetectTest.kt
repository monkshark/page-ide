package page.app

import kotlin.test.Test
import kotlin.test.assertEquals

class ArchDetectTest {

    @Test
    fun amd64Variants() {
        assertEquals("amd64", ArchDetect.archKey("amd64"))
        assertEquals("amd64", ArchDetect.archKey("x86_64"))
        assertEquals("amd64", ArchDetect.archKey("X86_64"))
        assertEquals("amd64", ArchDetect.archKey("x64"))
    }

    @Test
    fun arm64Variants() {
        assertEquals("arm64", ArchDetect.archKey("aarch64"))
        assertEquals("arm64", ArchDetect.archKey("arm64"))
        assertEquals("arm64", ArchDetect.archKey("AArch64"))
    }

    @Test
    fun x86Variants() {
        assertEquals("386", ArchDetect.archKey("x86"))
        assertEquals("386", ArchDetect.archKey("i386"))
        assertEquals("386", ArchDetect.archKey("i686"))
        assertEquals("386", ArchDetect.archKey("386"))
    }

    @Test
    fun unknownDefaultsToAmd64() {
        assertEquals("amd64", ArchDetect.archKey(""))
        assertEquals("amd64", ArchDetect.archKey("riscv64"))
    }
}
