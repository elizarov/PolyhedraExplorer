# Self-intersecting polyhedra

This plan adds immersed polyhedra whose faces or face arrangements pass through themselves. The
initial scope is regular star faces that wind around the center more than once. Degenerate
polyhedra with coincident vertices and disconnected compounds remain invalid.

An immersed polyhedron and its resolved physical boundary are different objects. The immersed
polyhedron retains its abstract faces, edges, vertices, and winding. Resolution splits all
intersections and produces one connected, embedded, consistently oriented boundary suitable for
solid export.

## Nonzero-winding semantics

Face and solid resolution use the nonzero-winding rule. After an arrangement is split into cells,
a planar or volumetric cell contains material exactly when its total signed winding number is not
zero. Winding magnitude does not represent multiple layers of material: regions with winding 1,
-1, 2, or -2 are all filled. Resolution emits only interfaces between zero- and nonzero-winding
cells and orients the resulting boundary outward.

In particular, a `{5/2}` pentagram is fully filled. Its five arms have winding magnitude one and
its central pentagon has winding magnitude two, so both are material. The resolved outer boundary
is one ten-sided concave star polygon. The even-odd rule, which would leave the central pentagon
empty, is not used. A hidden face rim remains a separate presentation feature and follows the
original five-edge pentagram path.

## Current implementation baseline

The current implementation supports only connected, embedded surfaces:

- `Polyhedron` requires every abstract edge to have exactly two oppositely oriented face uses and
  every vertex link to be one manifold cycle.
- A face is one boundary cycle. Its shared ear-clipping implementation rejects a self-intersecting
  boundary before rendering, picking, animation, or export can use it.
- `Polyhedron` serialization currently transfers only original vertex coordinates, vertex kinds,
  face vertex IDs, and face kinds. It has no derived face geometry or provenance mapping.
- `validateProperGeometry` rejects intersections between triangles from different faces unless the
  intersection lies on their explicitly shared vertex or edge. Every completed primitive stage is
  checked with this validator.
- The four Kepler-Poinsot seeds are currently stored as resolved, embedded triangle surfaces. Their
  classical star faces exist only as temporary input to a private resolver.
- The existing family identifier stores only `(family, n)`, and the UI remembers only one `n`.
  There is no seed-settings popup or `q` value in the worker state.
- Greatened and Stellated return those resolved catalog surfaces. Dual recognizes them and
  substitutes the corresponding resolved catalog dual; the general polar-dual path does not model
  classical immersed duality. Animation is explicitly disabled for Greatened, Stellated, and Dual
  on a recognized resolved regular-star surface.
- STL export serializes the triangles produced by the WebGL face buffers. It rounds coordinates
  and skips degenerate triangles, but it does not resolve intersections or perform a solid Boolean
  repair.
- The existing STL test validator checks triangle degeneracy, unoriented edge incidence, nonzero
  signed-volume magnitude, and matching solid names. It does not detect triangle intersections or
  require outward orientation, so it is not a sufficient postcondition for the new export repair.
- The transform and issue contracts have no Resolve operation or self-intersection issue. These are
  serialized across the Wasm worker boundary and therefore require coordinated model, core, worker,
  and UI changes.

These constraints must be revised deliberately; merely accepting intersections in
`validateProperGeometry` is insufficient.

## Star families

Add a **Star families** category at the end of the seed popup, immediately before
**Kepler-Poinsot**. It contains four entries: **Star prism**, **Star antiprism**,
**Star pyramid**, and **Star bipyramid**. The menu shows only the family names.

Selecting a family initially creates its `n = 5`, `q = 2` member. The seed pill uses a compact name
such as **Prism 5/2**, where `q` is the star polygon's step, or winding, value. Up and Down change
`n` as they do for the existing families. A gear button opens a seed-settings popup with a control
for `q`, covering 2 through 10.

Within the application's existing `n <= 100` limit and the `q <= 10` control limit, a star-family
member is valid exactly when `gcd(n, q) = 1` and `2 <= q < n / 2`. The strict upper bound keeps only
one of the two reversed traversals `q` and `n - q`, so every geometric star polygon has one family
identifier. The `n` and `q` controls enumerate only valid pairs: changing `n` keeps `q` and skips
invalid values, while changing `q` keeps `n` and skips invalid values. All four star families share
the same remembered valid `(n, q)` pair. The initial pair is `(5, 2)`.

The URL tag is the long-term storage format and must encode the family, `n`, and `q` without
ambiguity. The UI, core parser, worker contract, saved configurations, seed recognition, and
family-value memory must use the same typed identifier.

Use two-letter uppercase family prefixes followed by canonical decimal `n`, an underscore, and
canonical decimal `q`:

| Star family | Tag pattern | Initial member |
|---|---|---|
| Star prism | `SP<n>_<q>` | `SP5_2` |
| Star antiprism | `SA<n>_<q>` | `SA5_2` |
| Star pyramid | `SY<n>_<q>` | `SY5_2` |
| Star bipyramid | `SB<n>_<q>` | `SB5_2` |

Both numbers are always present, including for the initial `(5, 2)` member. The parser rejects
leading signs, leading zeroes, missing components, trailing data, out-of-range values, and pairs
that fail the validity rule. These prefixes do not collide with the existing one-letter family
tags `P`, `A`, `Y`, and `B` or any catalog seed tag. A typed `StarFamilySeedId(family, n, q)` owns
formatting, parsing, and validation; consumers do not split these strings themselves.

## Classical Kepler-Poinsot seeds

The four Kepler-Poinsot seeds represent their classical immersed forms, including star faces and
self-intersecting vertex figures. Their abstract F/E/V counts and face orbits must not be replaced
by the extra cells introduced only for rendering or resolution. Classical dual pairs remain exact
duals in both directions.

Greatened and Stellated produce the corresponding classical immersed construction. Applying
Resolve produces the embedded physical boundary that the catalog currently stores.

The existing long-term seed tags remain unchanged:

| Seed | Existing and future tag |
|---|---|
| Stellated dodecahedron | `SD` |
| Great dodecahedron | `GD` |
| Great stellated dodecahedron | `GSD` |
| Great icosahedron | `GI` |

After this migration, loading any URL or saved configuration with one of these tags constructs the
new classical immersed source solid. It does not load the former resolved catalog mesh and does not
implicitly append Resolve. The pentagram indicator offers Resolve in the normal way; explicitly
adding `R` produces the embedded physical boundary. The documented Conway names such as `sD` and
`gD` are not seed-tag aliases and are not repurposed: for example, lowercase `sD` remains the Snub
dodecahedron catalog tag.

Preserve source face-orbit numbering where an orbit survives the migration, in particular the
single Kepler-Poinsot face orbit as `FaceKind(0)`, so an existing hidden-face setting for that orbit
continues to apply. A legacy orbit-targeted transform whose old resolved-only target has no source
orbit still parses, loads the immersed seed and preceding valid stages, and reports the normal
structured transform-applicability error. It is never silently retargeted to a geometrically
different orbit.

## New serialization tags

| Entity | Typed identity | Canonical serialized form | Non-default example |
|---|---|---|---|
| Star prism seed | `StarFamilySeedId(Prism, n, q)` | `SP<n>_<q>` | `SP7_2` |
| Star antiprism seed | `StarFamilySeedId(Antiprism, n, q)` | `SA<n>_<q>` | `SA7_3` |
| Star pyramid seed | `StarFamilySeedId(Pyramid, n, q)` | `SY<n>_<q>` | `SY5_2` |
| Star bipyramid seed | `StarFamilySeedId(Bipyramid, n, q)` | `SB<n>_<q>` | `SB9_4` |
| Resolve transform | `TransformOperation.Resolve` | `R` | - |
| Radial vertex transform | `TransformOperation.Radial` plus `VertexKind` | `r[<orbit>]` | `r[α]~R=1.25` |
| Stellate face macro | `TransformOperation.StellateFace` plus `FaceKind` | `f[<orbit>]` | `f[α]~R=1.25` |
| Radius tweak | `TransformTweak.Radius` | omitted when `1` | `~R=1.25` |
| Stellation result tweak | `TransformTweak.StellationResult` | omitted when `1` | `S~l=2`, `G~l=2` |

Add the three operation tags to `TransformOperation`, and add the Radius and StellationResult tags
to `TransformTweak`; those enum declarations remain the single source of truth for string encoding.
Uppercase `R` is unambiguous in the grammar: before an optional target and `~` it is the Resolve
operation, while after `~` and before `=` it is the Radius tweak key. Operation tags remain unique
among operations, tweak tags remain unique among tweaks, and seed tags are parsed only in the typed
seed position.

