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
| JVM toolchain / bytecode target | 25 |

Gradle manages the Node.js runtime used by Kotlin JS/Wasm tasks. No global Node installation is required.

## Common commands

```shell
# Run core tests on the JVM and web tests in a JS browser (recommended fast feedback)
./gradlew test

# Run the core tests through both WasmGC Node and browser runners
./gradlew :core:wasmJsTest

# Assemble a local browser artifact
./gradlew browserDevelopmentDistribution

# Assemble the optimized deployable artifact
./gradlew browserProductionDistribution

# Run the deterministic 10,000-case exact-STL hardening corpus (opt-in, about 12 minutes)
./gradlew :core:stlStressCampaign

# Compare production JS and WasmGC kernels
./gradlew :benchmarks:jsNodeProductionRun
./gradlew :benchmarks:wasmJsNodeProductionRun
```

Prefer the root `test` task during development: it runs the complete core suite on the JVM for fast algorithm feedback and the web module's JS browser tests for UI coverage. It is deliberately the same test gate used by the release workflow. Release validation then relies on the focused production acceptance test for WasmGC integration rather than repeating the exhaustive core suite in every runtime.

The opt-in STL campaign uses seed `20260813` by default and accepts
`-PstlStressCases=<count>` and `-PstlStressSeed=<seed>`. Its current 10,000-case corpus produces
3,906 independently validated solids and 6,094 documented topology rejections, with no invalid
successful output and no limit rejection. A regression fails if an ordinary fixture becomes an
input error, a small fixture reaches a resource limit, fewer than 35% of candidates succeed, or an
apparently successful result fails the independent final-mesh validator.

Serve `build/dist/browser/development` or `build/dist/browser/production` over HTTP. A minimal local server is:

```shell
python -m http.server 8765 --directory build/dist/browser/development
```

## Release and deployment

Releases are initiated locally with one version tag:

```shell
./deploy.sh patch
```

`patch` reads the repository's remote semantic-version tags and increments the patch component, such as `1.0.15` to `1.0.16`. An exact `major.minor.patch` argument remains available when a minor or major version is needed. The script creates a lightweight tag at `HEAD` and pushes only that tag. Re-running it after a failed push is safe when the local tag already points to the same commit. It does not build, test, or publish files from the local machine.

A matching tag push triggers `.github/workflows/release.yml`. The pinned GitHub Actions environment checks out that commit on Ubuntu 24.04, installs the current Java LTS toolchain (Temurin JDK 25), and selects the project's checked-in Gradle 9.6.1 wrapper. It validates the tag format and Gradle wrapper, injects that tag as the application version shown in keyboard help, runs the root `test` task (core on JVM plus web in a JS browser), and builds `browserProductionDistribution`. Local builds identify themselves with the Gradle project version and can override it with `-PappVersion=<version>`.

The production acceptance test installs an exact Chrome for Testing build and serves that exact production directory. It opens representative seed-construction, primitive-transform, canonicalization, and Kepler-Poinsot `D -> Stellate -> Greaten` configurations; each case must load through the real Compose UI, WasmGC worker, and canvas and produce its expected F/E/V counts. It also exports a hidden-cap Prism 5/2 through the independent STL Wasm worker and requires a completed STL download with no structured error. Additional JS/Wasm core test matrices, exhaustive browser combinations, the core-JS benchmark baseline, and benchmark-module variants are not release gates. The accepted production directory is uploaded as a GitHub Pages artifact and deployed through the protected `github-pages` environment. Validation must complete before deployment can start, and the Pages concurrency group serializes releases without cancelling an in-progress production deployment.

The repository's Pages publishing source is **GitHub Actions**. The custom domain remains a repository Pages setting and is not produced by a developer workstation.

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
