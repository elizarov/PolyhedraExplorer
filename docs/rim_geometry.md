# Rim geometry

This document owns the geometric contract for visible face thickness and hidden-face rims. A rim is
material that follows the authoritative boundary of a hidden face; `Width` is the requested
perpendicular depth of the material and `Rim` is the requested in-face width of the visible band.
Rendering, STL, and OpenSCAD start from the same polygonal rim regions and thickness joins.

## Inputs and output

The construction receives the source `Polyhedron`, its derived `ResolvedFaceGeometry`, the set of
faces that contain material, the set represented only by rims, and the requested rim and face
widths. All distances are in normalized model coordinates. Export applies its millimetre scale only
after constructing the geometry.

For each source face the core returns `ResolvedRimGeometry`:

- one or more simple outer cycles with zero or more hole cycles;
- the applied and maximum usable widths;
- source-face and source-edge provenance for every cycle segment;
- a patch marker when a non-planar face must retain its piecewise-planar triangulation.

The result is polygonal and tessellation-free. Each consumer triangulates it for its own format.

## Face fill and rim regions

Every face first has a two-dimensional arrangement in its face plane. The arrangement splits all
source-edge crossings, classifies its cells by winding number, and retains the nonzero-winding
cells. These resolved cells define face fill; the self-intersecting source trace remains the
authoritative path that an immersed rim follows.

### Simple planar faces

For a simple face, each directed edge is projected into the face plane and rotated inward. At a
vertex, the unit-width inset direction is the intersection of the two adjacent offset edge lines:

`m = (i_previous + i_next) / (1 + i_previous · i_next)`.

Multiplying `m` by the requested rim width leaves both inset edges exactly that distance from their
source edges. This uses the actual corner angle and edge lengths, so regularity, tangency, and equal
edge lengths are not assumed.

The maximum fixed-topology inset is the first edge-collapse distance. Concave faces can become
self-intersecting before that distance, so the interval is sampled and the first invalid interval is
bisected. At the maximum, the rim becomes the complete filled face; below it, the outer face cycle
and reversed inset cycle form a region with one hole.

### Self-intersecting planar faces

An immersed face does not inset its already-resolved cells separately. Each authoritative source
edge produces one uninterrupted one-sided quadrilateral sheet between the edge and its offset line.
All sheets are unioned in the common face plane:

1. split sheet and optional clipping boundaries at every segment intersection;
2. classify both sides of each split segment against the union of source-edge sheets;
3. retain only segments separating material from empty space;
4. pair the directed segments into outer and hole cycles while preserving source-edge provenance.

Crossings can subdivide the union boundary for representation, but never terminate, recenter, or
change the side of a source-edge sheet. Different per-edge widths use the same arrangement with the
actual offset-line intersection at every source vertex.

The maximum slider width is the first width whose sheet union covers all nonzero-winding cells. It
is found by scale-based upper-bound growth followed by binary search; this is a UI range, not a
catalogue-specific value and not a different presentation geometry.

### Simple non-planar faces

A non-planar face uses its deterministic resolved triangles as the actual surface. Its rim band is
constructed once in the face's average-plane projection, clipped against each resolved triangle,
and lifted back by that triangle's barycentric coordinates. The resulting patches retain the same
folds as face rendering. A common conservative width is used when edge-specific widths would change
the clipping topology between patches.

## Perpendicular thickness and shared joins

The inner surface of a material face is offset by `Width` along a direction `d` whose projection on
every incident outward unit normal is one. Thus `p_inner = p_outer - Width · d` has exactly the
configured perpendicular depth in every incident face.

Along an edge with normals `n1` and `n2`, the minimum-norm solution is

`d = (n1 + n2) / (1 + n1 · n2)`.

This is the intersection of the two equally offset face planes and lies on their dihedral bisector.
At each ordinary face corner, the solver uses that face and the material faces across its incoming
and outgoing source edges. It chooses a finite three-normal solution; duplicate normals are
removed. One or two independent normals reduce to the normal or edge formula. If an adjacent face
has no material, that side contributes no constraint.

Acute immersed configurations can make the unconstrained solution travel beyond the material
available in an incident face. The complete join vector is then scaled uniformly until it first
reaches either:

- the inner edge of a configured rim band; or
- the first boundary of a resolved filled face along the tangential displacement; or
- the first boundary of the actual resolved rim region.

Both faces of an edge receive the same scaled three-dimensional join point. The rim-region test is
needed at an immersed crossing: a displacement can remain inside the nonzero-winding face fill but
leave the particular one-sided rim sheet.

A self-intersecting hidden face keeps the same connected resolved-rim triangulation on its top and
underside. A source edge uses its exact shared bisector direction; an opening boundary uses the face
normal. Moving those vertices by the full requested depth can fold an underside triangle across a
hole because the planar union no longer records which overlapping source sheet owns a crossing.
The renderer therefore finds the largest common depth factor for which every underside triangle
stays inside the actual resolved rim. The containment check covers triangle vertices, edge
midpoints, and centroids, and a binary search finds the factor.