All new parsers accept only their canonical form, and `parse -> encode` must reproduce the same
string. Orbit characters use the existing `AnyKind` encoding and URL escaping. Non-default finite
Radius values use the existing canonical numeric formatter; applicability and the dynamic safe
range are validated by the core rather than encoded into the parser.

## Face polygon resolution and rendering

The geometry model keeps both representations:

- The **source face** is the authoritative immersed boundary. Its original vertices and directed
  edges define abstract topology, F/E/V counts, transforms, duality, orbit kinds, serialization
  identity, and hidden-face rim paths.
- The **resolved face geometry** is derived presentation data computed once by geometry code from
  that source face. It contains the nonzero-winding simple cells, their presentation vertices and
  triangles, and a complete mapping back to the source face. Rendering and tessellation consumers
  use this representation instead of rerunning polygon resolution.

Each immutable `Polyhedron` computes or receives one resolved-face record per source face. A record
identifies the source face and kind; records every resolved cell and its winding; maps each derived
vertex to an original vertex or to the source boundary segments and segment parameters that created
it; and maps every resolved boundary edge and triangle to its source cell and face. Internal
arrangement edges are marked separately from segments belonging to the original immersed path.
This provenance supports animation, picking, colors, hidden rims, debugging, and export without
promoting crossing points into abstract polyhedron vertices.

The Wasm geometry code computes and validates this derived representation. The core response
serializes it with the source mesh, and the browser consumes the supplied records directly; the
browser does not repeat the numerically sensitive polygon-resolution algorithm. A newly constructed
or transformed immutable polyhedron gets new resolved records, so there is no mutable cache to
invalidate. Simple faces also receive resolved records; their mapping is direct and their resolved
triangles preserve the existing triangulation.

Self-crossing source faces must be planar within a scale-aware tolerance. Existing non-planar faces
remain supported when their average-plane projection has a simple boundary. A face that is both
non-planar and self-crossing has no boundary-only fill semantics and is not resolved by projecting
it into its average plane: projected edges can cross at different 3D positions, so lifting the
intersection would invent geometry.

The core rejects such a result before it becomes the current displayed or exported polyhedron. It
reports a precise planarization requirement and offers Canonical when canonicalization can produce
planar faces for that topology. The UI continues to display and export the preceding valid stage,
so the STL action never receives undefined face geometry. A
future feature may support arbitrary non-planar immersed faces by adding an explicit source-surface
triangle patch or parameterization; that representation is outside this implementation's scope.

A separate, independently tested polygon-resolution algorithm converts one planar,
self-intersecting face boundary into renderable cells before triangulation:

1. project the boundary into a stable two-dimensional basis for its plane;
2. find and split all proper boundary-segment intersections;
3. build the resulting planar arrangement without adding its crossing points to the abstract
   polyhedron's F/E/V topology;
4. retain every bounded cell with nonzero winding;
5. triangulate the included simple cells with consistent orientation; and
6. return resolved cells, presentation vertices and triangles, and the complete source mapping.

This resolved face tessellation replaces the current assumption that every triangle index refers
only to an original face vertex. One shared result drives WebGL fill, canvas hit-testing, shadows,
print preview, animation buffers, OpenSCAD polygon preparation, and the first stage of STL
preparation. A simple face continues to use the existing behavior without changing its visible
geometry.

The Z-buffer can handle intersections between already-tessellated faces for display. Per-face
polygon resolution does **not** make the complete three-dimensional arrangement a watertight
solid; intersections between different faces are handled by Resolve or the export preparation
pipeline.

## Validation contracts

`Polyhedron` validation is layered. A single relaxed `validateProperGeometry` check is not
sufficient because rendering accepts intentional immersion while some geometry algorithms require
an embedded input. Each stronger polyhedron contract includes all guarantees of the preceding one:

- **Abstract surface:** one connected, consistently oriented combinatorial two-manifold with valid
  source faces and orbit metadata. Topological transforms, abstract Dual, F/E/V, and orbit analysis
  consume this contract.
- **Renderable immersion:** an abstract surface with valid resolved-face records and non-degenerate
  presentation triangles. Classified self-intersections are permitted. WebGL, picking, animation,
  self-intersection detection, and Resolve consume this contract.
- **Embedded boundary:** a renderable immersion with no contact between unrelated surface features.
  Geometry algorithms that explicitly require a physical boundary and Resolve output consume this
  contract. Canonicalization instead consumes a canonicalizable abstract topology.

Polyhedron validation returns structured analysis rather than only throwing an exception. It
reports the strongest satisfied polyhedron contract, resolved-face data, classified intersection
features, and precise errors. Algorithms declare the minimum contract they accept and reject an
insufficient input with a structured applicability issue before executing. Successful Resolve
produces a new polyhedron that satisfies the embedded-boundary contract.

A printable STL solid is deliberately **not** a contract or inferred property of `Polyhedron`.
Export is a separate conversion pipeline:

1. tessellate the polyhedron's supplied resolved faces together with visibility, rim, width, and
   expansion settings into an export triangle arrangement;
2. operate on that tessellation and its provenance to split intersections, select nonzero-winding
   material, join crossing rims, and repair the boundary;
3. quantize and validate the resulting dedicated STL triangle mesh; and
4. serialize only that validated mesh.

The final STL mesh must be one outward-oriented, positive-volume, quantized, watertight solid with
finite non-degenerate triangles, opposite uses of every edge, and no duplicate or intersecting
triangles. These are postconditions of tessellation-to-STL conversion, not guarantees attached to
the source polyhedron. Export repair neither mutates the polyhedron nor inserts Resolve into its
transform chain.

OpenSCAD export has a different contract. It emits polygonal construction source rather than a
prevalidated final triangle mesh. A closed presentation receives the final polygonal Resolve
boundary before tessellation when the core can prepare it without the complete STL arrangement.
Otherwise, and for a presentation with hidden or opened faces, it receives individually closed
face and rim pieces inside an OpenSCAD Boolean union, delegating final corefinement to OpenSCAD's
geometry engine.

The following are invalid at every layer:

- non-finite coordinates or distinct source vertices at coincident positions;
- repeated source-face vertices, zero-length source edges, or zero-area source triangles;
- anything other than two oppositely oriented face uses per abstract edge;
- a vertex link that is not one manifold cycle, or more than one connected source component;
- a non-planar self-intersecting source face;
- positive-length overlap between unrelated collinear source edges; and
- positive-area overlap or coincidence between unrelated source faces.

Derived arrangement vertices may coincide and are merged while retaining all provenance; they are
not coincident source vertices. Negative and multiply wound cells are also valid. A resolved-face
triangle is oriented consistently with its source face and signed cell winding; an unexplained
local inversion is a tessellation error, not a permitted negative-winding region.

The intersection analysis distinguishes two supported kinds of immersion:

- A **regular immersion** has transverse crossings within one planar source face or between
  unrelated face patches.
- A **singular immersion** has an isolated but deterministically resolvable contact, such as a
  tangency, an unrelated edge passing through a source vertex, or three or more patches meeting at
  one point.

Singular immersion remains renderable so a continuous Radial vertex range can pass through contact
events on its way to a star realization. The arrangement stores each contributing source feature
at the shared derived point. A persistent line or area overlap is invalid. Zero-measure winding
cells at a contact do not represent material. Signed-volume cancellation does not invalidate a
renderable immersed polyhedron; solid volume is established later by Resolve or STL conversion.

The current validators are split accordingly. Constructor-level incidence checks continue to
enforce the abstract surface. Face resolution and intersection classification establish a
renderable immersion. The existing unrelated-triangle intersection check becomes the embedded
boundary check and uses resolved-face triangles. Signed volume is not a renderable-immersion
requirement because positive and negative winding regions may cancel. Resolve validates its new
embedded polyhedron, while export separately validates the converted STL mesh after quantization.

## Resolve transformation

Add **Resolve** as the last transformation in the **Star** category. It converts an immersed
surface into one connected, non-self-intersecting physical boundary. The implementation is generic
and is not limited to the four Kepler-Poinsot catalog seeds.

Resolve is serialized as uppercase `R`. It has no chirality, orbit target, or tweak. The explicit
operation is retained in the transform list even when it is an identity on an embedded input; the
serializer never inserts it while loading an immersed seed.

Resolve uses the following pipeline:

1. Take the supplied resolved-face triangles and provenance as input. A simple non-planar source
   face contributes its existing deterministic triangles; a planar self-intersecting source face
   contributes its precomputed nonzero-winding cells.
2. Normalize the working coordinates by circumradius and use a spatial index to find candidate
   triangle pairs. Split only at actual triangle/triangle intersections; do not split every source
   face by every complete face plane.
