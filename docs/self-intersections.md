# Self-intersecting polyhedra

This document specifies intentional surface immersion, per-face resolution, and the explicit
Resolved operation. General concave and non-planar embedded faces are specified in
[Non-convex geometry](non-convex.md). Seed identities belong to the [Seed catalog](seeds.md),
operation names and applicability belong to [Transformations and macros](transformations.md), and
printable conversion belongs to [Export](export.md).

## Geometry representations

An immersed polyhedron and its resolved physical boundary are different polyhedra.

| Representation | Purpose | Authoritative topology |
| --- | --- | --- |
| Source surface | Transform input, F/E/V, symmetry, orbit kinds, serialization, and duality | The original vertices, edges, and directed face boundaries |
| Resolved face geometry | Rendering and picking one source face | Derived presentation cells and triangles; crossings are not source vertices |
| Resolved physical boundary | Embedded result of the `Resolved` transform | The zero/nonzero-winding interface, including intersection-created elements |

Every immutable `Polyhedron` carries one `ResolvedFaceGeometry` record per source face. The record
contains presentation vertices, nonzero-winding cells, triangles, boundary and internal arrangement
edges, and provenance back to source vertices, edges, faces, and boundary-segment parameters. It is
serialized through the Wasm worker with the source mesh. Browser code consumes it directly and
does not repeat polygon arrangement.

Until Resolved is applied, source F/E/V and rotation orbits remain the displayed topology. Applying
Resolved creates a new embedded polyhedron whose own physical F/E/V and orbits become authoritative.
Derived presentation triangles inside one physical polygon are not counted as faces or edges.

## Nonzero-winding fill

Face and solid resolution use the nonzero-winding rule. A planar cell or three-dimensional region
contains material exactly when its signed winding number is not zero. Winding magnitude does not
represent multiple material layers: winding `1`, `-1`, `2`, and `-2` are all filled. Only the
interface between zero and nonzero winding is retained, oriented away from material.

A `{5/2}` pentagram is therefore fully filled. Its five arms have winding magnitude one and its
central pentagon has winding magnitude two. The even-odd rule, which would cut out the center, is
not used.

## Per-face resolution

A self-crossing source face must be planar within a scale-aware tolerance. The face is resolved in
its own plane as follows:

1. Project its directed boundary into a stable two-dimensional basis.
2. Split proper boundary-segment intersections into a planar arrangement.
3. Extract bounded cells whose winding is nonzero.
4. Triangulate each retained simple cell with the source face's orientation.
5. Record provenance for every derived vertex, edge, cell, and triangle.

Simple planar, concave, and simple non-planar faces receive the same record shape. Their triangles
come from the shared deterministic face triangulation described in
[Non-convex geometry](non-convex.md).

A face that is both non-planar and self-crossing is invalid. Projecting such a boundary can make
edges at different three-dimensional positions appear to cross, so lifting the projected
intersection would invent geometry. The core rejects that stage instead of displaying or exporting
an ambiguous surface.

Per-face resolution does not remove intersections between different faces. WebGL can depth-test
the resulting triangles, while the explicit Resolved transform or the STL conversion pipeline is
responsible for constructing an embedded solid boundary.

## Validation contracts

Geometry checks are layered, and each stronger contract contains the preceding guarantees:

| Contract | Guarantees | Principal consumers |
| --- | --- | --- |
| `AbstractSurface` | One connected, consistently oriented combinatorial two-manifold with valid source incidence | Topological operations, F/E/V, and orbit analysis |
| `RenderableImmersion` | Valid resolved-face records with finite, consistently oriented, non-degenerate presentation triangles | WebGL, picking, intersection analysis, and Resolved |
| `EmbeddedBoundary` | A renderable immersion with no contact between unrelated surface features | Operations that require a physical boundary |

Renderable immersions may contain transverse self-crossings within a source face, intersections
between unrelated face surfaces, and isolated singular contacts. Persistent line or area overlap,
coincident distinct source vertices, degenerate source elements, disconnected source components,
or invalid resolved triangles are rejected. Signed-volume cancellation is not itself an immersion
error.

`analyzeGeometry` reports the strongest satisfied contract. The evaluated core stage carries that
intersection summary with its response, so the UI can reuse it. `SelfCrossingFace` and
`IntersectingFaces` are reported separately. Algorithms declare their minimum input contract and
output policy; an unsupported geometry returns a structured applicability issue rather than
reaching the renderer.

## Resolved

`Resolved` (`R`) converts a renderable immersion into one connected embedded boundary of its
nonzero-winding material. It is a generic geometry operation, not a catalog-seed substitution.
Its production path:

1. Takes the presentation triangles supplied by every resolved source face. Each triangle carries
   its cell's absolute face-winding multiplicity into the generalized solid-winding sum; the
   geometric fragment itself is still emitted only once.
2. Uses sorted bounding intervals to find overlapping triangle candidates.
3. Splits triangles along actual non-coplanar intersection segments and rejects positive-area
   coplanar source-face overlap.
