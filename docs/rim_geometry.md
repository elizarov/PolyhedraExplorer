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

## Hard geometry contracts

The presentation with hidden faces must satisfy three independent whole-presentation invariants:

- it must not protrude into the unbounded exterior of the complete resolved polyhedron with every
  face present;
- its emitted surfaces must be closed. Surfaces may intersect and need not share the same
  triangulation, but every triangle edge must be covered completely by another surface. Exact
  paired edges, subdivided edges, and an edge meeting the interior of another surface are all
  valid; an uncovered interval is a hole.
- all emitted triangles must belong to one geometrically connected material complex. A shared
  edge, positive-length coplanar overlap, or positive-length transverse intersection connects two
  surfaces; an isolated point contact does not make a detached sheet valid.

The reusable presentation validator builds the actual WebGL triangles. For cases whose exterior is
described directly by the nonzero generalized-winding solid, it tests vertices and edge/centroid
samples, probes both sides of every transverse surface intersection, and reports the first
protruding triangle and point. Its closure pass first pairs exact mesh edges, then tests remaining
edges against intersecting triangle surfaces so that valid T-junctions do not produce false holes.
Its connectivity pass walks those same positive-length surface contacts and reports detached
components separately from holes.

That external test is necessary but cannot validate internal immersed sheets: a legitimate source
sheet can pass through a zero-winding three-dimensional cell that is nevertheless enclosed by the
outer presentation. The construction therefore also has independent local contracts:

- thickness direction is derived only from incident source faces, never from resolved-solid point
  classification or from which face orbits happen to be visible;
- every ordinary face uses the same all-face bisector-derived bottom join as an embedded convex
  presentation; an adjacent immersed face cannot replace or scale that join in its own plane;
- incident bottom outlines either share the same geometric rail or are joined by one explicit
  transition wall; role labels alone never decide whether the wall is needed;
- every top-to-opening, opening-to-underside, and face-to-face seam is fully surface-covered;
- every occurrence of a given bottom-corner role uses the same locally constructed point;
- every emitted top, underside, transition-wall, and opening-wall triangle is finite,
  non-degenerate, and consistently oriented with its own surface normal;
- every opening wall is wound and lit toward its opening;
- an immersed WebGL presentation is two-sided. Immersion can expose either side of a source sheet
  to an exterior winding cell, so global back-face culling cannot decide material coverage. A
  raster regression starts with culling enabled, compares the result with an explicitly two-sided
  reference, and requires zero pixels where culling exposes background that the reference covers.
  Back-facing fragments reverse their lighting normal so they remain recognizable as material;
- the opaque edge overlay draws only source-edge occurrences belonging to front-facing adjacent
  faces. Every resulting edge pixel must overlay face material; a back-facing occurrence cannot
  float across an opening or erase an already-rendered face pixel;
- hiding or showing another face cannot move an existing source-edge or source-vertex join.

Tests exercise these contracts over convex, stellated, and star-family examples, including complete
and mixed face-orbit visibility. Rim smaller than thickness, rim equal to thickness, rim larger
than thickness, and narrow-rim/large-thickness cases are separate settings in the test matrix.
High-winding pyramids are included in whole-presentation closure and local-join tests even where a
global nonzero-winding containment classifier cannot distinguish an internal sheet from the
exterior. Screen-space coverage tests use multiple rotations and canvas projections for immersed
Antiprism, Prism, Pyramid, and stellated catalogue presentations.

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
configured perpendicular depth in every incident face. This exact construction applies to ordinary
embedded edge and vertex fans. An immersed acute corner can require an unbounded or visually
destructive tangential miter; the tapered construction below preserves its source topology without
pretending that it is a single convex half-space corner.

Along an edge with normals `n1` and `n2`, the minimum-norm solution is

`d = (n1 + n2) / (1 + n1 · n2)`.

This is the intersection of the two equally offset face planes and lies on their dihedral bisector.
At a source vertex, all incident material-face normals participate. The solver finds the
minimum-length local displacement satisfying `n · d >= 1` for every incident normal by enumerating
the active one-, two-, and three-plane constraints. This gives the exact common corner for an
ordinary convex fan and a candidate inward point for an overdetermined fan. If the half-space
system has no common point, a regularized least-squares point is used. Duplicate normals are
removed. If an adjacent face has no material, it contributes no constraint.