3. Corefine all participating triangles together. Fast floating-point filters handle clear cases;
   uncertain orientation and intersection predicates fall back to exact arithmetic. Intersection
   points retain exact source-edge or source-triangle parameters until final coordinates are
   emitted.
4. Identify every derived vertex by canonical provenance: an original source vertex, a source edge
   plus its exact parameter, or the canonical set of source features meeting there. Coordinate
   proximity validates matching provenance but never decides topological identity.
5. At each corefined oriented fragment, evaluate generalized winding at safely offset points on
   both sides. Round values only when they are provably separated from a half-integer; verify an
   uncertain result with deterministic signed ray crossing. Keep the fragment exactly when one
   side has zero winding and the other has nonzero winding, and orient it away from the nonzero
   side.
6. Group every tangency, edge-through-vertex event, and multi-patch meeting into one singular
   arrangement node with all contributing provenance. If its selected zero/nonzero interface is
   non-manifold, apply deterministic tolerance-bounded local remeshing. Apply the identical rule
   and displacement to every contact in the same symmetry orbit, preserve all geometry outside the
   contact neighborhoods, and record the maximum displacement.
7. Build and validate the outward-oriented triangular embedded boundary first. Then merge adjacent
   coplanar triangles from one logical source region when their union is one safe simple polygon.
   Polygon merging is a deterministic topology-preserving second phase and cannot affect whether
   resolution succeeds.
8. Order all output vertices, faces, and kinds from canonical provenance rather than hash or
   candidate-pair iteration order. Uniform scaling and rotation preserve equivalent topology,
   Resolve on an embedded input is identity, and applying Resolve twice makes no further change.

The specialized Kepler-Poinsot face-plane splitter remains useful as a regression oracle while the
generic pipeline is developed, but it is not a second production Resolve implementation.

If exact nonzero-winding material has more than one disconnected positive-volume component,
Resolve is not applicable: it does not return a forbidden compound, discard smaller components, or
invent connecting material. The UI reports this controlled applicability result. The separate
STL conversion reports an exact-conversion error for the corresponding export and offers OpenSCAD
export as the native Boolean alternative.

For example, the top and bottom pentagrams of **Resolved Prism 5/2** become ten-sided concave star
polygons rather than overlapping triangles.

Resolve is an identity operation on an already embedded surface. In that case its pill shows the
existing recycle indicator, and clicking the indicator removes the operation, as it does for an
identity Canonical transformation.

## Resolved topology, provenance, and orbits

Resolved elements keep two independent classifications:

- **Provenance** records which source faces, edges, vertices, winding cells, and exact intersection
  parameters produced the element.
- **Output kind** identifies the element's actual proper-rotation orbit in the final resolved
  geometry.

Provenance answers where an element came from; output kind answers which final physical elements
are rotationally equivalent. Source kinds are not reused as output kinds because one source orbit
can split into several distinct resolved orbits, while an intersection element can have multiple
sources. Resolve therefore replaces the existing single-source face-kind mechanism with a
serializable many-to-many provenance model for its output vertices, edges, and faces.

The validated triangular embedded boundary is the authoritative intermediate topology. Polygon
merging then replaces a maximal connected set of adjacent triangles with one polygon exactly when:

- all triangles lie in the same plane under the filtered-exact predicate model;
- they come from the same logical source face;
- they bound the same zero/nonzero material interface;
- their union has one simple boundary and no holes; and
- the replacement preserves two opposite uses per edge and a manifold vertex link.

Different nonzero winding magnitudes do not prevent merging. The winding-one arms and winding-two
center of a filled `{5/2}` face therefore merge into one concave ten-sided polygon. Merging is
symmetry-equivariant: the algorithm decides and applies it to a complete symmetry orbit of regions,
not one triangle pair at a time in traversal order.

After Resolve, the displayed F/E/V counts describe this final embedded physical topology. They
include intersection-created vertices, split physical edges, and merged polygonal faces.
Presentation triangles inside one merged polygon do not count as independent faces or edges.

Each final element retains canonical provenance:

- a resolved vertex identifies an original source vertex, a source edge and exact parameter, or
  the canonical set of source features that meet there;
- a resolved edge identifies whether it descends from an immersed source-boundary segment or a
  surface-intersection segment and records all contributing source features; and
- a resolved face identifies its logical source face, included winding cells, and contributing
  corefined fragments.

Known source symmetry operations act on these provenance keys. Resolve propagates those exact
permutations to establish that the output preserves at least the source symmetry, then verifies
them against the final topology and coordinates. Geometric symmetry analysis runs afterward to
discover any stronger symmetry. A stronger operation is accepted when it preserves the final
geometry and incidence, even if it relates elements with different source provenance; provenance
must not prevent symmetry from becoming stronger.

Output kinds are the proper-rotation orbits of the final topology. Their deterministic order comes
from canonical provenance signatures, not hash iteration, generated IDs, model orientation, or
floating-point coordinate order. Orbit letters are therefore stable across uniform scaling,
rotation, repeated evaluation, and triangle-pair processing order.

After Resolve, face colors, F/E/V popup rows, visibility, rollover, and orbit-targeted operations
use these output kinds. If one source face orbit resolves into several physical rotation orbits,
they appear as separate rows and colors. Source provenance remains available for animation,
diagnostics, debugging, and tooltips but does not group unrelated output elements.

## Self-intersection detection

The core detects both self-crossing face boundaries and intersections between different face
surfaces. Detection distinguishes permitted crossings from degeneracy, coincident overlap,
non-manifold incidence, and disconnected components.

When a seed with no transform chain is immersed, its seed pill shows the pentagram indicator.
The indicator's tooltip and accessible label report both detected classes independently:
self-crossing source faces and intersections between different face surfaces. The pentagram means
that at least one of those classes is present; it is not restricted to star-shaped source faces.

Clicking the pentagram always appends a new Resolve transform. It never rewrites the seed or moves,
replaces, or removes an earlier Resolve. The seed-owned pentagram disappears immediately because
the state is no longer seed-only; successful Resolve also makes the displayed result embedded. If
Resolve is inapplicable, its own transform pill reports that structured issue while the seed pill
remains free of a transform-result indicator.

For a non-empty transform chain whose displayed result is immersed, the pentagram appears only on
the last transform pill. It is never placed on the seed or an earlier transform, even when that
earlier stage first introduced the intersections. The indicator has the same two-class report and
append behavior. Detection is cached with the evaluated geometry and does not rerun for camera
movement, rotation, display-only settings, or animation frames.

## Rendering hidden faces

When an immersed face orbit is hidden, its rim follows the original immersed boundary rather than
the outer boundary of its resolved fill. Hiding the top and bottom face orbit of **Prism 5/2**
therefore leaves a visible pentagram on each end.

Rim construction is a core geometry operation. For each hidden planar source face, the core reuses
the face's projected segment arrangement and nonzero-winding cell classification. For a configured
rim width `r`, every source-edge occurrence contributes a strip as follows:

- when the edge separates a zero-winding cell from a nonzero-winding cell, the complete width `r`
  lies on the nonzero side, preserving the existing inward-rim appearance at an exterior boundary;
- when both adjacent cells have nonzero winding, the strip extends `r / 2` on each side, so an
  internal immersed segment has the same total visible width; and
- an edge with zero winding on both sides contributes nothing and is reported as invalid overlap
  or cancellation when that situation contradicts the source-face contract.

The core unions all strips in the face plane. Crossings are planar unions with no over/under order,
height offset, duplicated coplanar surface, or internal seam. Ordinary source vertices use miter
joins up to four rim widths and bevel joins beyond that limit. Arrangement crossings are union
nodes, not joins. Rim topology may change as `r` increases; strips may meet and merge, but the
requested width is never locally reduced. For an immersed face, the maximum selectable rim is the
largest value below complete coverage of its nonzero-winding fill. Existing simple faces retain
their current inset limit and appearance.

The core returns polygonal **ResolvedRim** geometry, not triangles. It contains the face plane,
simple outer and hole cycles for every union region, its owning source face and orbit, and the set
of contributing source-edge occurrences for every derived boundary segment and region. A shared
polyhedron edge remains independently owned by each incident face while their rims are constructed
in their respective planes. Same-face crossing regions combine the provenance of all contributing
edges. Cross-face deduplication or union belongs to the later three-dimensional STL/OpenSCAD
construction, not to planar rim resolution.

Consumers decide whether tessellation is required:

- WebGL triangulates the ResolvedRim regions for front and back surfaces and constructs side walls
  from their boundary cycles. Front and back use the source face normal and its inverse; side-wall
  normals are calculated from the final expanded and thickened geometry. The union has no internal
  crossing walls or lighting seams.
- STL preparation triangulates the same polygonal regions after applying presentation settings,
  then includes them in its exact three-dimensional arrangement and final validation.
