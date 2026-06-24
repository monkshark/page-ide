plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.compiler)
}

dependencies {
    api(project(":page:core"))
    implementation(project(":page:ui"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(libs.kotlinx.coroutines.core)
    implementation("io.github.bonede:tree-sitter:0.26.3")
    implementation("io.github.bonede:tree-sitter-java:0.23.5")
    implementation("io.github.bonede:tree-sitter-kotlin:0.3.8.1")
    implementation("io.github.bonede:tree-sitter-python:0.25.0")
    implementation("io.github.bonede:tree-sitter-javascript:0.25.0")
    implementation("io.github.bonede:tree-sitter-typescript:0.23.2")
    implementation("io.github.bonede:tree-sitter-go:0.25.0")
    implementation("io.github.bonede:tree-sitter-rust:0.24.0")
    implementation("io.github.bonede:tree-sitter-dart:master-a")
    implementation("io.github.bonede:tree-sitter-c:0.24.1")
    implementation("io.github.bonede:tree-sitter-cpp:0.23.4")
    implementation("io.github.bonede:tree-sitter-scala:0.24.0")
    implementation("io.github.bonede:tree-sitter-ruby:0.23.1")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
