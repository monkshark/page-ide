plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.compiler)
}

dependencies {
    api(project(":page:core"))
    api(project(":page:editor"))
    api(compose.desktop.currentOs)
    api(compose.material3)
    api(compose.materialIconsExtended)
}