- OpenSCAD export writes the polygon cycles and holes directly as polygonal construction geometry;
  it does not triangulate planar rim caps in the application.

Rendering, hit testing, shadows, STL, and OpenSCAD therefore share one authoritative rim shape and
provenance while triangulation remains outside the core.

## Performance and limits

Face resolution, inter-face intersection detection, Resolve, and immersed-transform support use
the application's existing polyhedron complexity policy. The current authoritative maximum is
32,767 edges (`MAX_DISPLAY_EDGES`) in an accepted transform result. Do not add lower arrangement-
vertex, segment, cell, candidate-pair, or fragment limits to these polyhedron algorithms. Move the
constant into the shared polyhedron-complexity contract and give it a non-display-specific name
when these algorithms start using it directly.

Use spatial broad phases so the normal path does not perform all-pairs tests, cache resolved faces
and spatial indices with the immutable geometry stage, report monotonic stage progress from the
worker, and check cancellation between bounded batches. Benchmarks cover large `n`, large `q`, and
transformed immersed meshes up to the existing polyhedron limit. If those cases are not safe in a
supported browser, adjust the single application-wide polyhedron limit rather than creating
algorithm-specific geometry caps.

This policy does not govern STL's presentation-derived triangle arrangement. STL has separate,
higher limits because tessellation and printable face/rim construction can create substantially
more geometry than the source polyhedron.

## STL export

STL export uses exact arrangement-based conversion only. An explicit Resolve transformation is not
required before export. The action has two possible outcomes: one validated embedded, watertight
solid, or a structured export error. It never downloads an empty, partial, intersecting, open,
resource-truncated, or otherwise invalid STL.

Export preparation is presentation-aware; it does not simply apply Resolve to the complete
polyhedron and serialize that result. It constructs the requested printable geometry while it
still has access to the original immersed face paths:

1. visible self-intersecting faces contribute the included cells from the shared polygon resolver;
2. hidden faces contribute core-supplied ResolvedRim polygon regions that follow their original
   immersed boundaries; STL preparation triangulates those regions;
3. crossing rim segments are split and unioned into one printable region without replacing the
   immersed path with the outer boundary of the resolved face fill;
4. visibility, rim, width, and expansion geometry is generated before the final solid boundary is
   selected;
5. the three-dimensional resolver splits all remaining intersections, classifies solid cells,
   merges coincident vertices, removes duplicate and degenerate triangles, and orients the outer
   boundary; and
6. the final coordinate-quantized mesh is validated, because rounding can reintroduce degeneracy.

Consequently, exporting **Prism 5/2** with its top and bottom face orbit hidden produces a
watertight model with a clearly visible pentagram rim at both ends. Resolving the pentagram into a
filled ten-sided region before applying visibility would lose this required feature and is not an
acceptable export implementation.

The arrangement path preserves source edges and planar features. It may use exact predicates and
constructions to resolve floating-point ambiguity, but it must not substitute voxelization,
adaptive remeshing, a convex hull, component deletion, invented bridges, or another approximate
surface. The result must have one connected component, finite non-degenerate triangles, two
oppositely oriented uses of every edge, outward orientation, positive volume, no duplicate
triangles, no residual intersections, and deterministic output.

STL begins with a polyhedron accepted under the shared application limit, but its presentation
tessellation can be much larger than that source polyhedron because face thickness, rims,
visibility, expansion, intersection splitting, and solid repair create additional geometry. The STL
worker therefore has its own higher conversion ceilings:

- 250,000 presentation triangles entering the three-dimensional arrangement;
- 2,000,000 broad-phase candidate triangle pairs;
- 1,000,000 generated arrangement fragments;
- 500,000 triangles in the final quantized mesh;
- 256 MiB of exporter-accounted working memory; and
- 30 seconds of elapsed conversion time, excluding serialization and download.

These are STL-pipeline limits, not polyhedron limits. They are centralized exporter constants,
checked before allocation or expansion, and the worker remains cancellable throughout. Reaching a
ceiling is not malformed geometry and does not return the best result computed so far. Browser
benchmarks cover the ceilings independently of the source-polyhedron benchmarks.

An exact-topology failure, disconnected material, exhausted limit, or failed post-quantization
validation produces a structured error containing the failed stage, reason, active limit, and
observed count where applicable. The export popup explains that exact STL conversion could not be
completed safely in the browser and presents **Export OpenSCAD** as its primary recovery action.
That action preserves the current geometry, presentation settings, export scale, and generated file
name. It also explains that rendering the script in native OpenSCAD delegates the expensive Boolean
and STL generation work to its CGAL-backed pipeline.

## OpenSCAD export

OpenSCAD export deliberately does not mirror the STL pipeline. OpenSCAD receives polygonal
construction geometry and performs its own tessellation and, where required, Boolean merging. It
does not run the browser's complete three-dimensional STL arrangement and therefore remains
available after that arrangement reaches a resource limit.

A **closed-solid export** is used when every face orbit is visible and presentation settings do not
open seams between faces. When the core can resolve and safely merge the immersed input without
running the full STL arrangement, it sends that embedded polygonal boundary as one OpenSCAD
`polyhedron`. Its `faces` arrays contain merged simple polygon cycles rather than WebGL or STL
presentation triangles. If that preparation cannot establish a single embedded boundary, it falls
back to the piece-union form below instead of failing or invoking the STL converter. OpenSCAD owns
the final union and tessellation. This export preparation does not add a Resolve operation to the
displayed transform chain.

A **piece-union export** is used when any face orbit is hidden or another presentation option opens
the boundary, including face expansion. The application first constructs the requested basic
pieces while source-face semantics are still available:

- each visible resolved face region becomes an individually closed face piece with polygonal front
  and back caps and its side walls;
- each hidden face contributes individually closed rim pieces from its core-supplied ResolvedRim
  polygon cycles and holes, including the unioned crossing geometry of a pentagram rim; and
- width, expansion, visibility, colors where representable, source provenance, and deterministic
  naming are applied before the pieces are emitted.

Every emitted piece is a valid closed OpenSCAD solid expression; OpenSCAD is never asked to repair
an open sheet. ResolvedRim paths, including holes, remain polygonal and are emitted through
OpenSCAD's planar polygon/extrusion construction without application-side cap triangulation. A
simple non-planar cap uses its already defined deterministic triangles because one non-planar
OpenSCAD polygon would have ambiguous tessellation. The script wraps all pieces in one explicit
`union()` and delegates intersections, coincident internal walls, tessellation, and the final
single-solid merge to OpenSCAD's geometry engine. The application does not pre-union geometry from
different pieces.

For **Prism 5/2** with its top and bottom face orbit hidden, the side-face pieces and the extruded
pentagram-rim pieces are therefore exported separately under one `union()`. Rendering the script in
OpenSCAD produces the requested solid while retaining the visible pentagram construction at both
ends.

The two modes share source coordinates, scaling, visibility, rim, width, and expansion semantics
with the application, but they are not required to share STL's final triangles. OpenSCAD output is
tested both structurally and by rendering representative scripts with the OpenSCAD command-line
engine, then validating the resulting STL with the project's independent STL geometry validator.

## Radial vertex and Stellate face

Add the orbit-targeted primitive **Radial vertex**, displayed with its target as **Radial A**,
**Radial B**, and so on. It moves every vertex in one vertex orbit along the ray between the origin
and that vertex without changing topology. A value greater than the current radius moves the orbit
outward and forms spikes; a smaller positive value moves it inward and forms facets or dents. The
default value is the input position, so an unchanged Radial vertex is an identity operation.

Radial vertex uses operation tag `r` and an obligatory vertex-orbit target. Its Radius tweak uses
uppercase tweak tag `R`: the default is `r[α]`, and a non-default example is
`r[α]~R=1.25`. Radius `1` is omitted exactly as other default transform tweaks are omitted.

Offer Radial vertex only when all of the following are true for the target orbit:

- no target vertex is connected by an edge to another vertex of the same target kind;
- every face incident to a target vertex is triangular;
- no face incident to a target vertex has a self-intersecting source boundary;
- every target vertex and its radial direction are finite and nonzero; and
- moving the complete orbit preserves the abstract manifold incidence.

These restrictions ensure that every affected face remains planar and that independently moving
the entire orbit cannot directly collapse an edge whose two endpoints move together. The emitted
geometry may intentionally pass through itself, but it must still satisfy the immersed-surface
contract and must not contain coincident vertices, zero-length edges, or zero-area triangles.
Availability is computed from the authoritative source faces, not their resolved presentation
cells. Radial vertex is therefore never proposed for an orbit adjacent to a self-intersecting face:
moving only one of that face's boundary orbits could make the source face non-planar and violate the
planar self-crossing-face contract. The explicit rule remains in force even though the initial
all-triangular-neighbors requirement already excludes self-crossing polygon boundaries.

