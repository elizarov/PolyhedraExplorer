import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    kotlin("plugin.compose")
    id("org.jetbrains.compose")
}

abstract class GenerateAppVersionTask : DefaultTask() {
    @get:Input
    abstract val versionText: Property<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val version = versionText.get()
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
        val output = outputDirectory.file("polyhedra/web/main/AppVersion.kt").get().asFile
        output.parentFile.mkdirs()
        output.writeText(
            """
            package polyhedra.web.main

            internal const val APPLICATION_VERSION = "$version"
            """.trimIndent() + "\n"
        )
    }
}

val applicationVersion = providers.gradleProperty("appVersion")
    .orElse(providers.environmentVariable("APP_VERSION"))
    .orElse(provider { project.version.toString() })
val generatedAppVersionDirectory = layout.buildDirectory.dir("generated/sources/appVersion/kotlin")
val generateAppVersion = tasks.register<GenerateAppVersionTask>("generateAppVersion") {
    versionText.set(applicationVersion)
    outputDirectory.set(generatedAppVersionDirectory)
}

kotlin {
    js {
        browser {
            commonWebpackConfig {
                outputFileName = "PolyhedraExplorer.js"
                cssSupport {
                    enabled.set(true)
                }
            }
            testTask {
                useKarma {
                    useChromeHeadless()
                }
            }
        }
        binaries.executable()
    }

    sourceSets {
        jsMain {
            kotlin.srcDir(generatedAppVersionDirectory)
            dependencies {
                implementation(project(":model"))
                implementation("org.jetbrains.compose.runtime:runtime:1.11.1")
                implementation("org.jetbrains.compose.html:html-core:1.11.1")
                implementation("org.jetbrains.kotlinx:kotlinx-browser:0.5.0")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
                implementation(npm("gl-matrix", "3.4.4"))
                implementation(npm("earcut", "3.2.3"))
            }
        }
        jsTest.dependencies {
            implementation(kotlin("test"))
            implementation(project(":core"))
        }
    }
}

tasks.named("compileKotlinJs") {
    dependsOn(generateAppVersion)
}
