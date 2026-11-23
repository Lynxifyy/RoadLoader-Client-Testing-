pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "RoadClient"

include(":app")

// RoadLoader ships the 32-bit companion engine (Rust core, JNI bridge, shared
// memory session) and is consumed here as a git submodule.
include(":road-loader-core")
project(":road-loader-core").projectDir = file("RoadLoader/road-loader-core")
