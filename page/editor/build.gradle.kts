plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    api(project(":page:core"))
    implementation("io.github.bonede:tree-sitter:0.26.3")
    implementation("io.github.bonede:tree-sitter-java:0.23.5")
    implementation("io.github.bonede:tree-sitter-kotlin:0.3.8.1")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