Neither edge nor vertex directions query resolved face cells or the resolved three-dimensional
solid. This is essential for immersed polyhedra: internal source sheets use the same local joins as
externally visible sheets and do not change direction when another face orbit is hidden.

An immersed face cannot reuse the triangulation of the planar union for its underside. A union
triangle can join portions owned by different overlapping source-edge sheets; moving its
source-edge vertices to a shared miter can then fold that triangle across an opening. Scaling the
join separately in each incident face is also invalid: it gives the two faces different bottom
edges and makes an ordinary side face protrude from the seam.

WebGL therefore keeps two representations with distinct jobs. The visible top uses the resolved
planar union. The underside uses one uninterrupted sheet per authoritative source edge and two
locally derived bottom-corner roles:

1. The **full outline** intersects the two width-offset edge lines in the self-intersecting source
   face. Its tangential inset is capped by that face's arrangement-derived maximum.
2. The **standard outline** uses the shared all-face bisector join. Every ordinary hidden or shown
   face uses this outline, just as it does when no immersed face is present.
3. Exactly one explicit transition sheet closes every shared edge whose actual lower rails differ.
   Against a shown face, its rail follows every resolved source-edge vertex rather than
   approximating the potentially kinked inner boundary by one endpoint-to-endpoint segment.
4. The inner boundary of each bottom rim is a uniform `Rim` inset of its actual bottom outline,
   rather than an unrelated inset of the original top face.

At an immersed vertex not belonging to a self-intersecting face, such as the apex of a star
pyramid, every incident ordinary sheet reuses the all-face join. The standard outline consequently
keeps the configured perpendicular depth and stays watertight even when the dihedral angles are
more acute than in the corresponding non-star family member.

Opening walls connect the configured top inset to the independently constructed bottom inset and
may therefore be sloped or twisted. Their preferred winding and lighting normal point toward the
opening. Every selected triangle is emitted as its own surface with its own normal; no planar-quad
normal is assumed. Degenerate triangles are omitted and the diagonal with the stronger valid area
is selected. Overlapping source sheets represent the same material and are resolved by the depth
buffer; no union triangle can bridge unrelated openings.

For an ordinary embedded surface, the visible rim is widened when necessary to let a perpendicular
opening wall reach the shared inner bisector. The required in-face distance is the tangential part
of `Width · d`. If the polyhedron contains an immersed face, every visible top rim instead retains
the configured width. This keeps an ordinary triangular neighbor visually identical to the same
triangle in a non-star pyramid even when the immersed dihedral becomes extremely acute. The
full and standard bottom outlines, their transition sheet, and sloped opening walls absorb the
depth transition without changing that visible band.

## Consumer geometry

- **WebGL** triangulates the returned outer and hole cycles. Embedded presentations emit the top and
  shared inner surfaces plus walls around actual opening cycles. An immersed hidden face uses the
  continuous source-sheet underside, arrangement-limited bottom corners, and explicit full-to-standard
  transition sheets described above. The face pass temporarily disables physical back-face culling
  for the whole immersed presentation and flips back-fragment normals in the material shader; depth
  testing still selects the nearest sheet. It restores the incoming WebGL culling state before the
  edge and overlay passes. Opaque source-edge lines are composited afterward without depth writes
  and are culled using their adjacent face occurrence, matching triangle visibility.
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
| Very acute embedded dihedral | The immersed full outline is arrangement-limited; the ordinary neighboring outline keeps its bisector join, and an explicit transition wall closes their difference. |
| Simple face adjacent to an immersed face | Its visible band retains the configured rim and its bottom uses the ordinary full-depth join; a local transition wall absorbs the difference from the immersed outline. |
| Missing material on one adjacent face | The remaining face normal supplies the offset direction on that side. |
| More than three non-concurrent offset planes at one immersed vertex | The minimum feasible all-face join is selected from active one-, two-, and three-plane constraints; all ordinary incident sheets reuse the result. |
| Internal immersed structure | Source-face orientation and incident topology choose the side; resolved-solid containment never chooses or reverses it. |
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
