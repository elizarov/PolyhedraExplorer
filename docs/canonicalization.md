# Canonicalization

## Result versus operation

A **canonical representation** is the normalized geometric realization of a polyhedron topology in which:

- every edge is tangent to one common midsphere;
- the centroid of the edge tangency points is the origin; and
- every face is planar.

The representation is unique up to rotation and reflection. **Canonicalization** is the iterative numerical operation used to find that representation. The UI names this operation `Canonical`.

## Processing model

The implementation uses an edge-nearpoint/circle-packing relaxation derived from the Koebe-Andreev-Thurston construction and the algorithm implemented by [Antiprism](https://github.com/antiprism/antiprism/blob/master/base/canonical.cc).

The full conceptual processing mesh has one point for every source edge. Its faces form two interleaved circle packings:

- one processing face for every source face; and
- one processing face for every source vertex.

Each processing point therefore belongs to four faces: two from each packing. Opposite pairs represent the two circles that must be tangent at that point.

The iterative solver runs on the rotational-symmetry quotient of that mesh. It keeps one mutable point for each geometric edge orbit and one plane for each geometric source-vertex or source-face orbit. Incidences in the quotient carry precomputed rotations between the representative local frames, so representative face planes and edge points can be viewed in the coordinate frame needed by each relaxation equation.

## Iteration

Each iteration performs the following operations:

1. Compute the centroid from the edge-orbit representatives and the precomputed sum of their member rotations.
2. Compute the average plane of every processing-face orbit from rotated edge-orbit representatives.
3. Move each edge-orbit representative toward the average of its projections onto its four surrounding face-orbit planes.
4. Move it toward the two origin planes required by the opposing-face normals. This enforces tangency and orthogonality between the primal and dual circle packings.
5. Subtract the processing-point centroid so the canonical origin remains centered.
6. Normalize every edge-orbit representative onto the unit sphere.
7. Adjust the relaxation factor: successful error reduction increases it gradually, while an increase in error reduces it.

Convergence is measured by the largest representative-point offset relative to a `1e-12` target. Rotations preserve offset length, so this is also the maximum over the full symmetric packing. Once converged, each vertex-derived representative plane is rotated to all members of its orbit and the source vertices are reconstructed by polar reciprocation. The full mesh is therefore expanded only once, after iteration. This preserves source topology and orbit-kind metadata while producing planar faces and midsphere-tangent edges.

## Implementation details

- Edge-to-point and face-adjacency tables are built once before iteration.
- Local orthonormal frames and proper rotation matrices map each edge and processing face from its orbit representative to every symmetric copy.
- Candidate kind groups are verified against their initial local packing neighborhoods. Geometrically inconsistent members are split into separate solver orbits, preserving correctness for perturbed input whose kind metadata overstates its actual symmetry.
- Each edge orbit stores the sum of its member rotation matrices. Applying this one operator to the representative computes that orbit's complete centroid contribution without visiting its members.
- Mutable representative points, quotient face planes, transformed incidence planes, and offsets are allocated once and reused.
- Per-iteration complexity is proportional to the number of point/face orbits and quotient incidences, rather than the full mesh size. Expansion to all source vertices occurs once after convergence.
- The adjustment factor starts at `0.01`, grows by `1.01`, shrinks by `0.995`, and is capped at `0.5`.
- Progress is logarithmic, monotonic, and emitted at most once per 100 ms.
- The coroutine yields at the same reporting boundary; in the browser the complete solver runs as WasmGC in the dedicated core worker.
- Changing to a newer UI state terminates the active worker, cancelling obsolete computation immediately.
- Degenerate/non-finite geometry fails explicitly, and a 100,000-iteration ceiling prevents unbounded execution.
- `isCanonical()` independently verifies planar faces, a centered tangency-point centroid, and equal edge tangent radii.

## Verification

Core tests verify exact declared-orbit reduction for every catalog seed and every standard transform of the Platonic seeds, fallback splitting for perturbed geometry, canonical invariants, and the high-complexity chain `Truncated cube → Cantellated → Chamfered → Snub → Canonical`. Browser tests verify that worker progress can repaint while the UI remains interactive. Performance is measured by the shared JS/Wasm benchmark described in [Performance](performance.md).
