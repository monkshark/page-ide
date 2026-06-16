package page.atlas

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir
import page.atlas.analyzer.ImportGraphProvider
import page.atlas.render.EgoColumn
import page.atlas.render.buildEgoModel

class EgoPipelineTest {

    @Test
    fun `project slice splits dependents and imports into their columns`(@TempDir root: Path) {
        val pkg = root.resolve("src/com/example")
        Files.createDirectories(pkg)
        val focus = pkg.resolve("Focus.kt")
        Files.writeString(
            focus,
            "package com.example\n\nimport com.example.ImpA\nimport com.example.ImpB\n\nclass Focus",
        )
        Files.writeString(pkg.resolve("ImpA.kt"), "package com.example\n\nclass ImpA")
        Files.writeString(pkg.resolve("ImpB.kt"), "package com.example\n\nclass ImpB")
        for (name in listOf("DepX", "DepY", "DepZ")) {
            Files.writeString(
                pkg.resolve("$name.kt"),
                "package com.example\n\nimport com.example.Focus\n\nclass $name",
            )
        }
        val provider = ImportGraphProvider(root)
        val focusText = Files.readString(focus)
        val slice = provider.nodesForProject(focus, focusText)
        val focusId = focus.toAbsolutePath().normalize().toString()
        val model = buildEgoModel(slice, focusId)

        val dependents = model.nodes.count { it.column == EgoColumn.DEPENDENT }
        val imports = model.nodes.count { it.column == EgoColumn.IMPORT }
        assertEquals(3, dependents, "all three importers should land in the dependent column")
        assertEquals(2, imports, "both imported files should land in the import column")
    }
}
