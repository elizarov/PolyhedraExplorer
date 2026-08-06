# Transformations and macros

Let `F`, `E`, and `V` be the input counts of faces, edges, and vertices, and let
`F'`, `E'`, and `V'` be the corresponding output counts. The formulas below are
topological: they describe a valid closed polyhedron and do not specify its exact
coordinates. Different operations can therefore have the same count formula.

Macro expansions are written in project execution order, from first applied to
last applied. Traditional Conway notation is read in the opposite direction; for
example, the project expansion `a -> t` is conventionally written `ta`.

## Summary

| Name | Alternative names | Tag | Expansion | `F'` | `E'` | `V'` |
| --- | --- | --- | --- | --- | --- | --- |
| None | Identity, remove stage | `n` | - | `F` | `E` | `V` |
| Truncated | Truncate, vertex truncation | `t` | - | `F + V` | `3E` | `2E` |
| Rectified | Ambo, rectification, medial graph | `a` | - | `F + V` | `2E` | `E` |
| Dual | Dualization, reciprocal dual | `d` | - | `V` | `E` | `F` |
| Snub | Snubbing | `s`, `s'` | - | `F + 2E + V` | `5E` | `2E` |
| Propeller | Propellor | `p`, `p'` | - | `F + 2E` | `5E` | `V + 2E` |
| Whirl | - | `w`, `w'` | - | `F + 2E` | `7E` | `V + 4E` |
| Quinto | - | `q` | - | `F + 2E` | `6E` | `V + 3E` |
| Chamfered | Chamfer, edge chamfering | `c` | - | `F + E` | `4E` | `V + 2E` |
| Canonical | Canonicalization | `o` | - | `F` | `E` | `V` |
| Drop | Orbit deletion | `x[kind]` | - | input-dependent | input-dependent | input-dependent |
| Kis face | Selective akisation | `k[face]` | - | input-dependent | input-dependent | input-dependent |
| Truncate vertex | Selective vertex truncation | `t[vertex]` | - | input-dependent | input-dependent | input-dependent |
| Rectify vertex | Selective vertex rectification | `a[vertex]` | - | input-dependent | input-dependent | input-dependent |
| Kis | Akisation, Kleetope, cumulation, pyramid augmentation | `k` | `d -> t -> d` | `2E` | `3E` | `F + V` |
| Join | Dual ambo | `j` | `d -> a -> d` | `E` | `2E` | `F + V` |
| Needle | Dual truncation | `N` | `t -> d` | `2E` | `3E` | `F + V` |
| Zip | Bitruncation, dual kis | `z` | `d -> t` | `F + V` | `3E` | `2E` |
| Cantellated | Expand, expansion, cantellation, Stott expansion | `e` | `a -> a` | `F + E + V` | `4E` | `2E` |
| Bevelled | Bevel, omnitruncation | `b` | `a -> t` | `F + E + V` | `6E` | `4E` |
| Ortho | Dual expand, double join | `O` | `d -> a -> a -> d` | `2E` | `4E` | `F + E + V` |
| Meta | Dual bevel, kis-join | `m` | `d -> a -> t -> d` | `4E` | `6E` | `F + E + V` |
| Gyro | Dual snub | `g`, `g'` | `d -> s -> d` / `d -> s' -> d` | `2E` | `5E` | `F + 2E + V` |

Tags are case-sensitive. The project uses `n` for None and `o` for Canonical,
so Needle and Ortho use `N` and `O`; their conventional Conway symbols are `n`
and `o`. A trailing prime selects alternate chirality: `s'`, `p'`, and `w'`
are flipped Snub, Propeller, and Whirl, while `g' = d -> s' -> d` is flipped
Gyro.

## Primitive transformations

### None (`n`)

None is an identity operation in the core. In the transform editor it is an
action that removes the selected stage, so it is normally absent from a stored
chain. It changes neither topology nor geometry.

### Truncated (`t`)

Truncation cuts off every original vertex. Each original face remains with twice
as many sides, and each original vertex produces one new face. Two output
vertices lie on every original edge, which produces three output edge segments.
The cut depth is chosen from the representative regular-face geometry.

### Rectified (`a`)

Rectification, or ambo, places one output vertex at every original edge midpoint.
Every original face becomes a face through its edge midpoints, and every original
vertex becomes a face through the midpoints of its incident edges. It is the full
midpoint limit of truncation.

### Dual (`d`)

Duality exchanges faces and vertices while preserving a one-to-one correspondence
between edges. The implementation places a dual vertex for every face and builds
one dual face around every original vertex. Applying Dual twice restores the
original topology.

