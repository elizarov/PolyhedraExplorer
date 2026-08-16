import java.security.MessageDigest
import javax.inject.Inject
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

plugins {
    kotlin("multiplatform") version "2.4.10" apply false
    kotlin("plugin.serialization") version "2.4.10" apply false
    kotlin("plugin.compose") version "2.4.10" apply false
    id("org.jetbrains.compose") version "1.11.1" apply false
}

abstract class GenerateBrowserCacheVersionTask : DefaultTask() {
    @get:Input
    abstract val applicationVersion: Property<String>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val runtimeInputs: ConfigurableFileCollection

    @get:Internal
    abstract val rootDirectory: DirectoryProperty

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun generate() {
        val root = rootDirectory.get().asFile
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(applicationVersion.get().toByteArray())
        digest.update(0.toByte())
        runtimeInputs.files
            .filter(File::isFile)
            .sortedBy { it.relativeTo(root).invariantSeparatorsPath }
            .forEach { file ->
                digest.update(file.relativeTo(root).invariantSeparatorsPath.toByteArray())
                digest.update(0.toByte())
                file.inputStream().use { input ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        digest.update(buffer, 0, read)
                    }
                }
            }
        val version = digest.digest().take(8).joinToString("") { "%02x".format(it) }
        outputFile.get().asFile.apply {
            parentFile.mkdirs()
            writeText("$version\n")
        }
    }
}

abstract class AssembleBrowserDistributionTask @Inject constructor(
    private val fileSystemOperations: FileSystemOperations,
) : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val webDistribution: DirectoryProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val wasmDistribution: DirectoryProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val cacheVersionFile: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun assemble() {
        val output = outputDirectory.get().asFile
        val cacheVersion = cacheVersionFile.get().asFile.readText().trim()
        fileSystemOperations.delete { delete(output) }
        fileSystemOperations.copy {
            from(webDistribution)
            into(output)
        }
        fileSystemOperations.copy {
            from(wasmDistribution)
            into(output.resolve("core-$cacheVersion"))
        }
    }
}

val browserRuntimeInputs = files(
    fileTree(rootDir) {
        include("*.gradle.kts", "gradle.properties", "gradle/**")
        include("*/build.gradle.kts", "*/src/*Main/**")
        exclude("**/build/**")
    },
)
val browserCacheVersionFile = layout.buildDirectory.file("generated/browserCache/browser-cache-version.txt")
val browserApplicationVersion = providers.gradleProperty("appVersion")
    .orElse(providers.environmentVariable("APP_VERSION"))
    .orElse(provider { project.version.toString() })
val generateBrowserCacheVersion = tasks.register<GenerateBrowserCacheVersionTask>("generateBrowserCacheVersion") {
    applicationVersion.set(browserApplicationVersion)
    runtimeInputs.from(browserRuntimeInputs)
    rootDirectory.set(layout.projectDirectory)
    outputFile.set(browserCacheVersionFile)
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
) = tasks.register<AssembleBrowserDistributionTask>(taskName) {
    group = "distribution"
    description = "Assembles the $mode Compose/WebGL application with its WasmGC core."
    dependsOn(webTask, wasmTask, generateBrowserCacheVersion)
    webDistribution.set(project(":web").layout.buildDirectory.dir("dist/js/${mode}Executable"))
    wasmDistribution.set(project(":core").layout.buildDirectory.dir("compileSync/wasmJs/main/${mode}Executable/kotlin"))
    cacheVersionFile.set(browserCacheVersionFile)
    outputDirectory.set(layout.buildDirectory.dir("dist/browser/$mode"))
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
