# Performance

## Current production results

Median time per uncached operation; lower is better.

| Workload | Kotlin/JS | Kotlin/WasmGC | Speedup | Time reduction |
| --- | ---: | ---: | ---: | ---: |
| Truncate / icosahedron | 286.526 µs | 154.199 µs | 1.86× | 46.2% |
| Cantellate / dodecahedron | 350.624 µs | 201.546 µs | 1.74× | 42.5% |
| Bevel / dodecahedron | 450.773 µs | 256.835 µs | 1.76× | 43.0% |
| Snub / dodecahedron | 391.938 µs | 237.539 µs | 1.65× | 39.4% |
| Chamfer / icosahedron | 412.374 µs | 212.608 µs | 1.94× | 48.4% |
| Canonicalization / irregular truncated cube | 6.179 ms | 4.362 ms | 1.42× | 29.4% |

The geometric-mean speedup across these six workloads is approximately 1.72×.

## Method

- Both executables are optimized production builds from the same Kotlin common source.
- The runtime is Gradle-managed Node.js 24.10.0 on Windows x64.
- Each operation receives a fresh polyhedron copied outside the timed region, preventing transform caches from turning the measurement into a cache lookup.
- The five direct transforms use 3 warmup batches and 12 measured batches. Canonicalization uses 5 warmups and 15 measured samples so runtime tiering has settled before its median is taken.
- The table reports the median in nanoseconds per operation converted to µs or ms.
- Both targets produced checksum `2,050,235`, confirming equivalent benchmark outputs.

These figures measure computation kernels only. Wasm download/instantiation, JSON transfer, Compose work, and WebGL rendering are outside the timed region.

Run the current comparison with:

```shell
./gradlew :benchmarks:jsNodeProductionRun
./gradlew :benchmarks:wasmJsNodeProductionRun
```