The settings popup exposes one **Radius** slider as a factor of the input radius, with `1` as the
omitted default. Its dynamic range starts above the first inward degeneracy and extends outward
through all valid intersection events. Self-intersection is not a range boundary. For eligible
regular constructions, the range must include the exact radius at which the regular dodecahedron
construction below becomes a great stellated dodecahedron. Golden geometry, rather than an
arbitrary UI maximum, determines that required upper endpoint.

Add the face-targeted macro **Stellate face**, displayed as **Stellate α**, **Stellate β**, and so
on. It expands to:

1. Kis the selected face orbit; then
2. apply Radial vertex to the apex orbit created by that Kis operation.

Stellate face uses operation tag `f`, an obligatory face-orbit target, and the same Radius tweak.
The default form is `f[α]`; a non-default example is `f[α]~R=1.25`. This one macro tag is stored in
the transform chain rather than its generated Kis/Radial expansion. Its source-face target remains
stable even if the temporary apex orbit receives a different display letter after another stage.

The macro stores the source face kind and Radius setting, not the temporary letter assigned to the
derived apex orbit. Kis returns an explicit source-face-to-apex-kind mapping, and macro expansion
uses that mapping to select the Radial vertex target deterministically. The macro is offered only
when the Kis result's apex orbit satisfies the Radial vertex eligibility rules. It is placed in the
**Orbit-targeted** menu with the other targeted operations because its availability and identity
depend on the selected face orbit.

The global targeted-operation order becomes **Drop face**, **Drop edge**, **Drop vertex**,
**Kis face**, **Stellate face**, **Truncate vertex**, **Rectify vertex**, and **Radial vertex**.
Stellate face is also offered on eligible face-orbit rows, and Radial vertex on eligible
vertex-orbit rows, using the same order, availability calculation, tooltips, and remembered target
behavior as the transform menu.

At Radius `1`, Stellate face has the ordinary Kis geometry. Increasing Radius extends its triangular
face pyramids through self-intersecting star realizations; decreasing it moves them toward or
through the source face planes to form inward facets. The range and regular-value calculation must
make this tested construction possible:

```text
Dodecahedron -> Stellate face α -> Great stellated dodecahedron
```

The exact regular Radius is offered as a snap point and seed-replacement suggestion. The operation
retains the abstract Kis triangulation while its resolved-face data supplies nonzero-winding fill
for rendering and export. A Radial vertex setting change has identical topology and interpolates
its source vertices directly. Stellate face uses Kis's source-face-to-apex provenance for a stable
combined animation; no frame recomputes face resolution in the browser.

Radial vertex and Stellate face also provide the primary structured generator for non-convex test
geometry. A test utility applies them to every eligible orbit of representative seeds and
transformed polyhedra, then samples critical Radius values and deterministic points between them.
Moving inward generates concave dents and locally reflex neighborhoods; moving outward progresses
from convex spikes through face contacts and immersed star arrangements. Every generated case keeps
its seed, transform chain, target orbit, Radius, and source mapping, so a failure is reproducible
and can be minimized without starting from arbitrary triangle soup.

The generator classifies, rather than assumes, each sample as embedded convex, embedded non-convex,
immersed, or degenerate. It is reused by face resolution, intersection detection, Resolve,
transform-support, animation, rendering, and STL stress campaigns. Degenerate critical values test
controlled rejection; values immediately on both sides test scale-aware tolerances. Only minimized
cases that reveal distinct bugs become permanent regression fixtures.

## Generic stellation and greatening

