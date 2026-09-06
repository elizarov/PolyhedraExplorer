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

# Check stellation/cache and rim-STL timing budgets on a quiet local JVM
./gradlew :core:benchmarkValidation
```

Prefer the root `test` task during development: it runs the complete core suite on the JVM for fast algorithm feedback and the web module's JS browser tests for UI coverage. It is deliberately the same test gate used by the release workflow. Release validation then relies on the focused production acceptance test for WasmGC integration rather than repeating the exhaustive core suite in every runtime.

In a combined run, JVM tests wait for the browser tests so large geometry workloads do not compete
with webpack and Chrome for CPU. Focused `:core:jvmTest` runs remain JVM-only; compilation can still run
in parallel. Stellation timing excludes seed construction and first-use runtime linkage; a smaller
constellation warms the kernel before its result cache is cleared for the measured enumeration.
Test failures include full assertion details in CI logs.

The full functional workloads, including large STL exports and cache-identity assertions, run in
`test`. Hardware-dependent timing budgets belong to `:core:benchmarkValidation`, which invokes those
same workloads and enforces their budgets (3s stellation, 2s first cached switch, 1s repeated response,
1s Antiprism 7/3 rim STL, 30s Greatened 5 Truncated icosahedron rim STL). Run it on a quiet machine;
shared CI runner scheduling is not a geometry-correctness contract. Geometry assertions, test
timeouts, and production conversion resource limits remain enforced in release validation.

The opt-in STL campaign uses seed `20260813` by default and accepts
`-PstlStressCases=<count>` and `-PstlStressSeed=<seed>`. Its current 10,000-case corpus produces
3,906 independently validated solids and 6,094 documented topology rejections, with no invalid
successful output and no limit rejection. A regression fails if an ordinary fixture becomes an
input error, a small fixture reaches a resource limit, fewer than 35% of candidates succeed, or an
apparently successful result fails the independent final-mesh validator.

## Geometry test ownership

Every geometry algorithm has focused JVM unit tests for its own contract; browser acceptance is not
a substitute. The suites separate planar face arrangement, layered validation and intersection
classification, the Resolved transform and provenance, hidden-rim construction, transform domains, generic
stellation, STL presentation/arrangement/final validation, and OpenSCAD structure. Each suite covers
determinism, reversed orientation, rotation and scale invariance, tolerance boundaries, and
controlled rejection where those properties apply.

Shared adversarial fixtures are reused across algorithms, but each test asserts only the output of
the layer under test. Random stress cases are minimized before entering the normal suite. The full
10,000-case STL corpus remains opt-in so `./gradlew test` stays suitable for quick development, while
the minimized cases that exposed distinct failures remain permanent regressions.

Renderable-surface validation checks coincident vertices with a component-aware spatial grid,
including neighboring cells and an exact distance check. This keeps large tessellated STL regressions
practical without weakening their geometry checks or extending test timeouts. The spatial check is
tested against an independent all-pairs reference, including compound members and cell boundaries.

Large STL-output regressions validate the indexed triangles directly: finite coordinates, valid
indices, nondegenerate triangles, two opposite uses per edge, one connected fan per vertex,
component-aware vertex separation, and positive signed volume. They do not reconstruct a second
full `Polyhedron` and resolve every already-triangular face. Corrupted mesh fixtures separately
test this compact validator, including open edges, overused edges, reversed faces, and pinched fans.

Serve `build/dist/browser/development` or `build/dist/browser/production` over HTTP. A minimal local server is:

```shell
python -m http.server 8765 --directory build/dist/browser/development
```

For repeatable visual inspection, the standalone renderer accepts the exact compact configuration
stored after `#/` and writes a PNG with the production WebGL shaders. It evaluates the core through
the Kotlin/JS Node target and renders through `headless-gl`; it does not build a browser distribution,
start an HTTP server, or launch a browser:

```powershell
powershell -ExecutionPolicy Bypass -File tools/render-config.ps1 `
    'a(r(n))s(SA5_2)hf(γ,β,α)v(r(-42,-22.1,-110.3)s(0.11)fw(0.06666667)fr(0.03333333))' `
    'build/rendered/antiprism-5-2.png'
```

Every renderer invocation also prints a live core progress bar followed by the same diagnostic
report as the JVM inspector: core and orbit-analysis timings, result and geometry metadata,
transform stages, stored F/E/V kinds, full geometric orbit memberships, and the mapping between
stored kinds and geometric orbits. This makes a rendered regression image and its underlying core
classification available in one run.

