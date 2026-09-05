# Non-convex geometry

This document owns concave and simple non-planar face behavior for embedded polyhedra. Intentional
face or surface crossings, nonzero-winding fill, and Resolved belong to
[Self-intersecting polyhedra](self-intersections.md).

## Embedded surface contract

A proper boundary is a consistently oriented embedded two-manifold, possibly with several closed
components as described in [Compounds](compounds.md). Every
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

The core supplies hidden-face rims as polygonal regions rather than consumer-specific triangles.
Simple planar insets stop before edge collapse or a concave reflex-corner collision. A simple
non-planar rim is clipped to the shared deterministic face triangles and lifted back to their
piecewise-planar surface, so triangulation seams do not become opening walls. The complete inset,
thickness, shared-join, immersed-strip, scale, and edge-case contract is owned by
[Rim geometry](rim_geometry.md).

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