Replace the catalog substitution in Greatened and Stellated with one shared geometry engine based
on the input's facial-plane **constellation**. Extending all facial planes partitions each plane
into facets and three-dimensional space into cells; symmetry groups facets and cells into orbits.
Christopher Henrich's [stellation construction and computer
implementation](https://scispace.com/pdf/stellation-of-polyhedra-and-computer-implementation-2u5evsq1tw.pdf)
defines this representation, the cell-orbit graph, and connected-cell criteria. In its dodecahedron
case the four compact results are, in order, the dodecahedron, Stellated dodecahedron, Great
dodecahedron, and Great stellated dodecahedron, so the expected catalog results arise from geometry
rather than stored templates.

Do not enumerate every valid subset of cell orbits. Some highly symmetric inputs have hundreds of
millions of stellations. Generate only **main-line extensions**: for each successive cell stratum,
take all compact cell orbits from the central core through that stratum, then retain the result only
when the selected cell set and its complement are connected, the boundary is finite, and the result
satisfies the renderable-immersion and application-size contracts. Main-line selections are nested,
give a deterministic meaning to "next", preserve the input's complete point group, exclude chiral
branch choices and compounds, and provide at most one raw candidate per stratum.

The shared engine performs these steps:

1. Deduplicate and orient the authoritative planar source-face planes. Reject a non-planar source
   face, coincident incompatible planes, a plane through the symmetry center, or a plane set not
   preserved by the input point group.
2. Compute pairwise plane-intersection lines, triple-plane vertices, bounded plane facets, bounded
   cells, cell adjacency, cell power, and exact symmetry-orbit mappings using scale-aware predicates.
3. Classify the input's nonzero-winding material in the cell graph. A normal embedded input starts
   at the central compact stratum. A previous generic star result is recognized by its geometric
   plane/cell signature, not by seed or transform history.
4. Walk outward through the remaining compact strata and construct each main-line boundary. Group
   coplanar boundary facets by source plane and reconstruct one authoritative oriented source-face
   cycle per input face. Resolved facets remain derived presentation data; they do not replace the
   classical source cycles or their abstract incidence.
5. Reject a candidate with disconnected material, a compound, multiple disconnected face pieces
   on one required source face, forbidden line or area overlap, invalid manifold incidence, or an
   output beyond the shared polyhedron limit.
6. Classify and rank the surviving candidate separately for Stellated and Greatened, then validate
   the completed source polyhedron independently. Seed recognition runs only after this result has
   been built and may offer a catalog replacement; it never chooses the geometry.

The two transforms are different filters over that common ordered constellation:

- **Stellated** requires every new source edge line to be a continuation of the corresponding
  input face-edge line orbit. A face cycle may change its winding step, as a pentagon becomes a
  pentagram, but it retains its face-plane ownership and edge-line provenance.
- **Greatened** retains every face-plane orbit and the face's cyclic type: side count, winding step,
  and cyclic edge-kind word. It selects a strictly larger face circuit from farther intersections
  with other facial planes; its edge-line adjacency may change.

These rules are the project's deterministic Conway-style classification of generic constellation
results. A candidate can qualify as Stellated relative to one input and Greatened relative to a
different input. This is how the final regular star form is reached by either route without a
catalog special case:

```text
Dodecahedron --Stellated--> Stellated dodecahedron
Dodecahedron --Greatened--> Great dodecahedron
Great dodecahedron --Stellated--> Great stellated dodecahedron
Stellated dodecahedron --Greatened--> Great stellated dodecahedron
Icosahedron --Greatened--> Great icosahedron
```

For each operation, order qualifying strict extensions by increasing cell stratum. The default is
**Result 1**, the first qualifying extension, and its setting is omitted. When more than one result
exists, the last transform pill has its normal gear button; the settings popup contains a discrete
**Result** slider from `1` through the number of qualifying candidates, displays `n of N` plus the
candidate's F/E/V counts, and has the standard Reset action. A non-default selection is serialized
with the integer-only `l` tweak, for example `S~l=2` or `G~l=2`. Changing Result is immediate because
different candidates need not have compatible topology.

Applying the operation again performs the same search relative to its current geometric cell
signature and therefore chooses the next qualifying outward extension when one exists. Repetition
does not define one global order that mixes Stellated and Greatened: each operation keeps its own
geometric filter. This preserves their distinct meanings while supporting the natural "more
stellated" exploration workflow. If no qualifying extension exists, that operation is not offered;
an out-of-range serialized Result produces a structured applicability error rather than selecting
a different candidate.

Cache the plane constellation by normalized plane-and-symmetry signature. Candidate selection,
Result changes, and repeated operations reuse its facets, cells, powers, and orbit graph. The worker
reports separate constellation, candidate-search, source-face-reconstruction, and validation
progress stages.

## Transform support

An intentional self-intersection is not, by itself, invalid geometry. `Renderable immersion` is
the normal transform input and output contract. Applicability is determined by the construction's
actual needs: a supporting plane, a valid polar center, a locally simple target patch, or a
canonicalizable topology. A crossing elsewhere on the polyhedron never disables a local operation.

Transform applicability is structured rather than a Boolean. It records the minimum input
contract, face requirements, topology requirements, locality restrictions, output contract, and a
precise rejection reason. A successful transform is validated against its declared output
contract. Continuous safe ranges use the same contract, so an intentional transverse crossing does
not incorrectly terminate a slider range.

All transforms consume the authoritative immersed source faces. Resolved cells and presentation
triangles are derived geometry and are never treated as transform input. This preserves the
classical F/E/V incidence and prevents intersection-created cells from changing Dual or another
Conway operation.

In the following matrix, **conditional** means that an immersed input is supported but the
constructed result must still satisfy the renderable-immersion contract. **Local** means that only
the affected faces or vertex star have the stated restriction; unrelated crossings remain valid.

| Operation | Inter-face crossings | Self-crossing source face | Simple non-planar face | Contract and restriction |
| --- | --- | --- | --- | --- |
| None | Yes | Yes | Yes | Preserves the input contract. |
| Truncated | Yes | Yes | Conditional | General oriented-map construction; output must remain renderable. |
| Rectified | Yes | Yes | Conditional | General oriented-map construction and the exact full-depth truncation quotient. |
| Truncate vertex / Rectify vertex | Yes | Yes | Conditional | Same construction restricted to selected vertex orbits. |
| Dual | Yes | Yes | No | Requires a non-singular oriented plane for every authoritative face; produces planar source faces. |
| Cantellated | Yes | Yes | No | Uses the shared primal/dual corner construction; produces planar face-, vertex-, and edge-derived faces. |
| Bevelled | Yes | Conditional | No | Supported when the fused construction produces only planar or simple non-planar faces. |
| Snub | Yes | Conditional | No | Requires real face planes and a valid metric parameter solution; output is checked dynamically. |
| Chamfered | Yes | Local: no | No | Every affected face must be simple and planar because a crossed boundary has no unique inward offset. |
| Propeller / Whirl / Quinto | Yes | Yes | Yes | Operate on incidence, then require a canonicalizable spherical topology and produce a canonical embedding. |
| Canonical | Yes | Yes | Yes | Requires a spherical, convex-polyhedral abstract topology; input coordinates need not be embedded. |
| Greatened / Stellated | Conditional | Conditional | No | Require a finite symmetry-preserving facial-plane constellation and a qualifying main-line face circuit; produce a renderable immersion without catalog substitution. |
| Drop | Yes | Local: no | Conditional | The affected patch must be a simple disk whose merged boundary has an unambiguous face surface. |
| Kis / Kis face | Yes | Local: no | No | Every target face must be simple and planar; coning a crossed boundary creates forbidden line intersections. |
| Radial vertex | Yes | Local: no | Triangles only | Uses the eligibility rules below; unrelated immersed regions are allowed. |
| Stellate face | Yes | Local: no | No | Inherits the target-face Kis restriction and the created-apex Radial restriction. |
| Resolve | Yes | Yes | Simple non-planar only | Converts a renderable immersion to an embedded boundary. |

Non-planarity and self-crossing are separate properties. A simple non-planar face continues to use
its deterministic triangle surface. A face that is both non-planar and self-crossing is invalid and
cannot become valid merely because a transform otherwise works on abstract incidence.

### Truncation and rectification

Truncated and Rectified share one directed-edge cut construction. For each undirected source edge
`e = (v, w)`, choose one reversal-invariant rectification point `m(e)`. Use the common midsphere
tangency point when the complete polyhedron has such points on its edge segments; otherwise use the
Euclidean midpoint. For the directed edge leaving `v`, the cut point at depth `u` is:

`c(v, e, u) = (1 - u) v + u m(e)`.

At `u = 0`, all created points collapse to their source vertices. For `0 < u < 1`, the two
directed uses of an edge remain distinct and define truncation. At `u = 1`, they coincide at
`m(e)` and are identified, giving one Rectified vertex per abstract edge. Original-face boundaries
follow source-face order, while new vertex faces follow the existing cyclic order around each
source vertex. This definition is independent of unrelated geometric intersections.

The output is rejected only for a real renderable-immersion violation: degenerate source geometry,
a forbidden line or area overlap, invalid manifold incidence, or a derived face that is both
non-planar and self-crossing. Transverse intersections remain valid. Animation approaches the
topology-changing endpoints with a small gap and swaps to the exact input or Rectified mesh.

The regular default is a metric layer above this topology. A regular star face `{n/q}` uses its
actual step angle, `PI * q / n`, rather than treating it as an ordinary `n`-gon. If no consistent
regular metric target exists across the relevant orbits, the operation can remain applicable while
the UI omits a regular snap value and offers only a validated geometric range.

### Dual realization

Dual always means the abstract oriented-map dual: source faces become dual vertices, source
vertices become dual faces, and source edges retain their one-to-one correspondence. It operates
on authoritative faces, including planar self-crossing faces, rather than on their resolved cells.

Let `c` be the symmetry-preserving duality center and let an oriented unit-normal face plane be
`n(f) * (x - c) = h(f)`. For a fixed positive reciprocal radius `r`, the dual vertex is:

`p(f) = c + (r * r / h(f)) n(f)`.

This construction requires a non-degenerate face normal and `abs(h(f))` above a scale-relative
tolerance. It never substitutes an average plane for a genuinely non-planar face. The chosen
normalized core frame and reciprocal radius are stable across consecutive Dual stages so that
double Dual restores the original coordinates before final display scaling.

Every resulting dual face is planar: if source vertex `v` belongs to source face `f`, then
`(v - c) * (p(f) - c) = r * r`; consequently, all dual vertices surrounding `v` lie in one plane.
The dual boundary may self-cross and is resolved with the ordinary nonzero-winding face algorithm.
If a face plane passes through the only symmetry-preserving duality center, direct Dual is
inapplicable with a precise polar-singularity issue. It does not silently canonicalize the input;
Canonical followed by Dual is an explicit alternative when the abstract topology supports it.

### Dual animation and the rectification relation

The general Dual animation uses a fixed corner, or cantellation, topology rather than assuming that
the primal and dual have the same geometric rectification. For every incident source pair `(v, f)`,
create the animated corner point:

`q(v, f, t) = (1 - t) v + t p(f)`.

The fixed intermediate surface contains one face for every source face, one face for every source
vertex, and one quadrilateral for every source edge. For `0 < t < 1`:

- each source-face cell is a homothetic copy of its authoritative face and therefore keeps the same
  crossing arrangement and resolved provenance;
- each source-vertex cell is a homothetic copy of its final dual face;
- each source-edge cell is a parallelogram and therefore planar; and
- the resolved presentation topology is constant throughout the open interval.

Only endpoint cells collapse. The animation therefore uses one stable resolved triangle and
provenance layout between small endpoint gaps, then swaps to the exact source or dual mesh. No
browser frame recomputes polygon resolution, and there is no tessellation-topology pop inside the
animation interval.

Abstractly, `Rectified(P)` and `Rectified(Dual(P))` are the same map. Their coordinates coincide
only when the primal and polar dual are in verified shared midsphere position and corresponding
edges contain the same tangency point. In that special case, animation may use the visually useful
two-half path `P -> Rectified(P) -> Dual(P)`. The corner construction is the general path; an
unverified shared Rectified midpoint is never forced onto arbitrary reciprocal geometry.

Truncated and Rectified animation of a self-crossing face may have additional parameter values at
which its planar arrangement changes. The core detects those events and returns adaptive,
topology-compatible keyframe intervals with zero-area birth or death triangles at event boundaries.
Operation support does not depend on animation support: until such an interval has been produced,
an otherwise valid exceptional transform is applied immediately rather than rejected.

### Macro realization

Macro expansion specifies abstract Conway algebra. Coordinate realization is a separate layer and
may use a fused kernel, as long as it produces the same oriented map and the documented regular
geometry. A fused macro validates the contract of geometry it actually constructs; an unrendered
formal intermediate need not satisfy the embedded-boundary contract. It may not hide an undefined
stage by silently canonicalizing it.

| Macro | Immersed-input rule |
| --- | --- |
| Kis | Requires simple planar source faces and uses a direct apex realization of its `Dual -> Truncated -> Dual` map. |
| Join | Requires planar source faces and either a fused realization or proof that the Rectified intermediate supplies valid planes for final Dual. |
| Needle | Conditional: the Truncated result must have valid planes for final Dual unless a fused realization supplies them directly. |
| Zip | Supports planar-faced immersions: Dual produces planar faces and final Truncated produces a checked renderable immersion. |
| Cantellated | Supports planar-faced immersions directly through the shared corner kernel. |
| Bevelled | Supports planar-faced immersions when its fused result passes renderable-immersion validation. |
| Ortho | Uses a fused `Dual -> Cantellated -> Dual` realization and supports planar-faced immersions. |
| Meta | Conditional: its fused Bevelled geometry must supply valid planes for final Dual. |
| Gyro | Conditional: its chiral Snub geometry must supply valid planes for final Dual. |

Macro animation uses the final fused topology when available. Otherwise it is composed only from
primitive animation paths whose declared contracts hold over their complete parameter intervals.

### Scaling, symmetry, and recognition

Display scaling is also contract-aware. Circumradius is available for every non-degenerate
renderable immersion. Midradius is the existing symmetry-invariant aggregate of closest points on
authoritative edges and is available only when its denominator is finite and above tolerance.
Inradius means the minimum unsigned distance from the symmetry-preserving center to authoritative
face planes; it is available only when every source face has a non-singular oriented plane and no
such plane passes through the center. It is therefore meaningful for regular star faces but not for
a genuinely non-planar face. Scaling never invents an average plane. An unavailable serialized
scale returns a structured scale-applicability issue and offers Circumradius rather than producing
infinite, negative, or reflected coordinates.

Symmetry analysis consumes authoritative source topology and coordinates and accepts a renderable
immersion. A candidate rotation must preserve source incidence, face winding, and resolved
provenance; geometric crossings do not weaken the point group. Resolve separately computes kinds
for its physical output as specified above.

Seed recognition likewise compares authoritative source geometry, not resolved intersection
cells. It remains scale- and rotation-independent, distinguishes chirality where required, and can
recognize classical star seeds and transform results directly from their immersed representation.
Recognition failure never changes transform applicability or inserts Resolve.

## Implementation sequence

Implement the following steps in order. After each step, run its focused tests and the root
`./gradlew test` gate; do not begin the next step until both pass. A failure found during later
integration first becomes a minimized regression test in the owning focused suite. Steps that
change the worker or browser also build `browserProductionDistribution` and run the production
Wasm/browser acceptance test used by the release workflow.

1. **Freeze the baseline.** Add golden fixtures for current URLs, saved states, Kepler-Poinsot
   geometry, representative transforms, hidden-face rims, and exports. Gate on unchanged current
   behavior and a green root test.
2. **Establish typed contracts.** Add the new seed, operation, tweak, validation, issue,
   provenance, and worker-transport types, plus the shared polyhedron complexity limit, without
   changing geometry. Gate on exhaustive tag uniqueness, canonical round trips, malformed-input
   rejection, and legacy golden states.
3. **Build the planar arrangement kernel.** Implement normalized filtered predicates, exact
   fallback, segment splitting, nonzero-winding cell selection, canonical ordering, and complete
   source provenance as a standalone geometry component. Gate on the polygon-resolution suite and
   a reproducible randomized JVM run.
4. **Introduce resolved faces and layered validation.** Make every immutable polyhedron carry
   resolved-face records; add the abstract-surface, renderable-immersion, and embedded-boundary
   checks plus classified self-intersection analysis. Gate on the face-provenance, contract,
   planarity, degeneracy, and intersection-detection suites.
5. **Move presentation consumers to resolved geometry.** Serialize the records through the Wasm
   worker and make WebGL triangulation, picking, shadows, preview, and animation buffers consume
   them without resolving polygons in the browser. Gate on worker round trips, unchanged simple
   and non-planar-face rendering, immersed-face rendering, and production browser acceptance.
6. **Add immersed seeds.** Implement star-family `(n, q)` generation and migrate the four
   Kepler-Poinsot seeds to their classical source topology, then connect parsing, recognition,
   settings, family memory, and legacy loading. Gate on family enumeration, F/E/V, dual pairs,
   symmetry/orbits, legacy compatibility, and seed UI tests.
7. **Implement Resolve and resolved topology.** Build the generic three-dimensional arrangement,
   nonzero-winding boundary selection, singular-contact handling, safe polygon merging,
   many-to-many provenance, and final rotation orbits. Keep the specialized catalog resolver only
   as a test oracle. Gate on the complete Resolve and resolved-topology suites, including identity,
   idempotence, classical seeds, non-catalog inputs, determinism, and controlled inapplicability.
8. **Expose intersection status and Resolve UX.** Cache detection with evaluated geometry, map its
   issue and progress to the correct pill, and implement the pentagram action and Resolve identity
   removal. Gate on seed-only and transformed ownership, both intersection classes, worker
   cancellation/progress, keyboard behavior, and saved-state tests.
9. **Implement polygonal hidden-face rims.** Produce `ResolvedRim` in the core and move rendering,
   picking, and shadow consumers to its triangulated regions. Gate on the focused rim suite and the
   hidden-top-and-bottom Prism 5/2 rendering regression.
10. **Generalize the transform foundation.** Make Truncated, Rectified, Dual, Cantellated, and
    their dependent macros honor the declared immersed-input contracts and share the corner and
    quotient constructions. Gate every operation over the transform-domain matrix before enabling
    it in the UI.
11. **Add radial constructions.** Implement orbit-targeted Radial vertex, its safe range, and the
    Stellate face expansion and controls. Gate eligibility, interpolation, degeneracy boundaries,
    serialization, all four generated geometry classes, and the dodecahedron identity before
    exposing the operations.
12. **Replace catalog stellation paths.** Implement the reusable face-plane constellation and
    main-line selection engine, Result control, repetition, caching, and generic Greatened and
    Stellated transforms. Gate the focused construction suite, all catalog identities without
    recognition shortcuts, representative non-catalog cases, and UI result selection.
13. **Finish dependent behavior.** Add immersed Dual and transform animation paths, fused macro
    animation, contract-aware scaling, symmetry, orbit targeting, and recognition. Gate endpoint
    equivalence, safe intermediate frames, fallbacks at topology events, and the focused scaling,
    symmetry, recognition, and animation suites.
14. **Implement OpenSCAD export.** Add closed-boundary export and piecewise face/rim union export
    directly from polygonal core geometry. Gate structural output first, then OpenSCAD CLI
    acceptance and independent validation of its resulting meshes.
15. **Implement exact STL conversion.** Build the separate tessellation-to-solid arrangement,
    quantization, final validator, resource guards, structured errors, and OpenSCAD recovery path.
    Gate every stage independently, then all presentation variants and the Prism 5/2 pentagram-rim
    regression; never serialize a mesh that fails the final postconditions.
16. **Harden and release-gate the feature.** Optimize only after correctness profiles identify
    bottlenecks; add spatial caches, bounded cancellation, progress, and limit tests. Run the full
    root suite, production Wasm/browser acceptance, benchmarks up to the shared polyhedron limit,
    OpenSCAD acceptance, and the reproducible 10,000-case JVM export campaign. Minimize and commit
    every distinct failure, rerun all gates, and update the live documentation before release.

## Test coverage

Every new geometry algorithm has a focused unit-test suite independent of browser integration and
end-to-end export tests. Algorithm tests run on the JVM for fast feedback and cover deterministic
output, reversed orientation, rotations, scale changes, tolerance boundaries, and controlled
rejection of degenerate input.

At minimum, add these suites:

- **Polygon resolution:** simple convex and concave controls; `{5/2}` and higher-winding star
  polygons; multiple crossings; reversed paths; touching endpoints; collinear overlap; stable cell
  classification; non-overlapping output triangles; and agreement between resolved area and the
  nonzero-winding rule. A `{5/2}` regression test verifies that both the five arms and the
  twice-wound central pentagon are filled. Provenance tests verify the source-face, source-segment,
  cell, edge, and triangle mappings and their round trip through core-response serialization.
  Planarity tests accept tolerance-bound perturbations, reject genuinely non-planar self-crossing
  boundaries, and verify that average-plane projection is never used to invent crossing vertices.
- **Self-intersection detection:** intra-face and inter-face crossings; adjacent faces meeting only
  at their shared feature; tangencies and coincident regions; compounds and repeated vertices;
  already resolved surfaces; and scale-invariant classification.
- **Layered polyhedron validation:** focused tests for every polyhedron-contract transition and
  error classification; regular and singular immersion; rejected line and area overlap; coincident
  source versus merged derived vertices; negative and multiple winding; zero-measure contact cells;
  strongest-contract reporting; and enforcement of every geometry algorithm's declared input and
  output contracts. Separate tests start with export tessellations and verify the independent
  conversion and validation postconditions of the resulting STL triangle mesh.
- **Resolve:** every classical Kepler-Poinsot seed; representative members of all four star
  families; a non-catalog immersed fixture; identity and idempotence; classical dual pairs;
  connectedness, orientation, manifold incidence, absence of residual intersections, and stable
  face-orbit metadata. Tests independently cover triangle corefinement, exact-predicate fallback,
  provenance-based vertex identity, generalized-winding classification and ray verification,
  symmetric singular-contact regularization, canonical output ordering, scale and rotation
  invariance, safe polygon merging, and controlled rejection of disconnected material. The
  generic results for all four Kepler-Poinsot seeds are compared with the specialized resolver
  during migration.
- **Resolved topology and orbits:** maximal safe polygon merging, preservation of a simple boundary
  without holes, `{5/2}` ten-sided face merging across different nonzero winding magnitudes,
  symmetry-equivariant merging, final physical F/E/V counts, many-to-many provenance serialization,
  exact propagation of source symmetry, discovery of stronger symmetry, actual output rotation
  orbits, deterministic kind ordering, color/row separation for split source orbits, and stable
  kinds across scale, rotation, reevaluation, and shuffled corefinement order.
- **Transform domains and shared constructions:** table-driven tests exercise every operation on an
  embedded control, a planar self-crossing face, an unrelated inter-face crossing, a simple
  non-planar face, a singular contact, and an input just outside each declared domain. Truncated and
  Rectified tests prove directed-edge reversal invariance, `u = 1` quotient equivalence, the
  `{n/q}` regular-angle calculation, orbit-targeted equivalence, and controlled rejection of
  non-planar crossed output faces. Dual tests prove abstract incidence reversal, classical star
  pairs, planar dual faces, double-Dual coordinate recovery before display scaling, rotation and
  scale invariance, polar-singularity rejection, and the absence of a hidden Canonical fallback.
  Cantellated tests prove the face, vertex, and parallelogram planarity identities of the shared
  corner kernel. Macro tests distinguish abstract expansion from fused realization and verify the
  input and output contract of every named macro.
- **Scaling, symmetry, and recognition:** tests cover Circumradius, valid and singular Midradius,
  planar-star Inradius, rejected non-planar Inradius, and structured fallback without reflected or
  non-finite coordinates. Symmetry and seed-recognition cases compare authoritative immersed
  inputs before and after rotation, reflection, scaling, face resolution, and Resolve, verifying
  that derived intersection cells neither weaken source symmetry nor change source recognition.
- **Immersed transform animation:** Dual animation tests cover embedded and immersed inputs at
  several interior fractions, proving fixed F/E/V presentation topology, stable provenance,
  homothetic resolved face cells, planar edge parallelograms, compatible buffers, and exact endpoint
  swaps. Separate tests exercise the shared-Rectified fast path only when primal and dual tangency
  points coincide. Truncated and Rectified tests cover arrangement-event detection, adaptive
  interval refinement, zero-area triangle birth and death, bounded interpolation error, and the
  immediate-operation fallback when no safe animation correspondence is available.
- **Resolved hidden-face rims:** focused core tests cover equivalence with the existing simple-face
  inset, concave and negative-winding faces, multiple winding, the internal half-width and exterior
  full-width rules, ordinary miter and bounded bevel joins, two-segment and multi-segment crossings,
  topology changes as width grows, uniform visible width, maximum width, shared-edge and crossing
  provenance, deterministic outer/hole cycles, and **Prism 5/2** pentagrams. The core result is
  asserted to contain no tessellation. Rendering tests triangulate its regions independently and
  verify complete area coverage, finite front/back/side normals, no internal crossing walls, hit
  testing, and shadow geometry.
- **STL geometry preparation:** tests of the preparation algorithm before text serialization;
  watertight oriented-edge incidence, finite non-degenerate triangles, outward orientation,
  positive volume, no duplicate or intersecting triangles, coordinate quantization, deterministic
  output, exact source-feature preservation, and visible/hidden/rimmed export variants. Dedicated
  tests exercise accepted polyhedra near the shared edge limit, force both exporter resource guards
  and every exact-topology error, verify the structured stage and observed usage, and prove that no
  partial STL is serialized. A dedicated **Prism
  5/2** test hides the top and bottom faces and verifies, by slicing both ends of the output, that
  the exported solid retains the expected printable pentagram rims.
- **OpenSCAD export:** structural tests prove that closed-solid mode emits one pre-tessellation
  resolved `polyhedron` with merged polygon cycles when available, while its fallback, hidden-face,
  and expanded modes emit only individually closed, deterministically named face and rim pieces
  under one `union()`. ResolvedRim caps are emitted as polygon paths with holes and are not
  application-triangulated. Tests force an STL resource-limit error and verify that the offered
  OpenSCAD export is still generated from the same saved settings. Acceptance
  tests render representative scripts with OpenSCAD and pass the resulting mesh through the
  independent STL validator. They include a closed immersed star solid, a simple non-planar face,
  intersecting expanded pieces, and **Prism 5/2** with hidden top and bottom pentagram rims.
- **Star families and transforms:** identifier and URL round trips, valid `(n, q)` enumeration,
  expected F/E/V counts, symmetry/orbit classification, seed recognition, Greatened, Stellated,
  Dual, Radial vertex, Stellate face, and explicit rejection by transforms that do not support
  immersed input. Radial vertex tests cover every eligibility condition, inward and outward range
  boundaries, identity, direct interpolation, controlled rejection of degeneracy, and suppression
  for every orbit adjacent to a self-intersecting source face. Stellate face tests verify its
  source-face-to-apex mapping, expansion equivalence, serialization, orbit availability, and the
  regular Dodecahedron-to-Great-stellated-dodecahedron construction. The
  shared structured generator must produce examples in all four expected classes: embedded convex,
  embedded non-convex, immersed, and degenerate. It preserves a reproducible description of each.
- **Generic stellation and greatening:** focused tests independently cover plane deduplication,
  pair/triple intersections, facet and cell construction, cell power, adjacency, symmetry orbits,
  main-line prefix generation, current-prefix recognition, connected selection and complement,
  source-face-cycle reconstruction, edge-line provenance, face signatures, deterministic Result
  ordering, caching, progress, repetition, and every rejection contract. Geometry equivalence tests
  prove `S(D) = SD`, `G(D) = GD`, `G(I) = GI`, `S(GD) = GSD`, and `G(SD) = GSD`, including scale,
  rotation, reflection, and vertex/face renumbering. They also prove `S(G(D)) = G(S(D))` without
  allowing production code to consult seed recognition, catalog names, tags, fingerprints, or
  `KeplerPoinsotGeometry`. Representative non-catalog Platonic, Archimedean, Catalan, family, and
  transformed inputs verify useful generic results or precise inapplicability; selected outputs
  preserve or strengthen the input point group and never return a compound. Result Reset, dynamic
  range, `S~l=2` and `G~l=2` round trips, out-of-range errors, and add/remove/repeated-operation UI
  behavior have worker and browser tests.
- **Serialization compatibility:** exhaustive uniqueness and canonical round trips for every seed,
  family, transform, macro, orbit target, chirality, and tweak tag; rejected malformed star-family
  and new-transform tags; omission and restoration of the default Radius; and collision checks
  across all registered operation and tweak tags. Golden legacy states for `SD`, `GD`, `GSD`, and
  `GI` load the corresponding classical immersed source solids without an implicit `R`, preserve
  the surviving alpha face orbit and presentation settings, and offer Resolve. Separate golden
  states prove that an explicit `R`, `r[α]`, and `f[α]~R=1.25` round-trip unchanged. Legacy targeted
  operations that name a removed resolved-only orbit produce a structured applicability error
  rather than a parse failure or silent retargeting.
- **UI and worker integration:** default family selection, seed settings, keyboard navigation,
  recycle and pentagram actions, seed-only indicator ownership, independent face/inter-face
  intersection reporting, last-transform-only ownership for transformed results, append-only
  Resolve behavior, stage-to-pill issue mapping, saved-configuration compatibility, worker
  serialization, and representative rendering and export acceptance tests.

Shared adversarial fixtures should be used across detection, resolution, STL preparation, and
OpenSCAD acceptance, but each suite asserts its own algorithm's contract. A passing browser smoke
test is not a substitute for these unit tests.

In addition to the committed unit tests, run a reproducible JVM stress campaign over at least
10,000 randomly generated self-intersecting polygons. Embed each polygon in a closed prism- or
pyramid-like fixture, then vary vertex count, winding, crossing layout, orientation, scale,
extrusion depth, face visibility, and rim settings. Every generated export must pass the complete
post-quantization STL validator or return the documented structured limit/topology error without
serializing an STL. Track limit errors separately so the campaign cannot hide a regression by
turning ordinary cases into rejections. The stress runner records its random seed and reduces any
unexpected failure to a small reproducible fixture. Do not add all 10,000 generated cases to the
normal unit-test suite; add only the minimized examples that exposed distinct bugs during
development.
