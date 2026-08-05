plugins {
    kotlin("multiplatform") version "2.4.10" apply false
    kotlin("plugin.serialization") version "2.4.10" apply false
    kotlin("plugin.compose") version "2.4.10" apply false
    id("org.jetbrains.compose") version "1.11.1" apply false
}

allprojects {
    group = "me.polyhedron"
    version = "1.0-SNAPSHOT"
}

fun registerBrowserDistribution(
    taskName: String,
    mode: String,
    webTask: String,
    wasmTask: String,
) = tasks.register<Sync>(taskName) {
    group = "distribution"
    description = "Assembles the $mode Compose/WebGL application with its WasmGC core."
    dependsOn(webTask, wasmTask)
    from(project(":web").layout.buildDirectory.dir("dist/js/${mode}Executable"))
    from(project(":core").layout.buildDirectory.dir("compileSync/wasmJs/main/${mode}Executable/kotlin")) {
        into("core")
    }
    into(layout.buildDirectory.dir("dist/browser/$mode"))
}

registerBrowserDistribution(
    taskName = "browserDevelopmentDistribution",
    mode = "development",
    webTask = ":web:jsBrowserDevelopmentExecutableDistribution",
    wasmTask = ":core:wasmJsDevelopmentExecutableCompileSync",
)

registerBrowserDistribution(
    taskName = "browserProductionDistribution",
    mode = "production",
    webTask = ":web:jsBrowserDistribution",
    wasmTask = ":core:wasmJsProductionExecutableCompileSync",
)
