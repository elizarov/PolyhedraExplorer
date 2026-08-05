# Performance

## Current production results

Median time per uncached operation; lower is better.

| Workload | Kotlin/JS | Kotlin/WasmGC | Speedup | Time reduction |
| --- | ---: | ---: | ---: | ---: |
| Truncate / icosahedron | 266.656 us | 155.396 us | 1.72x | 41.7% |
| Detect seed / truncated icosahedron | 44.391 us | 20.244 us | 2.19x | 54.4% |
| Cantellate / dodecahedron | 324.856 us | 136.785 us | 2.38x | 57.9% |
| Bevel / dodecahedron | 453.099 us | 253.874 us | 1.78x | 44.0% |
| Snub / dodecahedron | 343.991 us | 226.803 us | 1.52x | 34.1% |
| Chamfer / icosahedron | 408.931 us | 210.950 us | 1.94x | 48.4% |
| Canonicalization / irregular truncated cube | 6.194 ms | 4.408 ms | 1.41x | 28.8% |

The geometric-mean speedup across these seven workloads is approximately 1.82x.

## Method

- Both executables are optimized production builds from the same Kotlin common source.
- The runtime is Gradle-managed Node.js 24.10.0 on Windows x64.
- Each operation receives a fresh polyhedron copied outside the timed region, preventing transform caches from turning the measurement into a cache lookup.
- Seed detection filters by FEV, compares one normalized projection fingerprint per directed edge orbit, and reuses cached catalog fingerprints. The benchmark times detection only, after warmup has populated the catalog cache.
- Seed detection runs in the Wasm worker only when the seed or transform chain changes. It is absent from scale-only requests, prior-state animation evaluation, animation frames, and other view/config updates.
- The six direct non-canonical workloads use 3 warmup batches and 12 measured batches. Canonicalization uses 5 warmups and 15 measured samples so runtime tiering has settled before its median is taken.
- Both targets produced checksum `2,599,235`, confirming equivalent benchmark outputs.

These figures measure computation kernels only. Wasm download/instantiation, JSON transfer, Compose work, and WebGL rendering are outside the timed region.

Run the current comparison with:

```shell
./gradlew :benchmarks:jsNodeProductionRun
./gradlew :benchmarks:wasmJsNodeProductionRun
```
