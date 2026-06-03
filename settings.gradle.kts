rootProject.name = "page"

pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

include(":page:core")
include(":page:perf")
include(":page:ui")
include(":page:editor")
include(":page:lsp")
include(":page:language")
include(":page:runtime")
include(":page:workspace")
include(":page:atlas")
include(":page:echo")
include(":page:pair")
include(":page:git")
include(":page:app")
