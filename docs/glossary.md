# Glossary

| Term | Meaning |
| --- | --- |
| Polyhedron | An immutable vertex/edge/face mesh plus orbit-kind metadata. |
| Seed | A built-in starting polyhedron selected before transforms. |
| Transform | An ordered operation that derives a new polyhedron from the previous stage. |
| Transform chain | The ordered list of transforms applied after the seed. |
| F/E/V or FEV | Face, edge, and vertex counts, in that order. |
| Kind | A rotation-orbit identifier for equivalent faces, edges, or vertices. Greek letters denote face kinds; capital Latin letters denote vertex kinds. |
| Topology | Connectivity of vertices, edges, and faces independent of coordinates. |
| Same topology | Two meshes whose indexed connectivity is compatible, allowing direct WebGL interpolation. |
| Drop | Removal of a topologically admissible face, edge, or vertex kind followed by reconstruction of the remaining boundary. |
| Canonical | Iterative adjustment toward a polyhedron whose edges are tangent to a common midsphere. |
| Planar face | A face whose vertices lie in one plane within the project tolerance. |
| Circumradius | Maximum distance from the origin to a vertex. |
| Midradius | Mean closest distance from the origin to an edge. |
| Inradius | Minimum distance from the origin to a face plane. |
| Face rim | In-face inset used to render borders around hidden or expanded faces. |
| Keyframe | A mesh and interpolation fraction returned by the core for a transform animation step. |
| Compose HTML | JetBrains Compose runtime and DOM builders used for the browser controls. |
| WasmGC | WebAssembly garbage-collected object model targeted by Kotlin/Wasm. |
| Core request | Serialized seed, transform, scale, prior-state, and animation inputs sent to Wasm. |
| Core response | Serialized mesh, intermediates, topology metadata, issues, progress outcome, and keyframes returned by Wasm. |