4. Samples generalized winding on both sides of each resulting fragment and retains only fragments
   separating zero from nonzero winding.
5. Welds matching fragment vertices with a scale-aware tolerance, cancels duplicate internal
   interfaces, and makes split boundary edges conforming.
6. Merges adjacent coplanar fragments with the same source set only when their boundary is one
   simple cycle, then removes arrangement-only degree-two points.
7. Requires exactly two incident faces per physical edge, one connected component, and no more than
   the shared 32,767-edge polyhedron limit.
8. Builds an outward-oriented polyhedron, records many-to-many source provenance, merges
   geometrically indistinguishable kinds, and validates the result as an embedded boundary.

Resolved is identity on an embedded input, and repeated Resolved makes no further geometric change.
The explicit transform remains in the chain and can be removed with its recycle action. If
nonzero-winding material has multiple components, no boundary, unsupported coplanar overlap, or an
oversized result, Resolved returns a structured error; it never drops components or invents
connections.

The four Kepler-Poinsot meshes retained as historical embedded fixtures are test oracles only. They
do not select production Resolved output and are not used by catalog recognition. A resolved solid
is not catalog-equivalent to its immersed source because their authoritative faces and topology
differ.

## Resolved topology, provenance, and orbits

Resolved output elements have two independent classifications:

- **Provenance** identifies the source vertices, edges, faces, winding cells, and segment positions
  that contributed to an output element.
- **Kind** identifies the element's proper-rotation orbit in the final embedded geometry.

One source orbit can split into several physical orbits, and an intersection element can have
several sources. Resolved therefore assigns output kinds from the completed geometry instead of
reusing source kinds. Face colors, popup rows, visibility, rollover, and later orbit-targeted
operations use those output kinds; provenance remains available to diagnostics and downstream
geometry consumers.

Resolved ordering and merging are deterministic for the same indexed input. Scaling or rotating
the input does not change its physical incidence, and subsequent symmetry analysis may discover a
point group stronger than the source provenance alone implies.

## Intersection indicator

The UI exposes immersion without changing geometry automatically:

- A seed-only immersed result shows a pentagram action on the seed pill.
- With a non-empty transform chain, only the last transform pill can show the action.
- The tooltip reports self-crossing source faces and inter-face crossings independently.
- Activating the action appends an explicit Resolved operation; it never rewrites the seed or an
  earlier transform.

The cached analysis belongs to the evaluated geometry stage. Camera movement, automatic rotation,
display settings, and animation frames do not rerun it.

## Hidden immersed faces

Hiding an immersed face preserves a rim along its original source boundary rather than replacing
it with the outer silhouette of its filled cells. Hiding the cap orbit of Prism 5/2 therefore leaves
a visible pentagram.

The core constructs one uninterrupted sheet along every authoritative source edge. The complete
sheet lies on the winding-interior side of the directed edge and keeps the same side and width before,
through, and after every arrangement crossing. A pentagram therefore has rim material along both
the central pentagon and all five outer triangular arms; a crossing never becomes an endpoint or a
reason to suppress part of an edge.

Adjacent sheets share the exact intersection of their offset lines only at their authoritative
source vertex. All sheets are then unioned in the face plane, so overlapping crossings have no duplicate top surfaces or
internal seams. The union boundary may be split at crossings as a derived triangulation detail, but
the source sheets themselves are never split or clipped by resolved winding cells. The maximum-width
calculation intersects the same sheets with the nonzero-winding fill only to detect complete coverage;
that measurement does not alter presentation geometry.

The result is tessellation-free `ResolvedRimGeometry`: deterministic outer and hole cycles,
source-edge provenance, the applied width, and the maximum width before the rim covers the complete
fill. Adjacent face offsets share the same angle-bisector edge and corner joins for embedded and
immersed surfaces alike. Immersed rims retain the configured top width; a steep dihedral never
widens the visible strip. If an acute miter reaches the inner boundary of that rim, or the first
boundary of a filled incident face, before the configured depth, the shared join stops at that local
offset-surface collapse instead of continuing through the material into an inverted, detached sheet.
The underside tapers to the bounded join while the top rim is unchanged. Every incident face uses
the same join point, so corners remain connected and ordinary immersed triangles retain the same
construction as their embedded counterparts. Simple-face inset and non-planar rim behavior remain owned by
[Non-convex geometry](non-convex.md); export postconditions are specified in [Export](export.md).

## Invariants

- Source topology is never rewritten merely to make an immersion renderable.
- Resolved-face geometry is derived data and never becomes transform input implicitly.
- Face and solid fill use the same nonzero-winding semantics.
- A successful Resolved result is one connected embedded boundary; compounds are rejected.
- Catalog recognition compares authoritative geometry, so Resolved output cannot be proposed as its
  immersed source seed.
- All numerically sensitive arrangement work runs in the Wasm core or export worker, not in DOM or
  WebGL code.
