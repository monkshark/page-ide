plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    api(project(":page:core"))
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
