# Polyhedra Explorer

Polyhedra Explorer is an interactive browser application for constructing, transforming, inspecting, and exporting symmetric convex polyhedra. Its UI is written with Compose HTML, its renderer uses DOM + WebGL, and all seed construction and polyhedron manipulation runs in Kotlin/WasmGC.

The application includes 31 Platonic, Archimedean, and Catalan seeds; primitive polyhedron transforms plus nine Conway-style macros; animated transitions; orbit-aware face, edge, and vertex inspection; URL-backed state; and STL/OpenSCAD export.

## Build and run

Requirements: JDK 17+ and a browser with WasmGC support.

```shell
./gradlew browserDevelopmentDistribution
python -m http.server 8765 --directory build/dist/browser/development
```

Open `http://127.0.0.1:8765/`. For a production artifact:

```shell
./gradlew browserProductionDistribution
```

Run tests and benchmarks with:

```shell
./gradlew :core:jvmTest
./gradlew :benchmarks:jsNodeProductionRun
./gradlew :benchmarks:wasmJsNodeProductionRun
```

The maintained project specification starts at [docs/README.md](docs/README.md).

## License

Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
