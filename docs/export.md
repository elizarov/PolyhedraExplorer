# Export

This document owns the geometry contract for STL and OpenSCAD output. UI controls and print preview
are summarized in [Features](features.md); immersed surface semantics and hidden star rims are
defined in [Self-intersecting polyhedra](self-intersections.md).

## Presentation input

Export starts from the authoritative polyhedron plus the presentation settings that affect physical
geometry: scale, face width, face expansion, hidden face kinds, and rim width. It does not read back
WebGL buffers and does not mutate the transform chain. Print-preview color is visual-only and does
not affect either format.

Visible faces contribute their resolved nonzero-winding regions. Hidden faces contribute the
core-supplied polygonal rim regions. Simple non-planar faces use their shared deterministic
triangles. These rules keep display and export on the same filled surface without requiring them to
share a final tessellation.

## STL

STL conversion runs in a dedicated Wasm worker and has only two outcomes: a completely validated
ASCII STL solid or a structured error with no download. The converter never serializes an empty,
partial, open, intersecting, or resource-truncated mesh.

### Conversion pipeline

1. Build the requested thickened presentation from visible face regions, hidden-face rims, width,
   expansion, and scale. A topology-preserving radial inner shell keeps derived intersection points
   on the same logical face construction.
2. Weld only representation-scale input noise, discard degenerate or duplicate input triangles,
   and group coplanar pieces into logical surfaces.
3. Accept an already embedded triangle boundary directly; otherwise corefine the complete triangle
   soup and select its three-dimensional zero/nonzero-winding interface. Independently closed rim
   extrusions retain piece identities, allowing winding tests to reject non-overlapping bounding
   boxes and classify large pieces by accelerated ray crossings without changing Boolean semantics.
4. Require one connected, outward-oriented embedded boundary and keep its final boundary
   triangulated.
5. Quantize coordinates to eight decimal places, rebuild indexed triangles, orient positive volume,
   and validate again after quantization.
6. Serialize ASCII STL only after the independent final-mesh checks succeed.

The final mesh requires finite non-degenerate triangles, two oppositely directed uses of every
edge, one connected component, outward positive volume, no duplicate triangles, and no residual
surface intersections. These are postconditions of STL conversion, not properties inferred from
the input `Polyhedron`.

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

Two output forms are used:

- **Closed polyhedron.** An already embedded presentation with every face visible and zero face
  expansion is emitted as one `polyhedron`. Planar resolved cells remain polygon cycles; simple
  non-planar faces use their deterministic triangles.
- **Piece union.** An immersed presentation, hidden face orbit, or nonzero expansion is emitted as
  one explicit `union()` of individually closed face or rim extrusions. Planar regions preserve
  outer and hole paths, including pentagram rims; non-planar regions are emitted as closed triangle
  pieces.

Every piece-union member has front and back caps and side walls. The application does not pre-union
pieces from different faces and does not ask OpenSCAD to repair open sheets. OpenSCAD's geometry
engine removes internal walls and produces the final solid when the script is rendered.

## Verification

Focused core tests cover presentation construction, arrangement, quantization, final STL
validation, every resource guard, deterministic output, and the no-partial-download rule. Structural
OpenSCAD tests distinguish closed-polyhedron and piece-union output and verify polygon paths and
closed pieces. Shared regressions include immersed catalog solids, concave and non-planar faces,
expanded pieces, hidden rims, Prism 5/2 with hidden caps, all-rim Antiprism 5/2 and Antiprism 7/3,
the acute triangular rims of resolved Bipyramid 7/2, and Pyramid 7/2 with either only its immersed
base or every face orbit hidden. The Antiprism 7/3 JVM regression also guards a complete conversion
time below one second.

The opt-in deterministic STL stress campaign and its current corpus results are documented with the
command that runs it in [Development](development.md).
