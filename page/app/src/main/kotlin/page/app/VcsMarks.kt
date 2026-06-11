package page.app

import java.nio.file.Path
import page.atlas.render.VcsMark
import page.git.GitStatusKind

fun vcsMarksFrom(statuses: Map<Path, GitStatusKind>): Map<String, VcsMark> =
    statuses.entries.associate { (path, kind) ->
        path.toAbsolutePath().normalize().toString() to when (kind) {
            GitStatusKind.MODIFIED, GitStatusKind.RENAMED -> VcsMark.MODIFIED
            GitStatusKind.ADDED, GitStatusKind.UNTRACKED -> VcsMark.ADDED
            GitStatusKind.DELETED -> VcsMark.DELETED
        }
    }
