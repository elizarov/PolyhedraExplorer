# Export

This document owns the geometry contract for STL and OpenSCAD output. UI controls and print preview
are summarized in [Features](features.md); immersed surface semantics and hidden star rims are
defined in [Self-intersecting polyhedra](self-intersections.md), and their physical construction is
defined in [Rim geometry](rim_geometry.md).

## Presentation input

Export starts from the authoritative polyhedron plus the presentation settings that affect physical
geometry: scale, face width, face expansion, hidden face kinds, and rim width. It does not read back
WebGL buffers and does not mutate the transform chain. Print-preview color is visual-only and does
not affect either format.

Visible faces contribute their resolved nonzero-winding regions. Hidden faces contribute the
core-supplied polygonal rim regions. Simple non-planar faces use their shared deterministic
triangles; because WebGL cannot fill one folded polygon as a single surface, their face orbits are
also presentation-hidden automatically. Their rims are planar patches clipped to those shared
triangles. These rules keep display and export on the same filled surface without requiring them to
share a final tessellation.

## STL

STL conversion runs in a dedicated Wasm worker and has only two outcomes: a completely validated
ASCII STL solid or a structured error with no download. The converter never serializes an empty,
partial, open, intersecting, or resource-truncated mesh.

### Conversion pipeline

1. Build the requested thickened presentation from visible face regions, hidden-face rims, width,
   expansion, and scale. With zero expansion, every inner face plane is offset by the configured
   perpendicular Width and neighboring planes meet on their dihedral bisector. Hidden openings are
   widened only where required for a perpendicular wall to reach that shared inner edge. Expanded,
   folded, and immersed fallback pieces remain independently closed inputs to the Boolean stage.
2. Weld only double-precision, scale-relative input noise, discard degenerate or duplicate input triangles,
   and group coplanar pieces into logical surfaces.
3. Accept an already embedded triangle boundary directly; otherwise corefine the complete triangle
   soup and select its three-dimensional zero/nonzero-winding interface. Independently closed face
   and rim extrusions retain piece identities, allowing winding tests to reject non-overlapping
   bounding boxes and classify large pieces by accelerated ray crossings without changing Boolean
   semantics.
4. Require an outward-oriented edge-closed boundary, retaining every material component, and keep the corefined arrangement
   triangulated. The STL path does not reconstruct source-style polygon faces or require one source
   vertex fan at a high-winding point junction.
5. Quantize coordinates to eight decimal places, rebuild indexed triangles, orient positive volume,
   and recheck exact edge incidence, orientation, and finite coordinates on every shell. The
   arrangement has already performed the surface-intersection validation, and final rounding is
   finer than its weld tolerance.
6. Serialize ASCII STL only after the independent final-mesh checks succeed.

High-winding source faces use independently closed rim pieces directly, and a rare topology failure
of another exact shell retries with one topology-stable radial shell referenced to the closest face
plane. That conservative fallback can make farther face orbits thicker, but never thinner, than the
configured width.

The final mesh requires finite non-degenerate triangles, two oppositely directed uses of every
edge, outward positive material volume, and no duplicate triangles. Separate closed components are
valid; intersecting compound members are unioned before serialization. The
arrangement has already selected the solid boundary before quantization. These are postconditions
of STL conversion, not properties inferred from the input `Polyhedron`.

An explicit Resolved transform is unnecessary. Export performs its own presentation-aware solid
conversion and preserves hidden immersed rims before resolving the complete three-dimensional
arrangement. In particular, Prism 5/2 with its two cap faces hidden exports a watertight solid with
a visible pentagram rim at each end.

### Browser limits and errors

STL tessellation can be much larger than the source polyhedron, so the exporter has limits separate
from the shared 32,767-edge polyhedron limit:

| Resource | Limit |
| --- | ---: |
| Presentation triangles | 250,000 |
| Broad-phase candidate pairs | 2,000,000 |
| Arrangement fragments | 1,000,000 |
| Final triangles | 500,000 |
| Accounted working memory | 256 MiB |
| Conversion time | 30 seconds |

Limits are checked before the corresponding expansion or allocation, and conversion remains
cancellable. A failure identifies its stage (`Input`, `BroadPhase`, `Arrangement`, `Quantization`,
or `Validation`), category (`InvalidInput`, `Topology`, or `Limit`), reason, and limit/observed value
when applicable. The export popup keeps the current settings and offers OpenSCAD as the recovery
path for geometry or workloads that cannot be completed safely in the browser.

## OpenSCAD

OpenSCAD receives polygonal construction geometry and owns final tessellation and Boolean
evaluation. It does not call the STL arrangement and remains available after an STL topology or
resource error.

The output form follows the geometry:

- **Closed polyhedron.** An already embedded presentation with every face visible and zero face
  expansion is emitted as one `polyhedron`. Planar resolved cells remain polygon cycles; simple
  non-planar faces use their deterministic triangles.
- **Closed compound.** With every face visible, zero expansion, and convex members, an explicit
  `union()` contains each member's closed `polyhedron`. Shared positions are welded only within a
  member. OpenSCAD performs their Boolean union; the compound is not exported as a hollow shell.
- **Piece union.** An immersed presentation, hidden face orbit, or nonzero expansion is emitted as
  one explicit `union()` of individually closed face or rim pieces. At zero expansion each piece is
  a `polyhedron` whose bottom vertices use the same equal-offset face-plane joins as WebGL and STL;
  its top region is triangulated with its holes intact. Nonzero expansion uses perpendicular
  polygon extrusion because separated pieces no longer share edge joins. Visible non-planar regions
  are closed triangle pieces, while hidden non-planar rims retain each clipped patch and its holes.

Every piece-union member has front and back caps and side walls. The application does not pre-union
pieces from different faces and does not ask OpenSCAD to repair open sheets. OpenSCAD's geometry
engine removes internal walls and produces the final solid when the script is rendered.

## Verification

Focused core tests cover presentation construction, arrangement, quantization, final STL
validation, every resource guard, deterministic output, and the no-partial-download rule. Structural
OpenSCAD tests distinguish closed-polyhedron and piece-union output, verify closed mitered pieces,
and cover regular, asymmetric, immersed, and folded fixtures. All five regular compounds are tested
with full faces and hidden rims through STL conversion and OpenSCAD generation. A separated-member
fixture verifies that valid disconnected material is retained. Geometry validation uses local
triangle scale for degeneracy and intersection contacts, so tiny valid arrangement fragments are
not rejected solely because the complete model is much larger.
Shared regressions include immersed catalog solids, concave and non-planar faces,
expanded pieces, hidden rims, Prism 5/2 with hidden caps, all-rim Antiprism 5/2 and Antiprism 7/3,
the acute triangular rims of resolved Bipyramid 7/2, and Pyramid 7/2 with either only its immersed
base or every face orbit hidden. High-winding pyramid coverage includes the minimized Pyramid 10/3
case and every hidden-orbit combination of Pyramid 15/7 and Pyramid 19/9. Folded-rim regressions include Truncated stellated octahedron and
its second truncation; lower-level coverage also uses an asymmetric synthetic non-planar
quadrilateral so the construction is not tied to a catalog transform. The Antiprism 7/3 JVM
regression also guards a complete conversion time below one second.

Rendering coverage includes the dimensionally unambiguous cube case: equal rim and width settings
produce square edge cross-sections. Core presentation tests independently verify configured depth
for a cube, tetrahedron, and immersed star rim, and validate the resulting cube STL solid.

The opt-in deterministic STL stress campaign and its current corpus results are documented with the
command that runs it in [Development](development.md).
