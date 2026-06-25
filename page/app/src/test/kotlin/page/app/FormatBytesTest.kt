package page.app

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class FormatBytesTest {

    @Test
    fun `formatBytes uses human-readable units`() {
        assertEquals("512 B", formatBytes(512))
        assertEquals("1.00 KB", formatBytes(1024))
        assertNotNull(formatBytes(5L * 1024 * 1024))
        assertEquals("1.00 MB", formatBytes(1024L * 1024L))
    }
}