### Snub (`s`)

Snubbing separates and consistently twists the original faces. It keeps one face
for every original face and vertex, then fills the gap around every original edge
with two triangles. The consistent twist makes Snub chiral: reversing the twist
produces its mirror form. The UI writes this alternate operation as `Snub'` (`s'`).

### Propeller (`p`)

Propeller keeps every original vertex and inserts two points at the thirds of
each original edge. Within every source face, one point from each boundary edge
forms a smaller, consistently rotated copy of that face. A quadrilateral fills
the region at every original face corner. Choosing the opposite rotation gives
the mirror operation, written `Propeller'` (`p'`). After building this incidence
structure, the core finds its canonical convex realization so a later transform
does not encounter the coplanar faces of the literal subdivision.

### Whirl (`w`)

Whirl starts with the same original vertices and directed one-third edge points
as Propeller, then adds an inner point for every face-edge incidence. The inner
points form the new central face, and a hexagon fills each original face corner.
Its consistent winding makes it chiral; the reverse winding is `Whirl'` (`w'`).
The returned geometry is the canonical convex realization of this topology.

### Quinto (`q`)

Quinto places one midpoint on every original edge and, on each side of that
edge, another point halfway from the midpoint to the source face center. Those
inner points form a central face, while every original face corner becomes a
pentagon. The construction has reflection symmetry, so Quinto has no alternate
chirality. As with Propeller and Whirl, the returned coordinates are canonicalized
to keep subsequent operations geometrically well-defined.

### Chamfered (`c`)

Chamfering moves the boundary of each original face inward and inserts one
hexagonal face along every original edge. Original vertices remain, and two new
vertices are introduced for every original edge. The current geometry uses
bisector planes and stops when the limiting new edges reach the regular target
length.

### Canonical (`o`)

Canonical changes coordinates but not connectivity. The background
[canonicalization algorithm](canonicalization.md) iteratively seeks the canonical
representation in which faces are planar and all edges are tangent to a common
midsphere. The result is unique only up to rotation and reflection.

### Drop (`x[kind]`)

Drop is a topology-dependent operation on one face, edge, or vertex rotation
orbit. It expands the requested deletion to adjacent elements that would
otherwise become degenerate, verifies that the merged boundary is one valid face,
and rebuilds the remaining mesh. Because the selected orbit and necessary cleanup
vary with the input, Drop has no single linear `F/E/V` formula.

### Kis face (`k[face]`)

Kis face raises an apex over every face in one selected face orbit and replaces
each selected `n`-gon with `n` triangles. Other face orbits retain their original
topology. The apex and retained-vertex coordinates come from the same geometry
construction as the full Kis macro; selecting every face orbit in the core
operation is therefore exactly equivalent to Kis. The operation is offered only
when the input has more than one face orbit and is displayed as `Kis α`, `Kis β`,
and so on.

### Truncate vertex (`t[vertex]`)

Truncate vertex cuts off every vertex in one selected vertex orbit. Each selected
vertex becomes a new face, while unselected vertices remain. Cut points use the
same truncation ratio and edge interpolation as full Truncated; selecting every
vertex orbit in the core operation is therefore exactly equivalent to Truncated.
The operation is offered only when the input has more than one vertex orbit and
is displayed as `Truncate A`, `Truncate B`, and so on.

### Rectify vertex (`a[vertex]`)

Rectify vertex moves the edges incident to one selected vertex orbit to their
midpoints. Every selected vertex becomes a new face, while unselected vertices
remain. When both ends of an edge are selected, they share one midpoint rather
than producing coincident duplicate vertices. Selecting every vertex orbit is
therefore exactly equivalent to full Rectified. The operation is offered only
when the input has more than one vertex orbit and is displayed as `Rectify A`,
`Rectify B`, and so on.

The transform popup presents valid selective operations in its final
`Orbit-targeted` section. Entries use one global order: Drop face, Drop edge, Drop
vertex, Kis face, Truncate vertex, Rectify vertex. The F/E/V orbit rows show every
operation currently available for that exact orbit at the right edge in the same
order, using × for Drop, an upward caret for Kis face, scissors for Truncate
vertex, and compress for Rectify vertex; hover tooltips name the operation and
target orbit. Choosing one stores its first concrete target. When an orbit-targeted
operation is the last chain item, its up/down controls cycle through all currently
valid targets of that same operation and wrap at both ends. The UI remembers the
last face, edge, and vertex target separately. Changing operation type through
the popup or left/right controls reuses that target when the new operation
supports it, so `Truncate B` changes to `Rectify B` instead of resetting to A.
The URL retains the
concrete `x[kind]`, `k[face]`, `t[vertex]`, or `a[vertex]` tag, so the selected
orbit round-trips.

