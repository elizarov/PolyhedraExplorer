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
5. During an initial unscrambling phase, detect a packing point outside the spherical cycle of its four neighbors and move it toward their centroid.
6. Subtract the processing-point centroid so the canonical origin remains centered.
7. Normalize every edge-orbit representative onto the unit sphere.
8. Adjust the relaxation factor: successful error reduction increases it gradually, while an increase in error reduces it.

The overlap correction runs at `0.1` for at most 10,000 iterations. If it is still active at that boundary, it is disabled and the smooth relaxation restarts conservatively at `0.001`; this avoids both persistent overlap-boundary cycles and aggressive post-unscrambling collapse.

Convergence is proposed when the largest representative-point offset reaches the `1e-12` target. Rotations preserve offset length, so this is also the maximum over the full symmetric packing. A proposal is accepted only after reconstructing the polyhedron and independently verifying all canonical invariants; reducing the relaxation factor therefore cannot create a false convergence result. Each vertex-derived representative plane is then rotated to all members of its orbit and the source vertices are reconstructed by polar reciprocation. The full mesh is expanded only once on the normal path, after iteration.

## Recovery paths

The rotational quotient is the primary and usual solver. If it fails or exhausts its iteration limit, canonicalization retries deterministically from the original topology in this order:

1. the full packing from edge centroids, without orbit reduction;
2. the full packing from edge near-points, which has a different basin for near-canonical inputs; and
3. the full packing from a topology-only spherical embedding.

The topology-only initializer fixes the largest processing face to a planar circle, solves a Tutte barycentric embedding for the remaining four-neighbor processing vertices, and lifts it to the unit sphere by inverse stereographic projection. This supplies a crossing-free start independent of pathological input coordinates. These recovery paths are exceptional; the regular iteration remains proportional to the number of symmetry orbits.

## Implementation details

- Edge-to-point and face-adjacency tables are built once before iteration.
- Local orthonormal frames and proper rotation matrices map each edge and processing face from its orbit representative to every symmetric copy.
- Candidate kind groups are verified against their initial local packing neighborhoods. Geometrically inconsistent members are split into separate solver orbits, preserving correctness for perturbed input whose kind metadata overstates its actual symmetry.
- Each edge orbit stores the sum of its member rotation matrices. Applying this one operator to the representative computes that orbit's complete centroid contribution without visiting its members.
- Mutable representative points, quotient face planes, transformed incidence planes, and offsets are allocated once and reused.
- Per-iteration complexity is proportional to the number of point/face orbits and quotient incidences, rather than the full mesh size. Expansion to all source vertices occurs once after convergence.
- The adjustment factor starts at `0.01`, grows by `1.01`, shrinks by `0.995`, and is capped at `0.5`.
- A singular opposing-normal cross product is skipped for that iteration; other constraints can move the packing away from the singularity so the correction becomes available later.
- Progress is logarithmic, monotonic, and emitted at most once per 100 ms.
- The coroutine yields at the same reporting boundary; in the browser the complete solver runs as WasmGC in the dedicated core worker.
- Changing to a newer UI state terminates the active worker, cancelling obsolete computation immediately.
- Degenerate/non-finite geometry fails explicitly, and each deterministic attempt has a 100,000-iteration ceiling.
- `isCanonical()` independently verifies planar faces, a centered tangency-point centroid, and equal edge tangent radii.

## Verification

Core tests verify exact declared-orbit reduction for every catalog seed and every standard transform of the Platonic seeds, fallback splitting for perturbed geometry, canonical invariants, the high-complexity chain `Truncated cube → Cantellated → Chamfered → Snub → Canonical`, and 24 minimized robustness regressions spanning catalog/family seeds, macros, continuous settings, and orbit-targeted transforms. A deterministic research audit additionally canonicalized 10,000 valid pseudo-random constructions of up to 500 faces without a failure. Tests also verify monotonic stage-local progress and that canonical convergence is attributed to the Canonical transform index. Browser tests verify that the progress pill moves to the stage reported by the worker while the UI remains interactive. Performance is measured by the shared JS/Wasm benchmark described in [Performance](performance.md).
