import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        outputModuleName.set("docs-viewer")
        browser {
            commonWebpackConfig {
                outputFileName = "docs-viewer.js"
            }
        }
        binaries.executable()
    }

    sourceSets {
        wasmJsMain.dependencies {
            implementation(project(":shared-core"))
            implementation(project(":page:atlas-view"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
        }
    }
}

val docsResourceDir = layout.projectDirectory.dir("src/wasmJsMain/resources/docs")
val docsIndexFile = layout.projectDirectory.file("src/wasmJsMain/resources/docs-index.json")

val syncDocs by tasks.registering(Copy::class) {
    from(rootProject.projectDir.resolve("docs")) { include("**/*.md") }
    into(docsResourceDir)
}

val generateDocsIndex by tasks.registering {
    dependsOn(syncDocs)
    val dir = docsResourceDir.asFile
    val out = docsIndexFile.asFile
    inputs.dir(dir)
    outputs.file(out)
    doLast {
        val rels = dir.walkTopDown()
            .filter { it.isFile && it.extension == "md" }
            .map { it.relativeTo(dir).path.replace('\\', '/') }
            .sorted()
            .toList()
        out.writeText(rels.joinToString(prefix = "[", postfix = "]", separator = ",") { "\"$it\"" })
    }
}

tasks.named("wasmJsProcessResources") {
    dependsOn(":page:atlas:exportAtlasSnapshot")
    dependsOn(generateDocsIndex)
}
