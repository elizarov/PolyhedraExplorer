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

## Classical star faces and the physical boundary

A classical Kepler-Poinsot polyhedron is an *immersed* regular polyhedron: its large regular
polygon or pentagram faces pass through one another. Feeding those faces directly to the renderer
would violate the project's embedded-surface contract and would produce an ambiguous STL. The core
instead resolves the face arrangement into the boundary of its nonzero-winding physical volume:

1. construct the twelve pentagon/pentagram planes or twenty great-icosahedron triangle planes;
2. split each plane polygon at every other face plane;
3. evaluate the signed ray winding immediately on both sides of every resulting cell;
4. retain only cells that separate zero winding from nonzero winding, orienting them outward;
5. merge coincident vertices, build one triangular two-manifold, and run the ordinary properness
   validator.

This representation preserves the visible solid form and full `I_h` symmetry without admitting
face intersections, disconnected compounds, or special cases in rendering and export. It also
means that the UI's F/E/V counts describe the resolved physical mesh, not the smaller abstract
regular map:

| Regular star form | Classical `F / E / V` | Resolved `F / E / V` |
| --- | --- | --- |
| Stellated dodecahedron | `12 / 30 / 12` | `60 / 90 / 32` |
| Great dodecahedron | `12 / 30 / 12` | `60 / 90 / 32` |
| Great stellated dodecahedron | `12 / 30 / 20` | `60 / 90 / 32` |
| Great icosahedron | `20 / 30 / 12` | `180 / 270 / 92` |

Classical Dual, Greaten, and Stellate recognize the scale-independent regular-star geometry and
operate on the abstract form. All other algorithms receive the resolved embedded mesh. This
distinction prevents the extra intersection cells from changing classical dual pairs.

## Shared surface triangulation

The model computes one scale-aware, deterministic ear-clipped triangulation for every face. Length,
area, and intersection tolerances scale with the actual mesh, so a small intermediate construction
has exactly the same validity as its display-scaled result. The same triangle indices drive WebGL
face fill, canvas face hit-testing, topology-compatible animation
subdivision, and STL export. OpenSCAD keeps a planar convex face as one polygon and emits the shared
triangles for concave or non-planar faces. Consequently, a concave notch is empty consistently in
the view, picking, and exported geometry; no consumer can accidentally restore a triangle fan.

Hidden-face rims are core-supplied polygonal regions. A simple face offsets adjacent edge lines in
its average plane, preserves the original three-dimensional boundary of a non-planar face, and
uses a four-width miter limit with a bevel beyond it. Its selectable width stops at the first edge
collapse or earlier concave reflex-corner collision. An immersed planar face instead unions strips
over its resolved nonzero-winding cells: exterior boundary segments receive the full configured
width inward, internal immersed segments receive half on each side, and crossings contain no
duplicate seams. The result carries deterministic outer/hole cycles and source-edge provenance;
WebGL and export code triangulate it independently.

## Transform applicability

Every completed primitive stage is checked with the proper-surface validator before it can reach
the renderer. A setting-dependent failure is returned as `InvalidGeometry` with the geometric
reason. Continuous-control safe ranges use the same check, so their selectable interval is derived
from the actual input mesh rather than a convex-only constant.

| Operation | Non-convex behavior |
| --- | --- |
| Truncated, Rectified | Unified edge-interpolation construction. Supported for a proper input when the resulting cuts remain proper. |
| Cantellated, Bevelled | Unified face/edge construction. Concave inputs are supported for proper parameter ranges; the result check bounds or rejects unsafe ranges. |
| Dual | For a recognized Kepler-Poinsot form, returns its classical star dual. Otherwise it tries the direct polar reciprocal of average face planes; if a reflex neighborhood makes that surface improper, it canonicalizes the input topology and reciprocates the convex realization. Singular or non-canonicalizable cases fail cleanly. |
| Greaten / Stellate | Build candidates from the input's own planar face constellation. Greaten retains cyclic face type while selecting a larger circuit; Stellate retains continued source edge lines while permitting a new winding step. Closed renderable non-catalog results are accepted; compounds, open incidence, non-planar sources, and unavailable Result selections are rejected with `TransformNotApplicable`. |
| Kis / Kis face | Full default Kis uses its Dual-Truncate-Dual definition and is accepted only when the result is proper. Continuous-height and orbit-targeted Kis require a topological dual and are deliberately unavailable on resolved regular-star meshes, whose classical dual has different physical cell topology. |
| Radial vertex / Stellate face | Radial vertex accepts an eligible independent vertex orbit surrounded by simple planar triangles, even when unrelated regions are immersed. Stellate face inherits selective Kis restrictions and applies only when its created apex orbit passes the same radial checks. |
| Join, Needle, Zip, Ortho, Meta | Their documented primitive expansions run under the same contract. Every Dual, Rectify, Truncate, Cantellate, or Bevel stage must be proper; a later stage cannot conceal an intersecting intermediate surface. |
| Gyro | Its Dual-Snub-Dual expansion validates the Snub surface and uses a strict final polar dual. It does not use the standalone Dual operation's canonical fallback, because that fallback could otherwise replace an invalid chiral construction with an unrelated convex realization. |
| Snub | Face rotation can overlap a concave boundary or a reflex neighborhood. Such inputs or settings are rejected by the intersection check. |
| Chamfered | The bisector construction can fold at reflex corners. It is supported only where its emitted face boundaries and full surface remain proper. |
| Propeller, Whirl, Quinto | Their incidence subdivision is canonicalized. They support a non-convex input topology when canonicalization converges, producing a convex canonical realization. |
| Canonical | Operates on topology and may replace a non-convex realization with its convex canonical realization. Solver or topology failures are reported by the transform stage. |
| Drop | The merged boundary must be one cycle, and the final surface must also pass simple-face, connectedness, and intersection validation. |
| Orbit-targeted Truncate / Rectify | Uses the same edge-cut construction and properness check as the full operation. |

The validator is the final authority even for operations described as supported: non-convexity is
not a single special case, and a sufficiently deep cut or offset can make an otherwise applicable
construction intersect.
