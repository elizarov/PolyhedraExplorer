# Performance

## Current production results

Median time per uncached operation; lower is better.

| Workload | Kotlin/JS | Kotlin/WasmGC | Speedup | Time reduction |
| --- | ---: | ---: | ---: | ---: |
| Truncate / icosahedron | 325.680 µs | 157.800 µs | 2.06× | 51.5% |
| Cantellate / dodecahedron | 352.471 µs | 216.814 µs | 1.63× | 38.5% |
| Bevel / dodecahedron | 602.672 µs | 264.076 µs | 2.28× | 56.2% |
| Snub / dodecahedron | 530.886 µs | 238.703 µs | 2.22× | 55.0% |
| Chamfer / icosahedron | 521.857 µs | 213.624 µs | 2.44× | 59.1% |
| Canonicalization / irregular truncated cube | 4.036 ms | 2.917 ms | 1.38× | 27.7% |

The geometric-mean speedup across these six workloads is approximately 1.96×.

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
