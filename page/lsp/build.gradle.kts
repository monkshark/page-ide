plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    api(project(":page:core"))
    api(libs.lsp4j)
    implementation(libs.kotlinx.coroutines.core)
    implementation("net.java.dev.jna:jna:5.14.0")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