`-Width` and `-Height` set the drawing-buffer and PNG dimensions. The PowerShell file is only a thin
argument wrapper around `:renderer:renderConfig`. It transfers the configuration as UTF-8 Base64 so
orbit letters survive the Windows batch boundary; Gradle decodes it before launching Node. Gradle
provisions and caches Node, `headless-gl`, `pngjs`, and their npm dependencies, compiles the core,
renderer, and CLI, and tracks the native `headless-gl` binding incrementally. When no binding exists
for the managed Node version, the Gradle setup task builds it with `node-gyp`; that first build needs
the platform's normal native C++ toolchain and Python. Later renders reuse the installed binding.

The equivalent Gradle entry point is `:renderer:renderConfig` with `renderConfiguration` (or the
UTF-8 `renderConfigurationBase64` used by the wrapper), `renderOutput`, `renderWidth`, and
`renderHeight` project properties. `./gradlew :renderer:jsNodeTest` renders the immersed Antiprism
5/2 regression with the actual shaders and verifies both opaque compositing and serialized
hidden-face rim selection.

For faster geometry and orbit debugging without Node, WebGL, or a PNG, use the JVM inspector:

```powershell
powershell -ExecutionPolicy Bypass -File tools/inspect-config.ps1 `
    'a(r(n))s(eC)t(G~l=3,d)v(fw(0.03333333)fr(0.03333333))'
```

The inspector accepts either the compact fragment or a complete URL. Its progress bar identifies
the transform currently consuming the core, and its separately reported `Core construction` time
includes the complete requested core evaluation. The additional geometric-orbit expansion is
timed independently. The direct Gradle entry point is `:core:inspectConfiguration` with
`inspectConfiguration` or UTF-8 `inspectConfigurationBase64`.

## Release and deployment

Releases are initiated locally with one version tag:

```shell
./deploy.sh patch
```

`patch` reads the repository's remote semantic-version tags and increments the patch component, such as `1.0.15` to `1.0.16`. An exact `major.minor.patch` argument remains available when a minor or major version is needed. The script creates a lightweight tag at `HEAD` and pushes only that tag. Re-running it after a failed push is safe when the local tag already points to the same commit. It does not build, test, or publish files from the local machine.

A matching tag push triggers `.github/workflows/release.yml`. The pinned GitHub Actions environment checks out that commit on Ubuntu 24.04, installs the current Java LTS toolchain (Temurin JDK 25), and selects the project's checked-in Gradle 9.6.1 wrapper. It validates the tag format and Gradle wrapper, injects that tag as the application version shown in keyboard help, runs the root `test` task (core on JVM plus web in a JS browser), and builds `browserProductionDistribution`. Local builds identify themselves with the Gradle project version and can override it with `-PappVersion=<version>`.

The production acceptance test installs an exact Chrome for Testing build and serves that exact production directory. It opens representative seed-construction, primitive-transform, canonicalization, Kepler-Poinsot `D -> Stellate -> Greaten`, and compound construction/transformation configurations; each case must load through the real Compose UI, WasmGC worker, and canvas and produce its expected F/E/V counts. It also exports a hidden-cap Prism 5/2 and hidden-face Two tetrahedra through the independent STL Wasm worker, reads the intercepted Blob download, and validates that its ASCII STL payload has a named solid, at least one facet, exactly three vertices per facet, and a matching end marker. Additional JS/Wasm core test matrices, exhaustive browser combinations, the core-JS benchmark baseline, and benchmark-module variants are not release gates. The accepted production directory is uploaded as a GitHub Pages artifact and deployed through the protected `github-pages` environment. Validation must complete before deployment can start, and the Pages concurrency group serializes releases without cancelling an in-progress production deployment.

The repository's Pages publishing source is **GitHub Actions**. The custom domain remains a repository Pages setting and is not produced by a developer workstation.

## Source layout

```text
model/src/commonMain/      serializable presentation model and core contract
core/src/commonMain/       seed generation, manipulation algorithms, and core API
core/src/jvmMain/          JVM-only debugging command-line tools
core/src/wasmJsMain/       exported Wasm browser API
core/src/commonTest/       algorithm and API tests
web/src/jsMain/kotlin/     Compose DOM UI and WebGL renderer
web/src/jsMain/resources/  HTML and CSS
renderer/src/jsMain/       Node/headless-gl PNG renderer and CLI
benchmarks/src/commonMain/ identical JS/Wasm workloads
docs/                      live specification
```

Build development and production distributions separately. Kotlin/JS uses a shared package directory for those webpack modes, so asking Gradle to execute both webpack tasks in one invocation is unsupported.

Core tests that call suspending algorithms use `kotlinx-coroutines-test`, so the same test bodies run on JVM, JS, and Wasm without a synchronous Wasm bridge. The exhaustive browser geometry tests use a 30-second Mocha limit. Incremental Wasm linking is disabled only for the core test executable as a workaround for a Kotlin 2.4.10 linker failure; application executables retain incremental linking.

When behavior or structure changes, update the relevant live-spec file. Performance changes must rerun both production benchmark targets and replace the current results in `performance.md`.
