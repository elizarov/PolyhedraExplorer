# Live specification

This directory describes the current behavior and structure of Polyhedra Explorer. It is a live specification: changes to architecture, behavior, dependencies, or performance must update these documents in the same change. The documents describe the resulting state, not a change log or roadmap.

Each detailed contract has one owning document. `features.md` summarizes user-visible behavior and
`glossary.md` defines terms; both link to the owner instead of repeating algorithm or pipeline
specifications.

- [Keyboard navigation](keyboard.md) — shortcut behavior, contextual navigation, focus rules, and in-app help.
- [Transformations and macros](transformations.md) - names, notation, expansions, count formulas, and operation summaries.
- [Seed catalog](seeds.md) — category definitions, complete seed table, symmetry and orbit counts, duals, and construction recipes.
- [Symmetries](symmetries.md) — full Schoenflies point groups, axis/plane exploration, rotation orbits, and possible symmetry strengthening.
- [Lighting and plastic material](lighting.md) — dielectric microfacet model, PLA defaults, controls, performance, and limitations.
- [Architecture](architecture.md) — module boundaries, runtime flow, and invariants.
- [Non-convex geometry](non-convex.md) — surface validity, shared triangulation, rims, and transform applicability.
- [Self-intersecting polyhedra](self-intersections.md) — immersed surfaces, nonzero-winding face geometry, Resolve, and intersection UI.
- [Export](export.md) — STL conversion, OpenSCAD construction, validation, and browser resource limits.
- [Features](features.md) — user-visible capabilities and supported operations.
- [Glossary](glossary.md) — project terminology and notation.
- [Canonicalization](canonicalization.md) — canonical representation invariants, solver steps, safeguards, and optimizations.
- [Development](development.md) — current toolchain and verification commands.
- [Performance](performance.md) — benchmark method and current JS/WasmGC comparison.
