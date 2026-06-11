package page.app

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import page.atlas.render.VcsMark
import page.git.GitStatusKind

class VcsMarksTest {

    @Test
    fun mapsEachStatusKindToMark() {
        val marks = vcsMarksFrom(
            mapOf(
                Path.of("ws/m.kt") to GitStatusKind.MODIFIED,
                Path.of("ws/r.kt") to GitStatusKind.RENAMED,
                Path.of("ws/a.kt") to GitStatusKind.ADDED,
                Path.of("ws/u.kt") to GitStatusKind.UNTRACKED,
                Path.of("ws/d.kt") to GitStatusKind.DELETED,
            ),
        )
        fun at(rel: String) = Path.of(rel).toAbsolutePath().normalize().toString()
        assertEquals(VcsMark.MODIFIED, marks[at("ws/m.kt")])
        assertEquals(VcsMark.MODIFIED, marks[at("ws/r.kt")])
        assertEquals(VcsMark.ADDED, marks[at("ws/a.kt")])
        assertEquals(VcsMark.ADDED, marks[at("ws/u.kt")])
        assertEquals(VcsMark.DELETED, marks[at("ws/d.kt")])
    }

    @Test
    fun keysMatchAtlasNodeIdFormat() {
        val path = Path.of("ws/sub/../sub/a.kt")
        val marks = vcsMarksFrom(mapOf(path to GitStatusKind.MODIFIED))
        assertEquals(
            setOf(path.toAbsolutePath().normalize().toString()),
            marks.keys,
        )
    }
}
