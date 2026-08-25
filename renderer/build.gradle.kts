import org.gradle.process.CommandLineArgumentProvider
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsEnvSpec
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsExec
import java.nio.charset.StandardCharsets
import java.util.Base64
import javax.inject.Inject

abstract class RenderConfigArguments @Inject constructor() : CommandLineArgumentProvider {
    @get:Input
    abstract val configuration: Property<String>

    @get:Input
    abstract val output: Property<String>

    @get:Input
    abstract val width: Property<String>

    @get:Input
    abstract val height: Property<String>

    @get:Internal
    abstract val rootDirectory: DirectoryProperty

    override fun asArguments(): Iterable<String> = listOf(
        configuration.get(),
        rootDirectory.file(output.get()).get().asFile.absolutePath,
        width.get(),
        height.get(),
    )
}

abstract class InstallHeadlessGl : DefaultTask() {
    @get:InputFile
    abstract val nodeExecutable: RegularFileProperty

    @get:Internal
    abstract val nodeModulesDirectory: DirectoryProperty

    @get:OutputFile
    abstract val nativeBinding: RegularFileProperty

    @get:Inject
    abstract val execOperations: ExecOperations

    @TaskAction
    fun install() {
        val node = nodeExecutable.get().asFile.absolutePath
        val nodeModules = nodeModulesDirectory.get().asFile
        val glDirectory = nodeModules.resolve("gl")

        val prebuild = execOperations.exec {
            workingDir(glDirectory)
            commandLine(node, nodeModules.resolve("prebuild-install/bin.js").absolutePath)
            isIgnoreExitValue = true
        }
        if (prebuild.exitValue == 0) {
            check(nativeBinding.get().asFile.isFile) { "headless-gl installer produced no native binding" }
            return
        }

        logger.lifecycle("No headless-gl binary is available for this Node version; building it with node-gyp.")
        execOperations.exec {
            workingDir(glDirectory)
            commandLine(node, nodeModules.resolve("node-gyp/bin/node-gyp.js").absolutePath, "rebuild")
        }.assertNormalExitValue()
        check(nativeBinding.get().asFile.isFile) { "headless-gl build produced no native binding" }
    }
}

plugins {
    kotlin("multiplatform")
}

kotlin {
    js {
        nodejs {
            testTask {
                useMocha {
                    timeout = "60s"
                }
            }
        }
        binaries.executable()
    }

    sourceSets {
        jsMain.dependencies {
            implementation(project(":core"))
            implementation(project(":web"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
            implementation(npm("gl", "8.1.6"))
            implementation(npm("pngjs", "7.0.0"))
        }
        jsTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
        }
    }
}

val renderConfiguration = providers.gradleProperty("renderConfigurationBase64")
    .map { encoded -> String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8) }
    .orElse(providers.gradleProperty("renderConfiguration"))
val renderOutput = providers.gradleProperty("renderOutput")
    .orElse("build/rendered/configuration.png")
val renderWidth = providers.gradleProperty("renderWidth").orElse("1600")
val renderHeight = providers.gradleProperty("renderHeight").orElse("1200")
val nodeJs = rootProject.extensions.getByType<NodeJsEnvSpec>()
val nodeModules = rootProject.layout.buildDirectory.dir("js/node_modules")
val installHeadlessGl = tasks.register<InstallHeadlessGl>("installHeadlessGl") {
    group = "build setup"
    description = "Installs or builds the native headless-gl binding using Gradle-managed Node dependencies."
    dependsOn(
        rootProject.tasks.named("kotlinNpmInstall"),
        rootProject.tasks.named("kotlinNodeJsSetup"),
        tasks.named("kotlinNodeJsSetup"),
        project(":web").tasks.named("kotlinNodeJsSetup"),
    )
    nodeExecutable.fileProvider(nodeJs.executable.map(::file))
    nodeModulesDirectory.set(nodeModules)
    nativeBinding.set(nodeModules.map { it.file("gl/build/Release/webgl.node") })
}

val nodeRun = tasks.named<NodeJsExec>("jsNodeDevelopmentRun") {
    dependsOn(installHeadlessGl)
    argumentProviders.add(objects.newInstance<RenderConfigArguments>().apply {
        configuration.set(renderConfiguration)
        output.set(renderOutput)
        width.set(renderWidth)
        height.set(renderHeight)
        rootDirectory.set(rootProject.layout.projectDirectory)
    })
}

tasks.named("jsNodeTest") {
    dependsOn(installHeadlessGl)
}

tasks.register("renderConfig") {
    group = "application"
    description = "Renders a serialized configuration to PNG with Node and headless-gl."
    dependsOn(nodeRun)
}