Truncate vertex and Rectify vertex expose parameterized cut-depth animation
kernels. Adding and removing one uses the same collapsed-topology technique as
the corresponding full operation. Changing the target with the orbit spinner
produces two half-duration keyframes: the old target animates out to the input
mesh, then the new target animates in. Kis face changes are immediate: collapsing
its apex produced unstable triangles, so the core intentionally emits no Kis-face
animation keyframes. Truncate vertex and Rectify vertex share the same selective
cut topology on a common target, so switching between them interpolates the cut
ratio directly in one full-duration step.

## Macros

A macro occupies one logical position in the UI and URL, but the Wasm core expands
it to primitive operations. The evaluator fuses `a -> a` and `a -> t` into the
direct cantellation and bevel geometry kernels. Consequently, expanded chains and
their macro replacements have identical regular geometry without running the
iterative canonicalization algorithm.

The UI can simplify the longest applied-end prefix to one primitive or macro. It
expands macros and cancels adjacent Dual pairs before comparison; for example,
displayed `Dual Needle` is offered as `Truncated`. Replacement is always explicit;
accepting it can expose a fused `aa`/`at` kernel and select its regular coordinate
realization. Snub primes remain attached during expansion, so `d -> s' -> d` is
offered as `Gyro'`, never unprimed Gyro.

### Kis (`k`)

Kis adds a new vertex over the center of every face and connects it to every
boundary vertex. Each original `n`-gon is replaced by `n` triangles. In this
project it is expressed as Dual, Truncated, Dual.

### Join (`j`)

Join is the dual of Rectified/Ambo. It introduces vertices corresponding to both
the original faces and vertices, then places one quadrilateral face across every
original edge. It resembles Kis with the original edges removed.

### Needle (`N`)

Needle is the dual of Truncated. New vertices correspond to the original faces and
vertices, while every original edge contributes two triangular faces. Its count
formula matches Kis, but its incidence structure is different.

### Zip (`z`)

Zip truncates the dual. It places new edges between neighboring original face
regions, as if the surface were zipped across each original edge. It is also
called bitruncation. Its count formula matches Truncated, but the correspondence
to original elements differs.

### Cantellated (`e`)

Cantellation, or expansion, separates every original face from its neighbors.
Faces remain over the original faces and vertices, and a new quadrilateral bridges
each original edge. Topologically it is two Rectified operations; the evaluator
uses the fused cantellation kernel to retain regular geometry.

### Bevelled (`b`)

Bevelling first rectifies and then truncates (`ta` in conventional notation).
It produces faces corresponding to every original face, edge, and vertex, with
four output vertices and six output edges per original edge. The evaluator uses
the fused bevel kernel so the expanded and named forms coincide geometrically.

### Ortho (`O`)

Ortho is the dual of Cantellated and is also equivalent to Join applied twice.
It places vertices corresponding to original faces, edges, and vertices and
subdivides the surface into two quadrilateral or kite-like faces per original
edge.

### Meta (`m`)

Meta is the dual of Bevelled and can also be viewed as Kis after Join. It connects
face-center and edge-derived vertices to the original vertex regions, producing
four triangular faces per original edge.

### Gyro (`g`)

Gyro is the dual of Snub. It uses a consistent handed twist to divide the surface
into two pentagonal faces per original edge. Like Snub, it preserves rotational
symmetry while discarding reflection symmetry and therefore has mirror forms.
The alternate form is written `Gyro'` (`g'`) and expands to `d -> s' -> d`.

## Sources of truth

- Project tags and geometry implementations:
  [`Transform.kt`](../core/src/commonMain/kotlin/transform/Transform.kt) and the
  other files in `core/src/commonMain/kotlin/transform/`.
- Macro expansions: [`TransformMacro.kt`](../model/src/commonMain/kotlin/api/TransformMacro.kt).
- Conway operator terminology and identities: [George W. Hart's Conway Notation
  for Polyhedra](https://www.georgehart.com/virtual-polyhedra/conway_notation.html)
  and [Antiprism's Conway documentation](https://www.antiprism.com/programs/conway.html).
- Propeller geometry and handedness: [George W. Hart's Propellor
  Polyhedra](https://georgehart.com/propello/propello.html).
