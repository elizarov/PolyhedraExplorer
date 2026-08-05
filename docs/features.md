# Features

## Polyhedra

- 31 built-in convex seeds: 5 Platonic, 13 Archimedean, and 13 Catalan solids.
- Transform chains containing None/removal, Truncated, Rectified, Cantellated, Dual, Bevelled, Snub, Chamfered, `Canonical` (the canonicalization operation), and topology-valid Drop operations.
- Optional catalog replacement: when a completed transform chain is geometrically equivalent to a built-in seed, a suggestion appears to the right of the chain. The current exploratory state remains unchanged until the suggestion is clicked.
- Background WasmGC execution for manipulation algorithms, with live convergence progress for symmetry-quotient canonicalization and cancellation when a newer state supersedes it.
- Circumradius, midradius, or inradius normalization.
- Transform applicability, identity, maximum-size, failure, and non-planar-face feedback.

## Exploration and rendering

- Interactive WebGL face and edge rendering with mouse and touch rotation and zoom.
- Two-way orbit rollover between the F/E/V popup rows and the canvas. Canvas picking considers front-facing geometry; manually hidden face orbits retain their full virtual picking surfaces. Selected faces are highlighted, selected edges receive a contrasting overlay, and selected vertices are marked with small shaded balls.
- Configurable automatic rotation, view scale, face expansion, transparency, width, rim, display mode, lighting, and shininess.
- Configuration sliders, checkboxes, and dropdowns stay synchronized in both directions with programmatic and URL-driven parameter changes.
- Smooth geometry transitions and topology-aware keyframe animations returned by the Wasm core.
- Rotation-orbit classification with orbit-based coloring and selection highlighting.
- Live face/edge/vertex counts and frames-per-second display.

## Inspection

- Face-kind table with count, inradius, adjacency, vertex figure, planarity, visibility, and available drop action.
- Edge-kind table with count, midradius, adjacency, and local geometry.
- Vertex-kind table with count, circumradius, adjacency, and vertex figure.
- Hide/show all faces or individual face kinds with immediately synchronized popup controls, while retaining configurable rims and shell width.

## Persistence and export

- Complete application state is encoded in the URL hash and restored on load.
- Export of rendered solid geometry to standards-compatible ASCII STL without zero-area facets.
- Export of polyhedron vertices and faces to OpenSCAD source.
- Export size and shell parameters are controlled from the export drawer.

## Runtime requirements

The application requires ES modules, WebGL, and WasmGC. Current Chromium, Firefox, and Safari releases that implement Kotlin/WasmGC are supported; legacy browsers without WasmGC are not given a JavaScript algorithm fallback.
