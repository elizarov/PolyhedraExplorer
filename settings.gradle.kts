rootProject.name = "PolyhedraExplorer"

pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

include(":model", ":core", ":web", ":benchmarks", ":renderer")
