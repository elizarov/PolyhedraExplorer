plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    kotlin("plugin.compose")
    id("org.jetbrains.compose")
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
        jsMain.dependencies {
            implementation(project(":model"))
            implementation("org.jetbrains.compose.runtime:runtime:1.11.1")
            implementation("org.jetbrains.compose.html:html-core:1.11.1")
            implementation("org.jetbrains.kotlinx:kotlinx-browser:0.5.0")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
            implementation(npm("gl-matrix", "3.4.4"))
        }
        jsTest.dependencies {
            implementation(kotlin("test"))
            implementation(project(":core"))
        }
    }
}
