# Canonicalization

## Result versus operation

A **canonical representation** is the normalized geometric realization of a polyhedron topology in which:

- every edge is tangent to one common midsphere;
- the centroid of the edge tangency points is the origin; and
- every face is planar.

The representation is unique up to rotation and reflection. **Canonicalization** is the iterative numerical operation used to find that representation. The UI names this operation `Canonical`.

## Processing model

The implementation uses an edge-nearpoint/circle-packing relaxation derived from the Koebe-Andreev-Thurston construction and the algorithm implemented by [Antiprism](https://github.com/antiprism/antiprism/blob/master/base/canonical.cc).

The solver constructs an internal processing mesh with one point for every source edge. Its faces form two interleaved circle packings:

- one processing face for every source face; and
- one processing face for every source vertex.

Each processing point therefore belongs to four faces: two from each packing. Opposite pairs represent the two circles that must be tangent at that point.

## Iteration

Each iteration performs the following operations:

1. Compute the centroid and average plane of every processing face.
2. Move each edge point toward the average of its projections onto its four surrounding planes.
3. Move it toward the two origin planes required by the opposing-face normals. This enforces tangency and orthogonality between the primal and dual circle packings.
4. Subtract the processing-point centroid so the canonical origin remains centered.
5. Normalize every processing point onto the unit sphere.
6. Adjust the relaxation factor: successful error reduction increases it gradually, while an increase in error reduces it.

Convergence is measured by the largest point offset relative to a `1e-12` target. Once converged, the source vertices are reconstructed from the vertex-derived processing-face planes by polar reciprocation. This preserves source topology and orbit-kind metadata while producing planar faces and midsphere-tangent edges.

## Implementation details

- Edge-to-point and face-adjacency tables are built once before iteration.
- Mutable point, plane, normal, and offset arrays are allocated once and reused.
- The adjustment factor starts at `0.01`, grows by `1.01`, shrinks by `0.995`, and is capped at `0.5`.
- Progress is logarithmic, monotonic, and emitted at most once per 100 ms.
- The coroutine yields at the same reporting boundary; in the browser the complete solver runs as WasmGC in the dedicated core worker.
- Changing to a newer UI state terminates the active worker, cancelling obsolete computation immediately.
- Degenerate/non-finite geometry fails explicitly, and a 100,000-iteration ceiling prevents unbounded execution.
- `isCanonical()` independently verifies planar faces, a centered tangency-point centroid, and equal edge tangent radii.

The solver processes every edge point; it does not currently reduce work by symmetry orbit.

## Verification

Core tests cover ordinary canonicalization and the high-complexity chain `Truncated cube → Cantellated → Chamfered → Snub → Canonical`. Browser tests verify that worker progress can repaint while the UI remains interactive. Performance is measured by the shared JS/Wasm benchmark described in [Performance](performance.md).
