# Glossary

| Term | Meaning |
| --- | --- |
| Polyhedron | An immutable vertex/edge/face mesh plus orbit-kind metadata. |
| [Proper surface](non-convex.md) | One connected, consistently oriented two-manifold mesh whose faces intersect only at their explicitly shared vertices and edges. Every edge has exactly two opposite face uses. |
| Non-convex polyhedron | A proper polyhedron that is not the boundary of a convex set. Its faces may be convex or concave; non-convexity is a property of the whole realization. |
| Concave face | A simple polygonal face with at least one reflex corner. It is filled, picked, animated, and exported through the model's shared ear-clipped triangulation rather than a triangle fan. |
| Compound | Two or more disconnected closed polyhedral shells treated as one result. Compounds are rejected. |
| [Seed](seeds.md) | A built-in starting polyhedron selected before transforms. There are 31 fixed types, four additional prime-tagged chiral representations, and four families with `n = 3..100`. |
| Seed family | A parameterized seed class: Prism, Antiprism, Pyramid, or Bipyramid. Its base/equator order `n` is stored in the seed tag and changed with bounded up/down controls. The UI privately remembers the last selected `n` (initially 3) across popup and horizontal navigation through every seed category; only a seed-level reset clears it. |
| Catalog recognition | Orientation-sensitive comparison of a completed transform result with both handed variants of the built-in seeds after circumradius normalization. A match with the proper chirality is offered as an optional replacement; accepting it replaces the equivalent seed-plus-transform-chain state with the single catalog seed. |
| [Transform](transformations.md) | An ordered operation that derives a new polyhedron from the previous stage. |
| Continuous transform parameter | A dimensionless multiplier for a transform's coordinate construction, edited in the gear popup while that transform is last in the chain. `100%` is the regular default and is omitted from serialized tags; only non-default values are stored. Its selectable interval is computed in the Wasm worker from the actual mesh at that transform stage and the operation's other current parameters, then rounded inward to the slider step. Parameters use topology-compatible interpolation; transforms such as full Rectified that have no such symmetric degree of freedom remain fixed. |
| Primitive transform | A directly executable transform. The fixed primitive choices are Truncated, Rectified, Dual, Snub, Propeller, Whirl, Quinto, Chamfered, and Canonical; topology-dependent Drop, Kis face, Truncate vertex, and Rectify vertex operations are also primitive. |
| Macro | One named logical transform whose implementation expands to a sequence of primitive transforms. It occupies one position in the URL and UI chain. |
| Chirality | Handedness of a shape or operation. Snub, Propeller, Whirl, Gyro, and four catalog seed types have two mirror-related variants; the alternate variant is written with a trailing prime (`'`). Quinto is achiral. |
| Prefix replacement | Optional simplification of the longest displayed transform-chain prefix formally equivalent—including chirality—to one primitive operation or macro after macro expansion and adjacent Dual cancellation. It is only a proposal; accepting it can expose a fused regular coordinate realization. |
| Composition fusion | Geometry-aware execution of a primitive subsequence through an equivalent direct kernel. `aa` is fused as cantellation and `at` as bevel so repeated rectification retains the intended regular realization. |
| Transform chain | The ordered list of transforms applied after the seed. |
| F/E/V or FEV | Face, edge, and vertex counts, in that order. |
| Kind | A rotation-orbit identifier for equivalent faces, edges, or vertices. Greek letters denote face kinds; capital Latin letters denote vertex kinds. |
| Rotation orbit | A set of mesh elements that are interchangeable under the polyhedron's rotational symmetries. |
| [Point group](symmetries.md) | The full Schoenflies point group derived from the current geometry, including proper rotations and every available reflection, inversion, or rotoreflection. Examples are pyramidal `C_nv`, prismatic `D_nh`, full octahedral `O_h`, and chiral icosahedral `I`. |
| Rotation axis | A line through the origin around which a non-identity proper rotation preserves the current geometry. The symmetry overlay draws each physical axis once as a thin black line, with configurable half-length relative to the circumradius. |
| Reflection plane | A plane through the origin whose mirror reflection preserves the current geometry. The symmetry overlay renders each one as a translucent circular disk with configurable radius relative to the circumradius. Chiral geometries have none even though they retain their proper rotations and axes. |
| Orbit rollover | Transient selection of a face, edge, or vertex rotation orbit. Hovering either its F/E/V popup row or matching front-facing canvas geometry, or navigating popup rows with Up/Down, selects the entire orbit in both views. |
| Orbit-targeted operation | An operation whose concrete behavior names one face, edge, or vertex rotation orbit. Drop removes an admissible orbit, Kis face pyramidalizes one face orbit, and Truncate/Rectify vertex cut one vertex orbit. The stored chain item retains the operation and exact orbit kind; transient UI memory separately retains the last face, edge, and vertex targets when switching operation type. |
| Topology | Connectivity of vertices, edges, and faces independent of coordinates. |
| Same topology | Two meshes whose indexed connectivity is compatible, allowing direct WebGL interpolation. |
| Drop | Removal of a topologically admissible face, edge, or vertex kind followed by reconstruction of the remaining boundary. A last-chain Drop exposes cyclic target controls for the valid orbits of the same element family. |
| Kis face | Selective Kis applied to one face rotation orbit. The concrete name and tag include the target, such as `Kis α` and `k[α]`. |
| Truncate vertex | Selective truncation applied to one vertex rotation orbit. The concrete name and tag include the target, such as `Truncate A` and `t[A]`. |
| Rectify vertex | Selective midpoint-limit truncation applied to one vertex rotation orbit. Adjacent selected vertices share their edge midpoint. The concrete name and tag include the target, such as `Rectify A` and `a[A]`. |
| Canonical representation | The normalized geometric representation targeted by the application for a polyhedron topology: its edge tangency points are centered at the origin, its faces are planar, and every edge is tangent to one common midsphere within project tolerances. It is unique up to rotation and reflection. This term names the resulting form, not the procedure used to find it. |
| [Canonicalization](canonicalization.md) | The iterative edge-nearpoint/circle-packing algorithm used to find a canonical representation. The UI exposes this operation as the `Canonical` transform and reports its convergence progress. |
| Planar face | A face whose vertices lie in one plane within the project tolerance. |
| Circumradius | Maximum distance from the origin to a vertex. |
| Midradius | Mean closest distance from the origin to an edge. |
| Inradius | Minimum distance from the origin to a face plane. |
| Face rim | In-face inset used to render borders around hidden or expanded faces. Its maximum stops before the first edge collapse or concave reflex-corner collision, and it uses the average plane for a non-planar face. |
| Print preview | An export-drawer rendering mode that replaces face-orbit colors with one selected filament color across faces, rims, inner surfaces, and walls, while suppressing edge overlays. It changes presentation only; mesh geometry and exported STL/SCAD data are unchanged. |
| OKLCH | The perceptual color space used by the print-color picker. Lightness controls brightness, chroma controls colorfulness, and hue chooses the color family; out-of-gamut selections reduce chroma while preserving lightness and hue before conversion to sRGB. |
| Saved configuration | One append-only, versioned browser-local record containing a custom or generated name, save timestamp, exact URL-format application state, and cropped scene preview. Loading it restores the URL state and reloads the application; saves are ordered newest first and are not deleted by the UI. |
| [Plastic material model](lighting.md) | The opaque dielectric Cook-Torrance BRDF used for faces: GGX microfacet distribution, correlated Smith visibility, Schlick Fresnel derived from IOR, and energy-conserving diffuse reflection. |
| Environment | The scene surrounding the polyhedron. `None` retains the background-only renderer; `Table` adds a fixed neutral-gray plastic receiver and a geometry-dependent cast shadow while the polyhedron rotates above it. |
| Planar projected shadow | A cast-shadow technique that projects the animated rendered face mesh from the same fixed point light used to illuminate it onto the table plane. One projection gives a sharp silhouette, and stencil unioning prevents face overlap from over-darkening it. |
| Roughness | Perceived isotropic microsurface roughness used by GGX. Lower values concentrate reflection into a sharp glossy highlight; higher values spread it into a broad matte response. |
| IOR | Index of refraction of the plastic. The shader converts it to normal-incidence reflectance `F0 = ((IOR - 1) / (IOR + 1))²`; PLA defaults to `1.46`. |
| Keyframe | A mesh and interpolation fraction returned by the core for a transform animation step. |
| Compose HTML | JetBrains Compose runtime and DOM builders used for the browser controls. |
| WasmGC | WebAssembly garbage-collected object model targeted by Kotlin/Wasm. |
| Core request | Serialized seed, transform, scale, prior-state, and animation inputs sent to the Wasm worker. |
| Core response | Serialized mesh, intermediates, topology metadata, issues, and keyframes returned by Wasm. Progress is delivered separately while the worker is running. |
