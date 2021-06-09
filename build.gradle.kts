plugins {
    kotlin("multiplatform") version "1.5.10"
    kotlin("plugin.serialization") version "1.5.10"
    application
}

group = "me.polyhedron"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven { url = uri("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/kotlin-js-wrappers") }
    maven { url = uri("https://maven.pkg.jetbrains.space/public/p/kotlinx-html/maven") }
}

val `kotlin-serialization-version`: String by project
val `kotlin-coroutines-version`: String by project
val `kotlin-react-version`: String by project
val `react-version`: String by project
val `gl-matrix-version`: String by project
val `history-version`: String by project

kotlin {
    jvm {
        testRuns["test"].executionTask.configure {
            useJUnit()
        }
        withJava()
    }
    js(IR) {
        browser {
            binaries.executable()
            commonWebpackConfig {
                cssSupport.enabled = true
            }
            testTask {
                useKarma {
                    useChromeHeadless()
                }
            }
        }
    }
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:${`kotlin-serialization-version`}")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:${`kotlin-serialization-version`}")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${`kotlin-coroutines-version`}")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
            }
        }
        val jvmMain by getting
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test-junit"))
            }
        }
        val jsMain by getting {
            dependencies {
                implementation("org.jetbrains:kotlin-react:${`kotlin-react-version`}")
                implementation("org.jetbrains:kotlin-react-dom:${`kotlin-react-version`}")
                implementation(npm("react", `react-version`))
                implementation(npm("gl-matrix", `gl-matrix-version`))
                implementation(npm("history", `history-version`))
            }
        }
        val jsTest by getting {
            dependencies {
                implementation(kotlin("test-js"))
            }
        }
        all {
            languageSettings.useExperimentalAnnotation("kotlin.RequiresOptIn")
            languageSettings.useExperimentalAnnotation("kotlin.ExperimentalStdlibApi")
            languageSettings.useExperimentalAnnotation("kotlin.js.ExperimentalJsExport")
        }
    }
}

application {
    mainClassName = "MainKt"
}
