# Transformations and macros

Let `F`, `E`, and `V` be the input counts of faces, edges, and vertices, and let
`F'`, `E'`, and `V'` be the corresponding output counts. The formulas below are
topological: they describe a valid closed polyhedron and do not specify its exact
coordinates. Different operations can therefore have the same count formula.

The formulas apply to ordinary topological meshes. Classical Kepler-Poinsot inputs retain their
immersed abstract faces; Dual, Greatened, and Stellated act on that source topology. Resolved instead
constructs a new physical boundary whose counts depend on the actual arrangement. Those counts are
listed in the [seed catalog](seeds.md), and no misleading linear formula is inferred.

Macro expansions are written in project execution order, from first applied to
last applied. Traditional Conway notation is read in the opposite direction; for
example, the project expansion `a -> t` is conventionally written `ta`.

The transform popup is ordered as `Transform`, `Macro`, `Orbit-targeted`, then
`Star`. The final `Star` section contains Greatened, Stellated, and Resolved.

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
| Greatened | Greatening | `G` | - | constellation-dependent | constellation-dependent | constellation-dependent |
| Stellated | Stellation | `S` | - | constellation-dependent | constellation-dependent | constellation-dependent |
| Resolved | Nonzero-winding resolution | `R` | - | arrangement-dependent | arrangement-dependent | arrangement-dependent |
| Drop | Orbit deletion | `x[kind]` | - | input-dependent | input-dependent | input-dependent |
| Kis face | Selective akisation | `k[face]` | - | input-dependent | input-dependent | input-dependent |
| Stellate face | Selective akisation and radial apex movement | `f[face]` | Kis selected faces, then Radial created apexes | input-dependent | input-dependent | input-dependent |
| Truncate vertex | Selective vertex truncation | `t[vertex]` | - | input-dependent | input-dependent | input-dependent |
| Rectify vertex | Selective vertex rectification | `a[vertex]` | - | input-dependent | input-dependent | input-dependent |
| Radial vertex | Radial orbit displacement, spike/facet | `r[vertex]` | - | `F` | `E` | `V` |
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
and `o`. Greatened and Stellated use uppercase `G` and `S` because lowercase `g`
and `s` are the established Gyro and Snub tags. A trailing prime selects alternate chirality: `s'`, `p'`, and `w'`
are flipped Snub, Propeller, and Whirl, while `g' = d -> s' -> d` is flipped
Gyro.

## Continuous parameters

The last transform has a gear button when it has a meaningful coordinate degree
of freedom; earlier pills do not show or open settings. Slider values are
dimensionless percentages of the operation's
regular construction: `100%` is the default, while lower or higher
values move continuously away from it without changing the operation's
topology. Default values are omitted from the URL. Non-default values follow the
operation tag as `~key=value`, for example `t~d=0.7` for 70% truncation depth.
The reset button in the popup's lower-right corner restores all controls to
`100%` and restores the operation's standard chirality.
The bounds shown by a slider are not universal constants: the Wasm worker starts
from a broad exploration envelope, applies the transform to the actual mesh at
that chain position, and narrows it to the connected interval with finite,
non-degenerate, consistently wound geometry. With multiple controls, each range
is recomputed while the other selected values are held fixed. The UI rounds the
limits inward to its 1% step. Unsafe values supplied directly in a URL are
rejected with a warning while the last valid mesh remains displayed.

| Operations | Controls | URL keys |
| --- | --- | --- |
| Truncated, Needle, Zip | Depth | `d` |
| Kis, Kis face | Height | `h` |
| Stellate face, Radial vertex | Radius | `R` |
| Greatened, Stellated | Result | `l` |
| Cantellated, Ortho | Distance | `c` |
| Bevelled, Meta | Distance, depth | `c`, `d` |
| Snub, Gyro | Inset, twist | `i`, `r` |
| Chamfered | Width | `w` |
| Truncate vertex, Rectify vertex | Depth | `d` |