The factor multiplies every thickness direction in the presentation, not only the failing face or
individual vertices. Adjacent faces therefore retain identical shared joins, opening walls reach
the same underside, and the surface remains connected. Ordinary geometry uses factor one. Only an
immersed rim whose requested depth would invert its resolved surface is made uniformly shallower;
its visible top rim keeps the configured in-face width.

For an embedded planar surface, the visible rim is widened when necessary to let a perpendicular
opening wall reach the shared inner bisector. The required in-face distance is the tangential part
of `Width · d`. Immersed and folded surfaces retain the configured top-rim width; only their
underside join is bounded. This prevents an acute dihedral from making the visible strip arbitrarily
wide.

## Consumer geometry

- **WebGL** triangulates the returned outer and hole cycles. It emits the top and shared inner
  surfaces plus walls around actual opening cycles. A source edge shared with another material face
  has no separate wall. Immersed-rim thickness uses the common safe-depth factor described above.
- **STL** constructs the complete thick presentation, corefines all triangle intersections, and
  selects the zero/nonzero-winding solid boundary. High-winding arrangements remain an indexed
  triangle boundary through quantization; they are not converted back into source-style polygon
  faces, because a valid STL boundary can contain junctions that have no single abstract source
  vertex fan. Every final triangle is non-degenerate, every edge has two opposite incidences, the
  mesh is connected, and volume is positive before serialization.
- **OpenSCAD** receives closed polygonal face and rim pieces. Shared joins define their lower
  vertices; OpenSCAD performs the final union and tessellation.

Nonzero face expansion separates faces, so shared offset-plane joins no longer apply. Those export
pieces use their own perpendicular extrusion and are unioned downstream.

## Numerical and geometric edge cases

| Case | Behavior |
| --- | --- |
| Zero rim | The hidden face contributes no top rim region. |
| Zero width | Only surface geometry is produced; no inner surface or depth wall is required. |
| Collapsed projected edge or 180-degree planar corner | No unique finite inset exists, so the simple-face maximum rim is zero. |
| Concave corner collision | The maximum rim stops before the first invalid inset, even when that precedes an edge collapse. |
| Source-edge crossing | The continuous one-sided sheets overlap and are resolved as a planar union; they are not clipped into independent edge fragments. |
| Very acute dihedral | The common underside join stops at the first available-material boundary instead of inverting or floating outside the face. |
| Missing material on one adjacent face | The remaining face normal supplies the offset direction on that side. |
| More than three non-concurrent offset planes at one immersed vertex | The least-residual shared direction is bounded by every incident filled/rim region; if the resulting immersed underside would fold, the whole presentation uses one common safe depth factor. |
| Non-planar face | Projection is used only for clipping; triangle-wise barycentric lifting restores the actual folded surface. |
| Multiple coplanar or transverse STL pieces | The three-dimensional arrangement splits intersections and removes internal fragments by solid winding. |
| High-winding point junction | STL keeps the edge-closed arrangement triangles directly instead of forcing them through the stricter source-polyhedron vertex-fan model. |

## Scale behavior and guards

The geometric distances come from the requested rim and width, actual edge lengths, face angles,
and resolved arrangement intersections. There are no seed names, catalogue radii, or fixed model
distances in the construction. Scaling a polyhedron and both requested widths by the same factor
therefore scales the ideal result by that factor.

Topology necessarily changes discretely when an inset edge collapses, a hole closes, or arrangement
boundaries meet. Numerical guards are otherwise scale-relative:

- planar rim arrangement tolerance is `max(16 · EPS · projectedScale, 10⁻¹² · projectedScale)`;
- STL arrangement tolerance is `max(32 · EPS · circumradius, 10⁻¹² · circumradius)`;
- boundary-side samples move by the larger of eight tolerances and `10⁻⁷` of the local segment;
- maximum-width searches use geometric coverage, not a fixed width.

Two local cleanup guards are intentionally proportional to the requested rim: clipped stair-step
joins are replaced only within approximately one rim width, and an inferred miter farther than four
rim widths is rejected. The stable STL rim-piece path uses the same four-width neighborhood when
joining fragments at a source vertex. These ratios prevent unbounded acute miters; they do not set
the resulting rim size.

## Ownership

Core region construction is in `core/.../poly/ResolvedRim.kt`; planar inset primitives and shared
thickness joins are in `model/.../poly/FaceRim.kt` and `FaceThicknessJoins.kt`. WebGL buffer
construction, STL solid arrangement, and OpenSCAD emission are consumers and must preserve this
geometry contract rather than independently reconstructing rim regions.
