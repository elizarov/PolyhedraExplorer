# Performance

## Current production results

Median time per uncached operation; lower is better.

| Workload | Kotlin/JS | Kotlin/WasmGC | Speedup | Time reduction |
| --- | ---: | ---: | ---: | ---: |
| Truncate / icosahedron | 258.880 us | 140.156 us | 1.85x | 45.9% |
| Detect seed / truncated icosahedron | 42.422 us | 19.214 us | 2.21x | 54.7% |
| Cantellate / dodecahedron | 314.032 us | 169.859 us | 1.85x | 45.9% |
| Bevel / dodecahedron | 464.731 us | 251.529 us | 1.85x | 45.9% |
| Snub / dodecahedron | 355.485 us | 238.663 us | 1.49x | 32.9% |
| Chamfer / icosahedron | 394.481 us | 203.119 us | 1.94x | 48.5% |
| Canonicalization / irregular truncated cube | 1.618 ms | 1.760 ms | 0.92x | -8.8% |

The geometric-mean speedup across these seven workloads is approximately 1.68x.

Canonicalization's symmetry quotient reduces this benchmark from 36 edge points and 38 processing faces to 2 point orbits and 3 face orbits. Against the same production benchmark before quotienting, its median fell from 6.039 ms to 1.618 ms in JS (3.73x, 73.2% less time) and from 4.448 ms to 1.760 ms in WasmGC (2.53x, 60.4% less time). Both versions converge in 544 iterations; only the work per iteration changed. The quotient kernel is small enough that WasmGC's advantage does not appear on this workload, although the quotient substantially improves both targets.

## Method

- Both executables are optimized production builds from the same Kotlin common source.
- The runtime is Gradle-managed Node.js 24.10.0 on Windows x64.
- Each operation receives a fresh polyhedron copied outside the timed region, preventing transform caches from turning the measurement into a cache lookup.
- Seed detection filters by FEV, compares one normalized projection fingerprint per directed edge orbit, and reuses cached catalog fingerprints. The benchmark times detection only, after warmup has populated the catalog cache.
- Seed detection runs in the Wasm worker only when the seed or transform chain changes. It is absent from scale-only requests, prior-state animation evaluation, animation frames, and other view/config updates.
- Canonicalization validates rotational kind groups geometrically, iterates only their quotient points, planes, and incidences, then rotates the converged vertex planes to every symmetric copy once during final reconstruction.
- The six direct non-canonical workloads use 3 warmup batches and 12 measured batches. Canonicalization uses 5 warmups and 15 measured samples so runtime tiering has settled before its median is taken.
- Both targets produced checksum `2,599,235`, confirming equivalent benchmark outputs.

These figures measure computation kernels only. Wasm download/instantiation, JSON transfer, Compose work, and WebGL rendering are outside the timed region.

Run the current comparison with:

```shell
./gradlew :benchmarks:jsNodeProductionRun
./gradlew :benchmarks:wasmJsNodeProductionRun
```
