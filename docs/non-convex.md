# Non-convex geometry

This document owns concave and simple non-planar face behavior for embedded polyhedra. Intentional
face or surface crossings, nonzero-winding fill, and Resolved belong to
[Self-intersecting polyhedra](self-intersections.md).

## Embedded surface contract

A proper polyhedron is one connected, consistently oriented embedded two-manifold. Every
undirected edge has exactly two oppositely directed face uses. Unrelated face surfaces meet nowhere;
adjacent faces meet only at their declared common vertex or edge. The surface encloses positive
signed volume and contains finite, non-degenerate geometry.

Convexity and planarity are independent of properness. A proper surface may be globally non-convex,
and an individual source face may be concave or non-planar. For this document, each source-face
boundary is simple. Self-crossing planar boundaries use the stronger immersed-surface model
specified in the linked self-intersection document.

The core rejects a proper-surface candidate with:

- a boundary edge, more than two uses of one edge, or equally directed adjacent uses;
- repeated face vertices, coincident distinct source vertices, degenerate edges or triangles, or
  non-finite coordinates;
- disconnected closed shells;
- a self-intersection or contact outside explicitly shared topology; or
- inward orientation or non-positive signed volume.

`validateProperGeometry` enforces this embedded contract using the exact presentation triangles
consumed downstream. `validateGeometry` additionally requires every source face to be planar for
algorithms and fixtures whose mathematical domain needs planar faces. The complete layered contract
is specified in [Self-intersecting polyhedra](self-intersections.md#validation-contracts).

## Shared face triangulation

The model computes one scale-aware deterministic triangulation for every simple face. A planar or
non-planar boundary is projected along the dominant component of its average normal. Planar faces
are ear-clipped in that stable two-dimensional basis. A folded face whose average projected point
sees every boundary edge receives a symmetry-preserving interior vertex and triangle fan; this
avoids choosing one privileged corner of a symmetric fold. Other non-planar faces are ear-clipped
using the shortest available three-dimensional diagonals, which keeps each fold local. The lifted
triangles define a non-planar face's actual surface; consumers do not infer a different curved or
planar patch.

The same resolved-face triangle indices drive:

- WebGL face fill and table shadows;
- canvas face hit testing;
- STL presentation construction; and
- OpenSCAD output for concave or non-planar regions.

Temporary animation topology remains boundary-only so interpolation does not change F/E/V or its
vertex correspondence; the completed mesh swaps to the shared resolved surface.

Consequently, a concave notch remains empty in rendering, picking, and export. No consumer may
replace the shared tessellation with a triangle fan or independently choose diagonals.

## Simple hidden-face rims

The core supplies hidden-face rims as polygonal regions rather than final consumer triangles. For a
simple planar face, adjacent boundary lines are offset in the face plane and meet at their exact
miter. Approximating that join can move the hole outside its source face and produce overlapping
renderer triangles. The maximum selectable width stops at the first edge collapse or concave
reflex-corner collision, so every emitted inset remains valid.

For a simple non-planar face, the same uniform inset band is built in the face's stable projection,
clipped by each triangle of the shared deterministic face tessellation, and lifted barycentrically
back to that triangle's plane. Each resulting region is therefore a planar patch, while their union
retains the source face's folds. Patch cuts introduced only by face tessellation carry no source-edge
provenance and do not become visible rim walls; source-boundary and inset segments do. This avoids
both a cap cutting across the folds and visible seams between adjacent patches.

Each `ResolvedRimGeometry` contains deterministic outer and hole cycles, source-edge provenance,
the applied width, and its maximum. A region also identifies when it is one clipped non-planar
triangle patch so consumers use its local plane. WebGL triangulates the regions for visible caps
and physical boundary walls; STL and OpenSCAD consume the same polygonal shape according to
[Export](export.md). Immersed-face strip
union and pentagram-rim semantics are specified separately in
[Self-intersecting polyhedra](self-intersections.md#hidden-immersed-faces).

## Transform applicability

Each primitive declares its minimum geometry contract, local face/plane requirements, topology
requirements, and output policy. The evaluator checks those declarations before construction and
validates every completed result afterward. A crossing or reflex neighborhood outside the affected
local patch does not disable an otherwise local operation.

Continuous-control ranges use the same operation-specific validation against the actual input mesh.
The supported interval can therefore shrink before an edge collapse, fold, invalid face plane, or
surface contact instead of relying on a convex-only constant. The operation-by-operation rules are
owned by [Transformations and macros](transformations.md); this document does not duplicate that
matrix.
