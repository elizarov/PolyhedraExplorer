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

## Cold Greatened Snub Cube construction

The focused JVM benchmark measures full, uncached enumeration and validation of the two supported
Greatened Snub Cube results. It clears the constellation candidate cache immediately before each
timed construction and verifies the stable candidate sequence `F/E/V = 38/240/158, 38/252/156`.
The cache used for normal Result switching therefore cannot shorten the measured operation.

| Winding classification | Median | Min | Max | Samples |
| --- | ---: | ---: | ---: | ---: |
| Solid angle for every query | 16.882 s | 16.746 s | 16.984 s | 3 |
| Axis ray with solid-angle fallback | 2.917 s | 2.905 s | 3.318 s | 5 |

The current classifier is **5.79x faster** on this construction, reducing the median by 82.7%.
Measurements used the Gradle-managed JDK 25 on an AMD Ryzen 9 5900X, with one warmup construction
before each measured run.

A baseline Flight Recorder profile attributed 93% of execution samples to resolved-boundary
construction and 86% specifically to solid-angle winding classification. Transcendental angle
functions accounted for about 71% of top-frame samples, while faceting search accounted for about
2%. The resolved-boundary classifier now tries three axis rays, which compute the same generalized
winding for closed oriented embedded or immersed surfaces, and uses the solid-angle calculation
only when every ray is degenerate on a boundary feature. This preserves the robust boundary path
without paying its trigonometric cost for ordinary classification points.

Run the regression benchmark with:

```shell
./gradlew :core:benchmarkGreatenedSnubCube
```

Add `-PbenchmarkJfr=true` to capture
`core/build/reports/benchmarks/greatened-snub-cube.jfr` for another profile.

## Interactive star-result enumeration

Uncached Stellated enumeration for Deltoidal hexecontahedron is a JVM regression workload. It must
produce all 27 supported results in under 3 seconds and independently validate every resulting
immersion and resolved physical boundary. The candidate search reuses one plane diagram per face
orbit, expands it through geometric symmetries, uses spatial indices for tolerant point merging,
and constructs physical stratum boundaries only when they are needed. Greatened and Stellated
enumeration both publish monotonic intermediate worker progress; cached result sets complete
without repeating candidate discovery. Each cached result also retains its geometry-contract
analysis, full point-group and F/E/V orbit classification, orbit-action availability, and its most
recent presentation-rim geometry. Returning to a large result therefore avoids repeated
classification and derives Stellate-face availability with one full-Kis
construction instead of one construction per face orbit. Switching between two Stellated Results
does not evaluate the previous result because this discrete setting has no animation keyframes.
The face-orbit classifier maps a transformed face through one of its boundary edges instead of
allocating and sorting a vertex list for every symmetry operation. The JVM regression covers
switching to Result 15 (`F = 2820`) from a warm candidate set and requires
the complete core response in under 2 seconds.

Run the current comparison with:

```shell
./gradlew :benchmarks:jsNodeProductionRun
./gradlew :benchmarks:wasmJsNodeProductionRun
```
