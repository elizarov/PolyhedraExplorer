# Glossary

| Term | Meaning |
| --- | --- |
| Polyhedron | An immutable vertex/edge/face mesh plus orbit-kind metadata. |
| Seed | A built-in starting polyhedron selected before transforms. |
| Catalog recognition | Comparison of a completed transform result with the built-in seeds after circumradius normalization. Globally reflected (opposite-handed) realizations count as the same geometry, while rotation-orbit analysis remains handedness-sensitive. A match is offered as an optional replacement; accepting it replaces the equivalent seed-plus-transform-chain state with the single catalog seed. |
| [Transform](transformations.md) | An ordered operation that derives a new polyhedron from the previous stage. |
| Primitive transform | A directly executable transform. The fixed primitive choices are Truncated, Rectified, Dual, Snub, Chamfered, and Canonical; topology-dependent Drop operations are also primitive. |
| Macro | One named logical transform whose implementation expands to a sequence of primitive transforms. It occupies one position in the URL and UI chain. |
| Macro folding | Optional replacement of a matching transform-chain suffix with its named macro. Folding changes notation, not the resulting geometry. |
| Composition fusion | Geometry-aware execution of a primitive subsequence through an equivalent direct kernel. `aa` is fused as cantellation and `at` as bevel so repeated rectification retains the intended regular realization. |
| Transform chain | The ordered list of transforms applied after the seed. |
| F/E/V or FEV | Face, edge, and vertex counts, in that order. |
| Kind | A rotation-orbit identifier for equivalent faces, edges, or vertices. Greek letters denote face kinds; capital Latin letters denote vertex kinds. |
| Rotation orbit | A set of mesh elements that are interchangeable under the polyhedron's rotational symmetries. |
| Orbit rollover | Transient selection of a face, edge, or vertex rotation orbit. Hovering either its F/E/V popup row or matching front-facing canvas geometry selects the entire orbit in both views. |
| Topology | Connectivity of vertices, edges, and faces independent of coordinates. |
| Same topology | Two meshes whose indexed connectivity is compatible, allowing direct WebGL interpolation. |
| Drop | Removal of a topologically admissible face, edge, or vertex kind followed by reconstruction of the remaining boundary. |
| Canonical representation | The normalized geometric representation targeted by the application for a polyhedron topology: its edge tangency points are centered at the origin, its faces are planar, and every edge is tangent to one common midsphere within project tolerances. It is unique up to rotation and reflection. This term names the resulting form, not the procedure used to find it. |
| [Canonicalization](canonicalization.md) | The iterative edge-nearpoint/circle-packing algorithm used to find a canonical representation. The UI exposes this operation as the `Canonical` transform and reports its convergence progress. |
| Planar face | A face whose vertices lie in one plane within the project tolerance. |
| Circumradius | Maximum distance from the origin to a vertex. |
| Midradius | Mean closest distance from the origin to an edge. |
| Inradius | Minimum distance from the origin to a face plane. |
| Face rim | In-face inset used to render borders around hidden or expanded faces. |
| Keyframe | A mesh and interpolation fraction returned by the core for a transform animation step. |
| Compose HTML | JetBrains Compose runtime and DOM builders used for the browser controls. |
| WasmGC | WebAssembly garbage-collected object model targeted by Kotlin/Wasm. |
| Core request | Serialized seed, transform, scale, prior-state, and animation inputs sent to the Wasm worker. |
| Core response | Serialized mesh, intermediates, topology metadata, issues, and keyframes returned by Wasm. Progress is delivered separately while the worker is running. |
