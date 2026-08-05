# Architecture

## Modules

| Module | Targets | Responsibility |
| --- | --- | --- |
| `model` | JVM, JS, WasmGC | Serializable mesh/presentation types, vector math needed for rendering and inspection, and the browser core-request/response contract. It contains no seed generator or manipulation transform. |
| `core` | JVM, JS, WasmGC | Seed construction, topology manipulation, transforms, scaling, validation, browser API evaluation, and JVM tests. The browser executes this module only as WasmGC; its JS target exists solely for the controlled baseline benchmark. |
| `web` | JS | Compose HTML controls, URL parameter state, animation interpolation, inspection/export UI, and the WebGL renderer. It consumes completed meshes and metadata; it does not invoke manipulation transforms. |
| `benchmarks` | JS, WasmGC | Identical production benchmark workloads over the core algorithms. |

## Browser runtime

```mermaid
flowchart LR
    U["User input / URL state"] --> C["Compose HTML controls"]
    C --> B["JSON core request"]
    B --> Q["Dedicated Web Worker"]
    Q --> W["Kotlin/WasmGC core"]
    W --> R["Mesh, topology, issues, animation keyframes"]
    W -. "canonicalization progress" .-> Q
    Q -. "progress messages" .-> C
    R --> V["JS view model"]
    V --> D["DOM inspection UI"]
    V --> G["WebGL renderer"]
    V --> E["STL / OpenSCAD export"]
```

`CoreClient.kt` is the browser-to-core invocation boundary. It owns a dedicated Web Worker, sends a serialized `CoreRequest`, receives progress messages, and decodes `CoreResponse`. The small `core-worker.js` shim dynamically imports the generated Wasm module and relays JSON messages; it contains no manipulation logic. Canonicalization and every other core operation therefore execute as WasmGC off the browser's main thread. Starting a newer request terminates an in-progress worker so stale expensive work cannot block the next result.

The generated Kotlin loader and the worker's dynamic-import expression are the only JavaScript interop required for core execution. The production web bundle depends on `model`, not `core`, so it cannot contain a JavaScript fallback copy of the manipulation engine.

Canonical representation invariants and the current circle-packing solver are specified in [Canonicalization](canonicalization.md).

The Wasm core owns:

- seed geometry construction;
- truncate, rectify, cantellate, dual, bevel, snub, chamfer, canonicalization (the UI's `Canonical` transform), and drop operations;
- size guards, applicability checks, warnings, and progress;
- scale normalization and topology/drop analysis;
- topology-changing animation keyframe construction.

The JS application owns DOM composition, user events, hash serialization, interpolation between returned keyframes, render-buffer generation, WebGL drawing, and file download.

## State and data flow

`RootParams` is the authoritative UI state. Its compact serialization is stored after `#/` in the URL, so reloads and copied links reproduce the current seed, transform chain, view, lighting, animation, and export settings.

Every seed/transform/scale change creates a `CoreState`. Results from superseded requests are ignored. A response contains the scaled display mesh, unscaled intermediate meshes, valid transform tags, per-stage droppable kinds, structured issues, and optional animation steps. Progress arrives as separate worker messages. Compose receives an explicit core-update signal so asynchronous results and progress are rendered immediately.

## Build outputs

`browserDevelopmentDistribution` and `browserProductionDistribution` assemble one deployable directory:

```text
build/dist/browser/<mode>/
├── index.html
├── PolyhedraExplorer.js
├── core-worker.js
├── css/
└── core/
    ├── PolyhedraExplorer-core.mjs
    ├── PolyhedraExplorer-core.wasm
    └── generated Kotlin/Wasm support modules
```

The site must be served over HTTP; loading it directly from the filesystem is unsupported because the Wasm module is fetched as an ES module.

## Invariants

- Browser manipulation algorithms execute through `evaluateCoreJson` in WasmGC inside a dedicated worker.
- CPU-intensive transforms cannot block DOM/WebGL interaction, and worker messages repaint progress on the main thread.
- The JS bundle contains the mesh presentation model and Wasm loader, but no seed-generation or transform implementation.
- The UI renders with DOM + WebGL; no canvas UI toolkit owns the controls.
- Transform order is significant and intermediate topology metadata corresponds to the same order.
- A displayed polyhedron never exceeds 32,767 edges.
- JS and Wasm benchmarks run the same common source and must produce the same checksum.
