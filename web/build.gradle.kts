import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
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

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val cacheVersionFile: RegularFileProperty

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
            internal const val BROWSER_CACHE_VERSION = "${cacheVersionFile.get().asFile.readText().trim()}"
            """.trimIndent() + "\n"
        )
    }
}

abstract class GenerateBrowserResourcesTask : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val templatesDirectory: DirectoryProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val cacheVersionFile: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val templates = templatesDirectory.get().asFile
        val output = outputDirectory.get().asFile
        val cacheVersion = cacheVersionFile.get().asFile.readText().trim()
        output.deleteRecursively()
        templates.walkTopDown().filter(File::isFile).forEach { template ->
            val target = output.resolve(template.relativeTo(templates))
            target.parentFile.mkdirs()
            target.writeText(template.readText().replace("__BROWSER_CACHE_VERSION__", cacheVersion))
        }
    }
}

val applicationVersion = providers.gradleProperty("appVersion")
    .orElse(providers.environmentVariable("APP_VERSION"))
    .orElse(provider { project.version.toString() })
val browserCacheVersionFile = rootProject.layout.buildDirectory.file("generated/browserCache/browser-cache-version.txt")
val generatedAppVersionDirectory = layout.buildDirectory.dir("generated/sources/appVersion/kotlin")
val generateAppVersion = tasks.register<GenerateAppVersionTask>("generateAppVersion") {
    dependsOn(":generateBrowserCacheVersion")
    versionText.set(applicationVersion)
    cacheVersionFile.set(browserCacheVersionFile)
    outputDirectory.set(generatedAppVersionDirectory)
}
val generatedBrowserResourcesDirectory = layout.buildDirectory.dir("generated/resources/browser")
val generateBrowserResources = tasks.register<GenerateBrowserResourcesTask>("generateBrowserResources") {
    dependsOn(":generateBrowserCacheVersion")
    templatesDirectory.set(layout.projectDirectory.dir("src/jsMain/resourceTemplates"))
    cacheVersionFile.set(browserCacheVersionFile)
    outputDirectory.set(generatedBrowserResourcesDirectory)
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
            resources.srcDir(generatedBrowserResourcesDirectory)
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

tasks.named("jsProcessResources") {
    dependsOn(generateBrowserResources)
}
