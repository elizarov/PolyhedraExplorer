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

tasks.register("test") {
    group = "verification"
    description = "Runs core tests on the JVM and web tests in a JS browser for fast feedback."
    dependsOn(":core:jvmTest", ":web:jsBrowserTest")
}

// Keep wall-clock performance assertions isolated from the browser runner's CPU load.
gradle.projectsEvaluated {
    project(":web").tasks.named("jsBrowserTest") {
        mustRunAfter(project(":core").tasks.named("jvmTest"))
    }
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
        // Keep the directory version in sync with CoreClient and the worker resource whenever
        // core behavior or its serialized contract changes. A distinct directory cache-busts
        // every generated Wasm support module, not only the entry point.
        into("core-v15")
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
