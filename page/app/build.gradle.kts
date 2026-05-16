import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.io.File
import java.net.URI

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.compiler)
}

dependencies {
    implementation(project(":page:core"))
    implementation(project(":page:ui"))
    implementation(project(":page:editor"))
    implementation(project(":page:lsp"))
    implementation(libs.kotlinx.coroutines.swing)
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

val klsVersion = "1.3.13-page-1"
val klsDownloadUrl = "https://github.com/Monkshark/kotlin-language-server/releases/download/$klsVersion/server.zip"
val klsLocalZip: String? = (findProperty("page.lsp.kotlin.localZip") as? String)
    ?: System.getenv("PAGE_LSP_KOTLIN_LOCAL_ZIP")
val klsResourcesDir: Provider<Directory> = layout.buildDirectory.dir("composeResources")
val klsServerDir: Provider<Directory> = klsResourcesDir.map { it.dir("common/lsp/server") }

abstract class DownloadKlsTask : DefaultTask() {
    @get:Input
    abstract val url: Property<String>

    @get:Input
    @get:org.gradle.api.tasks.Optional
    abstract val localZip: Property<String>

    @get:OutputFile
    abstract val target: RegularFileProperty

    @TaskAction
    fun run() {
        val out = target.get().asFile
        out.parentFile.mkdirs()
        val local = localZip.orNull
        if (local != null) {
            val src = File(local)
            require(src.exists()) { "page.lsp.kotlin.localZip not found: $src" }
            src.copyTo(out, overwrite = true)
            return
        }
        if (out.exists() && out.length() > 0) return
        URI(url.get()).toURL().openStream().use { input ->
            out.outputStream().use { output -> input.copyTo(output) }
        }
    }
}

val downloadKls by tasks.registering(DownloadKlsTask::class) {
    group = "page"
    description = "Downloads kotlin-language-server (or copies from page.lsp.kotlin.localZip)"
    url.set(klsDownloadUrl)
    klsLocalZip?.let { localZip.set(it) }
    target.set(layout.buildDirectory.file("kls/server-$klsVersion.zip"))
    outputs.cacheIf { true }
}

val extractKls by tasks.registering(Copy::class) {
    group = "page"
    description = "Extracts kotlin-language-server into compose resources"
    dependsOn(downloadKls)
    from(zipTree(downloadKls.map { it.outputs.files.singleFile }))
    into(klsServerDir)
    eachFile {
        relativePath = org.gradle.api.file.RelativePath(
            !file.isDirectory,
            *relativePath.segments.drop(1).toTypedArray(),
        )
    }
    includeEmptyDirs = false
    val binDir = klsServerDir.map { it.dir("bin").asFile }
    doLast {
        binDir.get().listFiles()?.forEach { it.setExecutable(true, false) }
    }
}

val klsVersionMarker by tasks.registering {
    dependsOn(extractKls)
    val versionStr = klsVersion
    val target = klsResourcesDir.map { it.file("common/lsp/VERSION") }
    outputs.file(target)
    doLast {
        val f = target.get().asFile
        f.parentFile.mkdirs()
        f.writeText(versionStr)
    }
}

compose.desktop {
    application {
        mainClass = "page.app.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Msi)
            packageName = "PAGE"
            packageVersion = "0.1.0"
            description = "PAGE — Pair · Atlas · Glass · Echo"
            vendor = "Monkshark"
            appResourcesRootDir.set(klsResourcesDir)
        }
    }
}

tasks.matching {
    it.name == "run" ||
        it.name == "runDistributable" ||
        it.name == "createDistributable" ||
        it.name == "prepareAppResources" ||
        it.name.startsWith("package")
}.configureEach { dependsOn(klsVersionMarker) }

tasks.register<JavaExec>("runCodeEditorDemo") {
    group = "application"
    description = "Launches the standalone CodeEditor demo window"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("page.app.CodeEditorDemoKt")
}

tasks.withType<JavaExec>().configureEach {
    System.getenv("PAGE_EDITOR_TREESITTER")?.takeIf { it.isNotBlank() }?.let {
        systemProperty("page.editor.treesitter", it)
    }
}
