# Non-convex geometry

## Surface contract

A displayed polyhedron is one connected, consistently oriented two-manifold mesh. Every
undirected edge has exactly two oppositely directed face uses. A face boundary is a simple polygon;
the face itself may be concave and may be non-planar. A non-planar face denotes the deterministic
triangle surface obtained by projecting its boundary along the dominant component of its average
normal and ear-clipping that projection.

The core rejects:

- boundary edges, edges used by more than two faces, and equally directed adjacent face uses;
- repeated face vertices, degenerate edges or triangles, non-finite coordinates, and inward-facing
  or zero-volume surfaces;
- disconnected closed shells, because they are compounds rather than one polyhedron;
- self-intersecting face boundaries; and
- intersections between different face triangles except at the vertex or edge those faces
  explicitly share.

Planarity is deliberately not part of properness. `validateProperGeometry` accepts a non-planar
face when its deterministic triangle surface is otherwise valid. `validateGeometry` adds the
stricter all-faces-planar requirement for algorithms and tests that need it.

## Shared surface triangulation

The model computes one scale-aware, deterministic ear-clipped triangulation for every face. The
same triangle indices drive WebGL face fill, canvas face hit-testing, topology-compatible animation
subdivision, and STL export. OpenSCAD keeps a planar convex face as one polygon and emits the shared
triangles for concave or non-planar faces. Consequently, a concave notch is empty consistently in
the view, picking, and exported geometry; no consumer can accidentally restore a triangle fan.

Face rims offset adjacent edge lines in the face's average plane. Convex faces are limited by their
first edge collapse. Concave faces additionally search for the earlier reflex-corner collision at
which the inset would stop being a simple polygon. This keeps the rim width uniform while avoiding
folded or crossing rim strips. The same construction remains defined for non-planar faces.

## Transform applicability

Every completed primitive stage is checked with the proper-surface validator before it can reach
the renderer. A setting-dependent failure is returned as `InvalidGeometry` with the geometric
reason. Continuous-control safe ranges use the same check, so their selectable interval is derived
from the actual input mesh rather than a convex-only constant.

| Operation | Non-convex behavior |
| --- | --- |
| Truncated, Rectified | Unified edge-interpolation construction. Supported for a proper input when the resulting cuts remain proper. |
| Cantellated, Bevelled | Unified face/edge construction. Concave inputs are supported for proper parameter ranges; the result check bounds or rejects unsafe ranges. |
| Dual | Tries the direct polar reciprocal of average face planes. If a reflex neighborhood makes that surface improper, it canonicalizes the input topology and reciprocates the convex realization. Singular or non-canonicalizable cases fail cleanly. |
| Kis / Kis face | Uses the dual-truncate-dual regular realization. It is geometry-dependent on non-convex inputs and is accepted only when its result is proper. |
| Snub | Face rotation can overlap a concave boundary or a reflex neighborhood. Such inputs or settings are rejected by the intersection check. |
| Chamfered | The bisector construction can fold at reflex corners. It is supported only where its emitted face boundaries and full surface remain proper. |
| Propeller, Whirl, Quinto | Their incidence subdivision is canonicalized. They support a non-convex input topology when canonicalization converges, producing a convex canonical realization. |
| Canonical | Operates on topology and may replace a non-convex realization with its convex canonical realization. Solver or topology failures are reported by the transform stage. |
| Drop | The merged boundary must be one cycle, and the final surface must also pass simple-face, connectedness, and intersection validation. |
| Orbit-targeted Truncate / Rectify | Uses the same edge-cut construction and properness check as the full operation. |

The validator is the final authority even for operations described as supported: non-convexity is
not a single special case, and a sufficiently deep cut or offset can make an otherwise applicable
construction intersect.
