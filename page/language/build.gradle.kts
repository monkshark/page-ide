plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.compiler)
}

dependencies {
    api(project(":page:core"))
    api(project(":page:lsp"))
    implementation(project(":page:runtime"))
    implementation(project(":page:editor"))
    implementation(compose.runtime)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
