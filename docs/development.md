# Development

## Toolchain

| Component | Version |
| --- | --- |
| Gradle wrapper | 9.6.1 |
| Kotlin Multiplatform / serialization / Compose compiler plugin | 2.4.10 |
| JetBrains Compose | 1.11.1 |
| kotlinx.coroutines | 1.11.0 |
| kotlinx.serialization | 1.11.0 |
| kotlinx-browser | 0.5.0 |
| gl-matrix | 3.4.4 |
| JVM bytecode target | 17 |

Gradle manages the Node.js runtime used by Kotlin JS/Wasm tasks. No global Node installation is required.

## Common commands

```shell
# Compile and run JVM tests
./gradlew :core:jvmTest

# Run the core tests through both WasmGC Node and browser runners
./gradlew :core:wasmJsTest

# Assemble a local browser artifact
./gradlew browserDevelopmentDistribution

# Assemble the optimized deployable artifact
./gradlew browserProductionDistribution

# Compare production JS and WasmGC kernels
./gradlew :benchmarks:jsNodeProductionRun
./gradlew :benchmarks:wasmJsNodeProductionRun
```

Serve `build/dist/browser/development` or `build/dist/browser/production` over HTTP. A minimal local server is:

```shell
python -m http.server 8765 --directory build/dist/browser/development
```

## Source layout

```text
model/src/commonMain/      serializable presentation model and core contract
core/src/commonMain/       seed generation, manipulation algorithms, and core API
core/src/wasmJsMain/       exported Wasm browser API
core/src/commonTest/       algorithm and API tests
web/src/jsMain/kotlin/     Compose DOM UI and WebGL renderer
web/src/jsMain/resources/  HTML and CSS
benchmarks/src/commonMain/ identical JS/Wasm workloads
docs/                      live specification
```

Build development and production distributions separately. Kotlin/JS uses a shared package directory for those webpack modes, so asking Gradle to execute both webpack tasks in one invocation is unsupported.

Core tests that call suspending algorithms use `kotlinx-coroutines-test`, so the same test bodies run on JVM, JS, and Wasm without a synchronous Wasm bridge. The exhaustive browser geometry tests use a 30-second Mocha limit. Incremental Wasm linking is disabled only for the core test executable as a workaround for a Kotlin 2.4.10 linker failure; application executables retain incremental linking.

When behavior or structure changes, update the relevant live-spec file. Performance changes must rerun both production benchmark targets and replace the current results in `performance.md`.
