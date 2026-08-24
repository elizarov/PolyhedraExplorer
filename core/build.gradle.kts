import org.gradle.process.CommandLineArgumentProvider

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

kotlin {
    jvmToolchain(25)
    jvm {
        compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_25)
    }
    js {
        nodejs {
            testTask {
                useMocha {
                    timeout = "30s"
                }
            }
        }
    }
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        nodejs()
        binaries.executable()
        generateTypeScriptDefinitions()
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":model"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.11.0")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
        }
    }
}

tasks.named<org.jetbrains.kotlin.gradle.targets.js.ir.KotlinJsIrLink>(
    "compileTestDevelopmentExecutableKotlinWasmJs"
) {
    // Kotlin 2.4.10's incremental Wasm linker intermittently loses stdlib declarations
    // while linking this multi-platform test executable.
    incrementalWasm = false
}

val jvmTestCompilation = kotlin.targets.getByName<org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget>("jvm")
    .compilations.getByName("test")

tasks.register<JavaExec>("stlStressCampaign") {
    group = "verification"
    description = "Runs the reproducible opt-in 10,000-case exact STL export campaign."
    dependsOn("jvmTestClasses")
    mainClass.set("polyhedra.core.StlStressCampaignKt")
    classpath = jvmTestCompilation.output.allOutputs + jvmTestCompilation.runtimeDependencyFiles
    val cases = providers.gradleProperty("stlStressCases").orElse("10000")
    val seed = providers.gradleProperty("stlStressSeed").orElse("20260813")
    argumentProviders.add(CommandLineArgumentProvider { listOf(cases.get(), seed.get()) })
}

tasks.register<JavaExec>("benchmarkGreatenedSnubCube") {
    group = "benchmark"
    description = "Benchmarks an uncached generic Greatened construction of the Snub Cube."
    dependsOn("jvmTestClasses")
    mainClass.set("polyhedra.core.GreatenedSnubCubeBenchmarkKt")
    classpath = jvmTestCompilation.output.allOutputs + jvmTestCompilation.runtimeDependencyFiles
    val warmups = providers.gradleProperty("benchmarkWarmups").orElse("1")
    val samples = providers.gradleProperty("benchmarkSamples").orElse("5")
    argumentProviders.add(CommandLineArgumentProvider { listOf(warmups.get(), samples.get()) })
    if (providers.gradleProperty("benchmarkJfr").orNull == "true") {
        val recording = layout.buildDirectory.file("reports/benchmarks/greatened-snub-cube.jfr").get().asFile
        doFirst { recording.parentFile.mkdirs() }
        jvmArgs("-XX:StartFlightRecording=filename=${recording.absolutePath},settings=profile,dumponexit=true")
    }
}

tasks.register<JavaExec>("benchmarkGreatenedRhombicTriacontahedron") {
    group = "benchmark"
    description = "Benchmarks uncached enumeration of all Greatened Rhombic Triacontahedron results."
    dependsOn("jvmTestClasses")
    mainClass.set("polyhedra.core.GreatenedRhombicTriacontahedronBenchmarkKt")
    classpath = jvmTestCompilation.output.allOutputs + jvmTestCompilation.runtimeDependencyFiles
    val warmups = providers.gradleProperty("benchmarkWarmups").orElse("1")
    val samples = providers.gradleProperty("benchmarkSamples").orElse("5")
    argumentProviders.add(CommandLineArgumentProvider { listOf(warmups.get(), samples.get()) })
    if (providers.gradleProperty("benchmarkJfr").orNull == "true") {
        val recording = layout.buildDirectory.file(
            "reports/benchmarks/greatened-rhombic-triacontahedron.jfr",
        ).get().asFile
        doFirst { recording.parentFile.mkdirs() }
        jvmArgs("-XX:StartFlightRecording=filename=${recording.absolutePath},settings=profile,dumponexit=true")
    }
}
