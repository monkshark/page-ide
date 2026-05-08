plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    api(project(":page:core"))
    api(libs.lsp4j)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
