# Polyhedral compounds

A compound contains several closed polyhedral surface components in one configuration. Components
are defined by face/edge incidence, not by vertex proximity or material intersections. Members can
be embedded or immersed.

## Source representation

`Polyhedron` retains flat vertex/face arrays. Cached `components` partitions faces by shared edges;
`vertexComponentIds` identifies each vertex's member. Every member remains an oriented two-manifold:
two opposite uses per edge and one closed fan per vertex. Coincident positions in different members
keep different IDs. F/E/V includes these copies: Five cubes has 30/60/40, but only 20 distinct vertex
positions. Indexed serialization preserves this topology without a new format.

`compound` builds a disjoint union without welding. `surfaceFromCycles` splits independent vertex
fans in generated geometric circuits. Neither inserts intersection vertices nor erases internal
members. The shared edge limit applies to the complete compound.

## Symmetry and recognition

A symmetry must map the whole arrangement, including every directed face circuit. At coincident
positions, matching traverses an entire oriented component instead of choosing an arbitrary nearest
vertex. Proper rotations determine F/E/V orbits and UI kinds; reflections are not rotations. Ten
tetrahedra therefore has two proper-rotation orbits of each element type, although its full point
group exchanges its two chiral sets.
If a transform makes two complete members coincide, their exchange also joins element orbits;
it is not counted as an extra spatial rotation when naming the point group.

Catalogue recognition verifies relative placement and handedness under one global proper rotation.
A bag of individually congruent members is insufficient. Five tetrahedra and Five tetrahedra′ are
distinct recognized arrangements.

## Operations and physical material

Local Conway operations preserve independent component topology. Canonicalization solves each
member's circle packing separately, so overlapping members do not repel each other. Its common
edge-tangency normalization applies to all members. Targeted operations address global rotation
orbits, not member indices. Existing geometric applicability rules remain in force.

Stellated and Greatened may return compounds; enumeration and ordering belong to
[Transformations](transformations.md). The five classic regular seeds belong to [Seeds](seeds.md).
Disconnectedness alone never disqualifies a candidate.

Resolved and export operate on physical material: each member uses nonzero winding and members are
unioned. Coplanar overlaps share arrangement cuts and deterministic triangulation. Separate physical
components are retained, never discarded or connected with invented struts. OpenSCAD receives the
closed boundary or closed presentation pieces; STL receives the non-intersecting physical boundary.
Detailed postconditions belong to [Export](export.md). Hidden rims use each member's own adjacent
faces for joins, never the exterior union as a direction oracle.

## Verification

Tests check member regularity, component counts, serialization, duality, point groups, rotation
orbits, and handed recognition. The primitive/macro matrix runs on every regular compound.
Discovery tests construct classical compounds from octahedral and icosahedral geometry alone.
Export tests cover full faces, hidden rims, and separated physical components.