The chiral Snub, Gyro, Propeller, and Whirl settings also contain their chirality
flip while they are the last chain item. Propeller and Whirl have no stable
continuous coordinate control because their preliminary construction is
canonicalized; their gear therefore contains only chirality. Rectified and Join
are also fixed because moving a shared edge midpoint splits it into two vertices
and becomes truncation rather than a coordinate variation of rectification.
Result is a discrete integer setting rather than a percentage. The worker lists the available
main-line candidates for the actual input; the row displays `n of N` and that candidate's F/E/V.
Result `1` is the default and is omitted, while later choices use tags such as `S~l=2`.

Dual, Drop, Quinto, Canonical, Resolved, and None have no continuous geometric setting.

## Geometry domains

Every primitive declares a machine-readable input contract, face requirement, topology
requirement, locality flag, and output policy. An intentional crossing outside a local target does
not disable the operation; the completed result must still satisfy its declared policy. Contract
names and immersion semantics are defined in
[Self-intersecting polyhedra](self-intersections.md#validation-contracts).

| Operations | Additional geometry requirement | Output policy |
| --- | --- | --- |
| None | Oriented source map | Preserve the input contract |
| Truncated, Rectified, Truncate vertex, Rectify vertex | Oriented source map; the target kind must exist for selective forms | Renderable immersion |
| Dual, Cantellated, Bevelled, Snub | Every authoritative face has a planar, non-singular oriented plane | Renderable immersion; Snub preserves a stronger input contract |
| Kis | Every authoritative face is simple and planar | Renderable immersion |
| Kis face | Selected faces are simple and planar | Embedded boundary |
| Chamfered | Every authoritative face is simple and planar | Embedded boundary |
| Drop | Selected neighborhood is a simple planar local disk and closes after removal | Embedded boundary |
| Radial vertex, Stellate face | Selected neighborhood passes the independent triangular-orbit checks | Renderable immersion |
| Propeller, Whirl, Quinto, Canonical | Abstract topology is a canonicalizable sphere | Embedded canonical realization |
| Greatened, Stellated | Planar authoritative faces produce a valid finite face-plane constellation | Renderable immersion |
| Resolved | Valid resolved-face planar arrangement | Embedded boundary |

Macros use the domain of their realized primitive sequence or fused kernel. They cannot use a later
stage to conceal an undefined intermediate. Dynamic parameter ranges run the same output validation
as the selected operation.

## Animations

Transform animation is computed in the Wasm core and returned as one or more
topology-compatible mesh pairs. The renderer interpolates positions, normals,
and face colors on the GPU; it does not rerun a transform per frame. The
Animation / Updates controls enable the behavior and set the duration of one
operation phase. Applying an operation runs its construction forward and
removing it runs the same construction backward. Replacing unrelated operations
uses two full phases: the old operation animates out, then the new one animates
in. A visually stationary phase is omitted and its operation keeps the full
duration; for example, replacing an identity Canonical on a regular seed starts
the new operation immediately. Compatible pairs such as Truncated/Rectified use
one direct phase, and chirality flips remain immediate.

Three constructions are used:

- **Parameterized topology.** Truncate, Rectify, Dual, Cantellate, Bevel, Snub,
  Chamfer, and selective vertex cuts retain the output topology while a ratio
  approaches the input or limiting geometry. A tiny endpoint gap avoids
  evaluating exactly degenerate normals before the renderer switches meshes.
- **Surface construction.** Propeller begins with its new corner faces nearly
  collapsed at the source vertices. Whirl and Quinto lay their output topology
  on the input surface with each new central face nearly collapsed at the source
  face center. The constructions visibly open into their canonical outputs while
  preserving the input surface at the first frame and working under Midradius
  scaling.
- **Coordinate interpolation.** Canonicalization, Radial vertex, and changes to continuous
  settings keep connectivity fixed and interpolate corresponding vertices
  directly.

All components of a multi-part macro share one normalized progress value. The
core constructs one topology-compatible mesh whose component-created vertices
are collapsed onto the input at 0%, and whose coordinates are the exact macro
result at 100%. Apply and removal therefore use one fused morph over one configured
operation duration; removal runs the same morph backward. Cantellated and
Bevelled already use direct single-kernel morphs. Non-default macro settings,
including Kis Height, are included in the same fused target.

Animation is intentionally omitted where no stable, non-self-intersecting mesh
correspondence exists: Drop, adding/removing or retargeting selective Kis face,
Greatened, Stellated, Resolved, and chirality flips. Resolved changes the physical arrangement
topology; the regular-star operations change
the resolved intersection-cell topology, and their classical collapsed forms would be immersed
rather than proper meshes. A selective Kis Height change still interpolates because
its topology is already present. A chirality flip is immediate rather than
passing through a flattened or self-intersecting intermediate mesh.

## Primitive transformations

### None (`n`)

None is an identity operation in the core. In the transform editor it is an
action that removes the selected stage, so it is normally absent from a stored
chain. It changes neither topology nor geometry.

None has no animation of its own. When it removes a stage, the removed
operation's animation is played backward whenever that operation supports one.

### Truncated (`t`)

Truncation cuts off every original vertex. Each original face remains with twice
as many sides, and each original vertex produces one new face. Two output
vertices lie on every original edge, which produces three output edge segments.
The default cut depth is chosen from the representative regular-face geometry;
for a regular star `{n/q}` it uses the actual half-step angle `PI q / n`, not
the convex `PI / n` angle. The Depth setting scales it continuously. A singular
regular snap is rejected while nearby validated depths remain usable.

For animation, the two cut points on each directed edge begin arbitrarily close
to their source vertex and move to the selected cut ratio. The new vertex faces
therefore grow out while the retained faces shorten; removal reverses the cut.
Depth changes interpolate the ratio directly without collapsing the topology.

### Rectified (`a`)

Rectification, or ambo, places one output vertex at every original edge midpoint.
Every original face becomes a face through its edge midpoints, and every original
vertex becomes a face through the midpoints of its incident edges. It is the exact
full-depth quotient of the shared directed-edge truncation construction: the two
directed cut points are identified as one reversal-invariant edge point. A common
midsphere tangency point is used when every edge has one in its segment; otherwise
the Euclidean midpoint is used. Rectified has no independent continuous parameter.

Rectified uses the same cut topology as Truncated and animates its cut points to
the shared edge-midpoint limit. On removal they return toward their source
vertices before the input mesh replaces the near-degenerate keyframe.

### Dual (`d`)

Duality exchanges faces and vertices while preserving a one-to-one correspondence
between edges. The implementation places the polar reciprocal of every authoritative
oriented face plane and builds one dual face around every original vertex. It requires
all source faces to be planar with a nonzero offset from the symmetry center. Applying
Dual twice restores the original topology and normalized coordinates. The same general
construction exchanges the classical Kepler-Poinsot pairs Stellated dodecahedron /
Great dodecahedron and Great stellated dodecahedron / Great icosahedron; catalog
recognition only names the result and resolved intersection cells are never dualized.

On an embedded input, Dual is animated through the limiting cantellation family. Face-, edge-, and
vertex-derived regions move toward the reciprocal face points until the mesh is
visually the dual; removal traverses that limit in reverse. This supplies stable
matching buffers even though the input and dual exchange faces and vertices.
Duality on any immersed input is immediate because its resolved presentation cells cannot pass
through that cantellation family without introducing animation-only intersections.

### Snub (`s`)

Snubbing separates and consistently twists the original faces. It keeps one face
for every original face and vertex, then fills the gap around every original edge
with two triangles. The consistent twist makes Snub chiral: reversing the twist
produces its mirror form. Inset and Twist can be varied independently. The UI
writes this alternate operation as `Snub'` (`s'`).

Snub animation grows the inset and signed twist together from the untwisted
input limit; changing Inset or Twist interpolates those values directly. Removal
collapses the same construction. Switching between `Snub` and `Snub'` is
immediate because interpolating opposite handedness crosses an invalid flattened
mesh.

### Propeller (`p`)

Propeller keeps every original vertex and inserts two points at the thirds of
each original edge. Within every source face, one point from each boundary edge
forms a smaller, consistently rotated copy of that face. A quadrilateral fills
the region at every original face corner. Choosing the opposite rotation gives
the mirror operation, written `Propeller'` (`p'`). After building this incidence
structure, the core finds its canonical convex realization so a later transform
does not encounter the coplanar faces of the literal subdivision.

Propeller starts with each directed edge point very close to its source vertex.
The central polygon therefore retains almost the whole input face while the new
corner quadrilaterals are narrow slivers. They visibly open and twist into the
canonical Propeller coordinates; removal collapses them back before the original
faces replace the construction. The small nonzero opening avoids degenerate face
normals. `Propeller`/`Propeller'` flips remain immediate.

### Whirl (`w`)

Whirl starts with the same original vertices and directed one-third edge points
as Propeller, then adds an inner point for every face-edge incidence. The inner
points form the new central face, and a hexagon fills each original face corner.
Its consistent winding makes it chiral; the reverse winding is `Whirl'` (`w'`).
The returned geometry is the canonical convex realization of this topology.

Whirl starts as a planar subdivision with each inner ring nearly collapsed at
the source-face center. The surrounding corner hexagons retain the input surface,
then the inner ring visibly opens and all vertices move into the canonical Whirl;
removal reverses it. Changing between `Whirl` and `Whirl'` does not interpolate
chirality.

### Quinto (`q`)

Quinto places one midpoint on every original edge and, on each side of that
edge, another point halfway from the midpoint to the source face center. Those
inner points form a central face, while every original face corner becomes a
pentagon. The construction has reflection symmetry, so Quinto has no alternate
chirality. As with Propeller and Whirl, the returned coordinates are canonicalized
to keep subsequent operations geometrically well-defined.

Quinto animation starts with its edge midpoints on the source edges and its inner
points near the source-face centers. The central face is therefore almost
collapsed while the surrounding corner pentagons retain the input surface. That
central face visibly opens as the vertices move to canonical Quinto coordinates;
removal runs the same motion backward.

### Chamfered (`c`)

Chamfering moves the boundary of each original face inward and inserts one
hexagonal face along every original edge. Original vertices remain, and two new
vertices are introduced for every original edge. The geometry uses
bisector planes and stops when the limiting new edges reach the regular target
length. The Width setting scales the regular limiting distance.

Chamfer animation starts with the new edge faces collapsed along their source
edges while the retained vertices coincide with the input. Increasing the
chamfer ratio opens those faces to the configured Width; removal closes them,
and Width changes interpolate the ratio directly.

### Canonical (`o`)

Canonical changes coordinates but not connectivity. The background
[canonicalization algorithm](canonicalization.md) iteratively seeks the canonical
representation in which faces are planar and all edges are tangent to a common
midsphere. The result is unique only up to rotation and reflection.

Canonical preserves connectivity, so animation directly interpolates every
vertex from the current realization to the canonical coordinates. Removing a
Canonical stage performs the same coordinate morph backward.

### Drop (`x[kind]`)

Drop is a topology-dependent operation on one face, edge, or vertex rotation
orbit. It expands the requested deletion to adjacent elements that would
otherwise become degenerate, verifies that the merged boundary is one valid face,
and rebuilds the remaining mesh. Because the selected orbit and necessary cleanup
vary with the input, Drop has no single linear `F/E/V` formula.

Drop is immediate. Deleting an orbit can recursively delete vertices and edges
and merge several source faces into one boundary, so there is no general
one-to-one face/vertex correspondence suitable for the renderer's mesh morph.

### Kis face (`k[face]`)

Kis face raises an apex over every face in one selected face orbit and replaces
each selected `n`-gon with `n` triangles. Other face orbits retain their original
topology. The apex and retained-vertex coordinates come from the same geometry
construction as the full Kis macro; selecting every face orbit in the core
operation is therefore exactly equivalent to Kis. The operation is offered only
when the input has more than one face orbit and is displayed as `Kis α`, `Kis β`,
and so on. Height moves the generated apex along the line from the source face
center to its regular Kis position.

Once selective Kis exists, Height changes animate the apex coordinates directly.
Adding, removing, or changing its target orbit is immediate: collapsing only
some apexes leaves unstable zero-area triangles and cannot define a valid
interpolation. This limitation does not apply to the full Kis macro, whose
Dual/Truncated/Dual expansion uses stable primitive limiting meshes.

### Stellate face (`f[face]`)

Stellate face is a face-targeted macro: it applies Kis face to one source face
orbit, then moves the resulting apex vertex orbit radially. It is offered only
when the created apex orbit is independent, is surrounded exclusively by simple
planar triangles, and is not adjacent to another vertex of its own orbit. This
also excludes targets whose apex would touch a self-intersecting or non-planar
source face. The source-face-to-apex mapping is retained explicitly rather than
inferred from orbit numbering.

Radius `1` is ordinary Kis geometry. Values above `1` move the apexes farther
from the origin; positive values below `1` move them inward and can form facets,
dents, and immersed star surfaces. The worker computes the safe connected range
for the actual input. It also derives every radius in that range where a new
triangular facet becomes coplanar with another face of the result. The compact
left/right controls step precisely to the preceding or following such geometric
landmark; they are derived from the current mesh and have no catalog-specific
values. On a dodecahedron, two of these landmarks resolve to the same physical
geometries as the Stellated dodecahedron and Great stellated dodecahedron seeds.
The authoritative triangular faces remain different from the immersed star-face
seeds, so no catalog replacement is proposed before Resolved is applied.

Applying or removing Stellate face animates one stable output topology: the new
apexes start at their source-face centers and move to the requested radial
positions. Radius changes interpolate the existing apex coordinates directly.

### Truncate vertex (`t[vertex]`)

Truncate vertex cuts off every vertex in one selected vertex orbit. Each selected
vertex becomes a new face, while unselected vertices remain. Cut points use the
same truncation ratio and edge interpolation as full Truncated; selecting every
vertex orbit in the core operation is therefore exactly equivalent to Truncated.
The operation is offered only when the input has more than one vertex orbit and
is displayed as `Truncate A`, `Truncate B`, and so on. Depth scales the regular
cut position.

Truncate vertex uses the full truncation ratio kernel restricted to the selected
vertex orbit. Adding grows its new faces from the selected source vertices and
removal collapses them. Changing target orbit animates the old target out and
the new target in as two full operation phases.

### Rectify vertex (`a[vertex]`)

Rectify vertex moves the edges incident to one selected vertex orbit to their
midpoints. Every selected vertex becomes a new face, while unselected vertices
remain. When both ends of an edge are selected, they share one midpoint rather
than producing coincident duplicate vertices. Selecting every vertex orbit is
therefore exactly equivalent to full Rectified. The operation is offered only
when the input has more than one vertex orbit and is displayed as `Rectify A`,
`Rectify B`, and so on. Depth explores shallower cuts up to the midpoint limit.

Rectify vertex animates the selected orbit through the same selective cut
topology until shared cut points reach the midpoint limit. Adding and removal
run that ratio forward or backward; changing target orbit uses the same
old-target-out/new-target-in sequence as Truncate vertex.

### Radial vertex (`r[vertex]`)

Radial vertex multiplies the position of every vertex in one selected rotation
orbit by a positive Radius while preserving all faces, edges, and vertex IDs.
Radius `1` is the identity; larger values form outward spikes and smaller values
form inward facets or dents. It is offered only for an independent vertex orbit
whose incident faces are simple planar triangles. In particular, vertices next
to self-crossing or non-planar faces are excluded. The worker derives a safe
connected Radius interval from the entering geometry.

Because connectivity is unchanged, applying, removing, retargeting on a
compatible topology, and changing Radius use direct vertex interpolation.

The transform popup presents valid selective operations in its
`Orbit-targeted` section, immediately before `Star`. Entries use one global order: Drop face, Drop edge, Drop
vertex, Kis face, Stellate face, Truncate vertex, Rectify vertex, Radial vertex. The F/E/V orbit rows show every
operation currently available for that exact orbit at the right edge in the same
order, using × for Drop, an upward caret for Kis face, a star for Stellate face,
scissors for Truncate vertex, compress for Rectify vertex, and vertical arrows
for Radial vertex; hover tooltips name the operation and
target orbit. Choosing one stores its first concrete target. When an orbit-targeted
operation is the last chain item, its up/down controls cycle through all currently
valid targets of that same operation and wrap at both ends. The UI remembers the
last face, edge, and vertex target separately. Changing operation type through
the popup or left/right controls reuses that target when the new operation
supports it, so `Truncate B` changes to `Rectify B` instead of resetting to A.
The URL retains the
concrete `x[kind]`, `k[face]`, `f[face]`, `t[vertex]`, `a[vertex]`, or `r[vertex]` tag, so the selected
orbit round-trips.

Truncate vertex and Rectify vertex share one selective cut topology on a common
target, so switching between them interpolates the cut ratio directly in one
full-duration step instead of collapsing and rebuilding the orbit.

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

Kis animates Dual, Truncated, and Dual on one shared 0–100% clock. At 0% its
retained vertices coincide with the input vertices and each new apex is collapsed
to its source-face boundary; at 100% the mesh has the exact Kis coordinates.
Removal reverses that one fused morph. A non-default Height changes the same
100% target without adding another animation stage.

### Join (`j`)

Join is the dual of Rectified/Ambo. It introduces vertices corresponding to both
the original faces and vertices, then places one quadrilateral face across every
original edge. It resembles Kis with the original edges removed.

Join animates `Dual -> Rectified -> Dual` as one fused morph: all three component
percentages advance together, and removal runs that morph backward.

### Needle (`N`)

Needle is the dual of Truncated. New vertices correspond to the original faces and
vertices, while every original edge contributes two triangular faces. Its count
formula matches Kis, but its incidence structure is different.

Needle advances Truncated and Dual together on one 0–100% clock. Removal reverses
the same fused morph.

### Zip (`z`)

Zip truncates the dual. It places new edges between neighboring original face
regions, as if the surface were zipped across each original edge. It is also
called bitruncation. Its count formula matches Truncated, but the correspondence
to original elements differs.

Zip uses a direct fused cut topology: Dual and Truncated advance together while
its final cut vertices move from their corresponding source vertices to the
zipped positions. This avoids exposing the intermediate dual mesh. Its Depth
setting changes the same topology directly.

### Cantellated (`e`)

Cantellation, or expansion, separates every original face from its neighbors.
Faces remain over the original faces and vertices, and a new quadrilateral bridges
each original edge. Topologically it is two Rectified operations; the evaluator
uses the fused cantellation kernel to retain regular geometry.

Cantellated uses one fused cantellation morph rather than visibly stopping at
the intermediate Rectified mesh. Its Distance ratio opens or closes the original
face, edge, and vertex regions continuously on apply, removal, and setting edits.

### Bevelled (`b`)

Bevelling first rectifies and then truncates (`ta` in conventional notation).
It produces faces corresponding to every original face, edge, and vertex, with
four output vertices and six output edges per original edge. The evaluator uses
the fused bevel kernel so the expanded and named forms coincide geometrically.

Bevelled uses one fused two-parameter bevel morph. Distance separates the face
regions and Depth cuts their corners in the same animation; removal returns both
ratios toward the input limit, and setting edits interpolate them directly.

### Ortho (`O`)

Ortho is the dual of Cantellated and is also equivalent to Join applied twice.
It places vertices corresponding to original faces, edges, and vertices and
subdivides the surface into two quadrilateral or kite-like faces per original
edge.

Ortho advances `Dual -> Cantellated -> Dual` together on one shared 0–100% clock.
Removal reverses the same fused morph.

### Meta (`m`)

Meta is the dual of Bevelled and can also be viewed as Kis after Join. It connects
face-center and edge-derived vertices to the original vertex regions, producing
four triangular faces per original edge.

Meta advances `Dual -> Bevelled -> Dual` together on one shared 0–100% clock.
Removal reverses the same fused morph; Distance and Depth are included in its
100% target coordinates.

### Gyro (`g`)

Gyro is the dual of Snub. It uses a consistent handed twist to divide the surface
into two pentagonal faces per original edge. Like Snub, it preserves rotational
symmetry while discarding reflection symmetry and therefore has mirror forms.
The alternate form is written `Gyro'` (`g'`) and expands to `d -> s' -> d`.

Gyro advances `Dual -> Snub -> Dual` together on one shared 0–100% clock while
preserving the selected handedness. Removal reverses the same fused morph. Inset
and Twist are included in its 100% target coordinates. `Gyro`/`Gyro'` flips are
immediate to avoid passing through the invalid opposite-twist intermediate.

## Star transformations

Greatened, Stellated, and Resolved are primitive transforms grouped in the final Star popup section.
The first two derive candidates from the input geometry but use distinct constructions described
below.

### Greatened (`G`)

Greatening is implemented as generic symmetric faceting of the polar dual. Source faces become
dual vertices; candidate planes through those vertices supply convex and regular-star circuits.
The full geometric point group expands each circuit into a face orbit, and an exact-cover search
combines orbit sets that use every dual vertex and every edge exactly twice. Each connected valid
faceting is reciprocated and aligned back to the source planes. There is no catalog-specific
construction or fallback; catalog recognition runs only on the completed result.

Every result therefore keeps exactly one authoritative face on every source face plane, so `F`
is unchanged, while its face boundary, `E`, and `V` may change. Generic ordering first minimizes
changes to the source faces' side counts and winding steps, then total cyclic changes and circuit
radius. This geometry-only ordering makes the classical result the default without naming it in
the algorithm. It produces the classical identities:

| Input | Output |
| --- | --- |
| Dodecahedron | Great dodecahedron |
| Icosahedron | Great icosahedron |
| Stellated dodecahedron | Great stellated dodecahedron |

The same construction applies to non-catalog and mixed-face inputs; for example, Cuboctahedron has
three valid Greatened results even though its triangular and square face planes cannot share one
uniform regular circuit. Candidate data is cached by a circumradius-normalized plane, boundary,
and kind signature. If several strict extensions survive, the Result setting selects them in the
generic order above. A missing or out-of-range result reports
`TransformNotApplicable` rather than substituting a catalog mesh.

Greatening and Stellated are related but distinct. Greatening facets the polar dual and preserves
one face per source plane. Stellated fills a complete cell-power stratum of the source plane
arrangement; a source plane may contribute several output faces, so its face count commonly grows.
Distinct nonzero-winding presentation cells inside one self-crossing Greatened face remain derived
geometry and do not create additional authoritative face orbits. Applying Resolved promotes the
embedded physical boundary and classifies its completed faces into their own proper-rotation
orbits.
For Dodecahedron both operations discover the same three classical geometries, but Greatened starts
with Great dodecahedron while Stellated starts with Stellated dodecahedron. For Cuboctahedron their
candidate sets are disjoint. Greatened therefore adds useful face-preserving extensions rather than
being an alias for Stellated.

Result 1 is omitted from serialization. Later results use `G~l=n`, display `n` as an HTML subscript
on the Greatened pill, and use `_n` in generated export filenames. The popup's compact left/right
controls select the previous or next greatening and keep the Result slider synchronized.
Uncached Result discovery reports its faceting-plane, symmetry-orbit, exact-cover, and candidate
filtering phases on the Greatened pill. A candidate must have integer generalized winding at the
origin, as every closed oriented immersion does at an off-surface point. This inexpensive generic
closure test avoids resolving every unselected candidate. Geometry-contract analysis, symmetry,
orbit actions, and presentation rims are computed once when a result is selected and retained in
its cache record. The test suite independently resolves every published result for representative
large candidate sets. Cached discovery completes immediately.

Greatened is immediate because its changed immersed topology has no stable collapsed correspondence
to the input surface.

### Stellated (`S`)

Stellation constructs the bounded arrangement of all authoritative source-face planes. Each
arrangement cell is identified by the set of source planes crossed from the convex core, so its
cell power is its exact graph distance from that core. The main line adds complete successive
power strata. It reconstructs a connected immersed source surface when the plane-diagram
boundaries form one, and otherwise retains the proper physical stratum boundary. Results are
ordered from the closest supported stratum outwards.

The candidate search is entirely geometry-based: it does not inspect seed tags, catalog names, or
stored catalog meshes. Disconnected source surfaces (compounds), open incidence, collapsed edges,
non-planar sources, and invalid immersed surfaces are discarded. In particular, the icosahedron's
`C` stratum is the compound of five octahedra and is not offered. Its supported main line therefore
contains `B`, `D`, `E`, `F`, `G`, and `H`; `G` is recognized afterward as the Great icosahedron.
Each surviving candidate derives its face and vertex kinds from the geometric automorphism orbits
of the generated surface; edge kinds follow from their endpoint and incident-face kinds. One source
face plane can therefore contribute several distinct face orbits in a later stratum, and those
orbits remain separately selectable and colored in the F/E/V popup.
The classical dodecahedral results are:

| Input | Output |
| --- | --- |
| Dodecahedron | Stellated dodecahedron |
| Great dodecahedron | Great stellated dodecahedron |

The dodecahedron's three outward strata are Stellated dodecahedron, Great dodecahedron, and Great
stellated dodecahedron in that order. Consequently, both `D -> G -> S` and `D -> S -> G` reach the
great stellated dodecahedron. A qualifying non-catalog input is transformed directly from its own
planes. The Result gear enumerates only surviving supported strata; Result 1 is the closest and is
omitted from serialization, while later results use `S~l=n`. A non-first result is shown as an HTML
subscript on the Stellated transform pill and as `_n` in generated export filenames. The slider's
compact tap controls share the popup's bottom action row with Reset, select the previous or next
available result, and keep the slider position synchronized.

Uncached Result discovery reports its plane-diagram, arrangement-circuit, and successive-stratum
phases on the Stellated pill. Plane diagrams are computed once per face orbit and expanded by the
polyhedron's geometric symmetries. Spatial point indices merge arrangement intersections without
quadratic scans, and physical stratum boundaries are constructed only when reconstruction or
geometric matching needs them. Cached discovery completes immediately.
The bounded candidate cache owns each result's geometry-contract analysis, full point-group and
F/E/V orbit classification, complete set of core-derived orbit-targeted action tags, and most
recent presentation-rim geometry. A Result change reuses those records and skips evaluation
of the previous Result because no animation can be produced. Stellate-face eligibility is derived
for every face orbit from one shared full-Kis construction rather than rebuilding selective Kis
once per orbit.

Stellated is immediate for the same reason as Greatened: no connected, non-degenerate,
topology-compatible interpolation has the required endpoint topology.

### Resolved (`R`)

Resolved turns a renderable immersion into the embedded boundary of its nonzero-winding material.
It corefines the supplied face triangles only at actual surface intersections, keeps fragments
that separate zero from nonzero winding, joins conforming fragments into a manifold, and safely
merges coplanar triangles into polygons. The result has new arrangement-dependent F/E/V counts,
actual rotation-orbit kinds, and many-to-many provenance back to the source surface.

Resolved is an identity on an already embedded surface and keeps its explicit chain item, where the
recycle action can remove it. An immersed seed or transformed result shows a pentagram action on
the pill that owns the current geometry status; activating it appends `R`. Resolved is immediate
because its physical topology cannot be collapsed onto the immersed input without intersections.

## Sources of truth

- Project tags and geometry implementations:
  [`Transform.kt`](../core/src/commonMain/kotlin/transform/Transform.kt) and the
  other files in `core/src/commonMain/kotlin/transform/`.
- Macro expansions: [`TransformMacro.kt`](../model/src/commonMain/kotlin/api/TransformMacro.kt).
- Conway operator terminology and identities: [George W. Hart's Conway Notation
  for Polyhedra](https://www.georgehart.com/virtual-polyhedra/conway_notation.html)
  and [Antiprism's Conway documentation](https://www.antiprism.com/programs/conway.html).
- Regular star polyhedra and stellation relationships: [George W. Hart's
  Kepler-Poinsot Polyhedra](https://www.georgehart.com/virtual-polyhedra/kepler-poinsot-info.html)
  and [Stellations](https://www.georgehart.com/virtual-polyhedra/stellations-info.html).
- Propeller geometry and handedness: [George W. Hart's Propellor
  Polyhedra](https://georgehart.com/propello/propello.html).
