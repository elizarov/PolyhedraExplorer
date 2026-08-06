# Seed catalog

Polyhedra Explorer contains the complete classical catalog of 31 convex seed
types: 5 Platonic, 13 Archimedean, and 13 Catalan solids. The two chiral
Archimedean solids and their Catalan duals also have prime-tagged mirror forms,
so the core recognizes 35 concrete seed representations.

## Categories

### Platonic solids

A Platonic solid is a convex regular polyhedron: every face is the same regular
polygon, and the same arrangement of faces meets at every vertex. Equivalently,
its symmetry group is transitive on faces, edges, and vertices. Exactly five
exist.

### Archimedean solids

An Archimedean solid is a convex, vertex-transitive polyhedron whose faces are
regular polygons of a common edge length, with at least two polygon types. This
is the classical set of 13, excluding the Platonic solids and the infinite prism
and antiprism families. The snub cube and snub dodecahedron are chiral and each
has two mirror realizations.

### Catalan solids

A Catalan solid is the dual of an Archimedean solid. Its faces are congruent and
the symmetry group is face-transitive, but the faces are generally not regular
polygons and its vertices are not all equivalent. There are 13 types. The duals
of the two snub solids are chiral and also have two mirror realizations.

## Reading the catalog

- Counts and orbit counts use UI order: faces, edges, vertices (`F / E / V`).
- Orbit counts are the project's proper-rotation orbits. Reflections are not
  used, so an achiral solid can have two mirror-related rotational orbits that
  its full symmetry group would merge.
- `T_d`, `O_h`, and `I_h` are the full tetrahedral, octahedral, and icosahedral
  point groups, of orders 24, 48, and 120. `O` and `I` are the chiral rotational
  octahedral and icosahedral groups, of orders 24 and 60.
- A recipe `Cube + t` means: select Cube, then apply Truncated (`t`). Names and
  tags link to the complete [transformation reference](transformations.md).
- A slash in a chiral recipe gives the unprimed/prime operation respectively.
  For example, `C + s/s'` builds `sC/sC'`. Prime marks distinguish the two
  project representations; they do not assign universal *laevo* or *dextro*
  labels, which can reverse under duality.
- “Canonical recipe” below means the preferred concise catalog construction,
  not the iterative Canonical transformation (`o`).

## Catalog summary

| Class | Seed (tag) | Common alternative names | Dual | `F / E / V` | Symmetry | `F / E / V` orbits | Canonical building recipe |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Platonic | Tetrahedron (`T`) | Regular triangular pyramid | Tetrahedron | `4 / 6 / 4` | `T_d` | `1 / 1 / 1` | Base seed |
| Platonic | Cube (`C`) | Regular hexahedron | Octahedron | `6 / 12 / 8` | `O_h` | `1 / 1 / 1` | Base seed |
| Platonic | Octahedron (`O`) | — | Cube | `8 / 12 / 6` | `O_h` | `1 / 1 / 1` | `C + d` |
| Platonic | Dodecahedron (`D`) | — | Icosahedron | `12 / 30 / 20` | `I_h` | `1 / 1 / 1` | `I + d` |
| Platonic | Icosahedron (`I`) | — | Dodecahedron | `20 / 30 / 12` | `I_h` | `1 / 1 / 1` | Base seed; `T + s` |
| Archimedean | Truncated tetrahedron (`tT`) | — | Triakis tetrahedron | `8 / 18 / 12` | `T_d` | `2 / 2 / 1` | `T + t` |
| Archimedean | Cuboctahedron (`aC`) | — | Rhombic dodecahedron | `14 / 24 / 12` | `O_h` | `2 / 1 / 1` | (`C` or `O`) + `a` |
| Archimedean | Truncated cube (`tC`) | — | Triakis octahedron | `14 / 36 / 24` | `O_h` | `2 / 2 / 1` | `C + t` |
| Archimedean | Truncated octahedron (`tO`) | — | Tetrakis hexahedron | `14 / 36 / 24` | `O_h` | `2 / 2 / 1` | `O + t` |
| Archimedean | Rhombicuboctahedron (`eC`) | Small rhombicuboctahedron | Deltoidal icositetrahedron | `26 / 48 / 24` | `O_h` | `3 / 2 / 1` | (`C` or `O`) + `e` |
| Archimedean | Rhombitruncated cuboctahedron (`bC`) | Truncated cuboctahedron; great rhombicuboctahedron | Disdyakis dodecahedron | `26 / 72 / 48` | `O_h` | `3 / 3 / 2` | (`C` or `O`) + `b` |
| Archimedean | Snub cube (`sC`, `sC'`) | — | Pentagonal icositetrahedron | `38 / 60 / 24` | `O` | `3 / 3 / 1` | `C + s/s'`; `O + s'/s` |
| Archimedean | Icosidodecahedron (`aD`) | — | Rhombic triacontahedron | `32 / 60 / 30` | `I_h` | `2 / 1 / 1` | (`D` or `I`) + `a` |
| Archimedean | Truncated dodecahedron (`tD`) | — | Triakis icosahedron | `32 / 90 / 60` | `I_h` | `2 / 2 / 1` | `D + t` |
| Archimedean | Truncated icosahedron (`tI`) | Soccer-ball solid; buckyball shape | Pentakis dodecahedron | `32 / 90 / 60` | `I_h` | `2 / 2 / 1` | `I + t` |
| Archimedean | Rhombicosidodecahedron (`eD`) | Small rhombicosidodecahedron | Deltoidal hexecontahedron | `62 / 120 / 60` | `I_h` | `3 / 2 / 1` | (`D` or `I`) + `e` |
| Archimedean | Rhombitruncated icosidodecahedron (`bD`) | Truncated icosidodecahedron; great rhombicosidodecahedron | Disdyakis triacontahedron | `62 / 180 / 120` | `I_h` | `3 / 3 / 2` | (`D` or `I`) + `b` |
| Archimedean | Snub dodecahedron (`sD`, `sD'`) | — | Pentagonal hexecontahedron | `92 / 150 / 60` | `I` | `3 / 3 / 1` | `D + s/s'`; `I + s'/s` |
| Catalan | Triakis tetrahedron (`dtT`) | Tristetrahedron | Truncated tetrahedron | `12 / 18 / 8` | `T_d` | `1 / 2 / 2` | `T + Needle (N)` |
| Catalan | Rhombic dodecahedron (`daC`) | — | Cuboctahedron | `12 / 24 / 14` | `O_h` | `1 / 1 / 2` | (`C` or `O`) + Join (`j`) |
| Catalan | Triakis octahedron (`dtC`) | Small triakis octahedron; trisoctahedron | Truncated cube | `24 / 36 / 14` | `O_h` | `1 / 2 / 2` | `C + Needle (N)`; `O + Kis (k)` |
| Catalan | Tetrakis hexahedron (`dtO`) | Tetrahexahedron | Truncated octahedron | `24 / 36 / 14` | `O_h` | `1 / 2 / 2` | `O + Needle (N)`; `C + Kis (k)` |
| Catalan | Deltoidal icositetrahedron (`deC`) | Trapezoidal icositetrahedron; strombic icositetrahedron | Rhombicuboctahedron | `24 / 48 / 26` | `O_h` | `1 / 2 / 3` | (`C` or `O`) + Ortho (`O`) |
| Catalan | Disdyakis dodecahedron (`dbC`) | Hexakis octahedron; hexoctahedron | Rhombitruncated cuboctahedron | `48 / 72 / 26` | `O_h` | `2 / 3 / 3` | (`C` or `O`) + Meta (`m`) |
| Catalan | Pentagonal icositetrahedron (`dsC`, `dsC'`) | — | Snub cube | `24 / 60 / 38` | `O` | `1 / 3 / 3` | `C + g'/g`; `O + g/g'` |
| Catalan | Rhombic triacontahedron (`daD`) | — | Icosidodecahedron | `30 / 60 / 32` | `I_h` | `1 / 1 / 2` | (`D` or `I`) + Join (`j`) |
| Catalan | Triakis icosahedron (`dtD`) | — | Truncated dodecahedron | `60 / 90 / 32` | `I_h` | `1 / 2 / 2` | `D + Needle (N)`; `I + Kis (k)` |
| Catalan | Pentakis dodecahedron (`dtI`) | — | Truncated icosahedron | `60 / 90 / 32` | `I_h` | `1 / 2 / 2` | `I + Needle (N)`; `D + Kis (k)` |
| Catalan | Deltoidal hexecontahedron (`deD`) | Trapezoidal hexecontahedron; strombic hexecontahedron | Rhombicosidodecahedron | `60 / 120 / 62` | `I_h` | `1 / 2 / 3` | (`D` or `I`) + Ortho (`O`) |
| Catalan | Disdyakis triacontahedron (`dbD`) | Hexakis icosahedron | Rhombitruncated icosidodecahedron | `120 / 180 / 62` | `I_h` | `2 / 3 / 3` | (`D` or `I`) + Meta (`m`) |
| Catalan | Pentagonal hexecontahedron (`dsD`, `dsD'`) | — | Snub dodecahedron | `60 / 150 / 92` | `I` | `1 / 3 / 3` | `D + g'/g`; `I + g/g'` |

In rows such as (`C` or `O`) + `a`, the operation applies to either named seed.

## Completeness and validation

- The catalog contains 31 unique base tags: exactly 5 Platonic, 13 Archimedean,
  and 13 Catalan types. This matches the complete classical enumerations.
- The 13 Catalan rows pair one-to-one with the 13 Archimedean rows. Every pair
  has the same `E`, exchanged `F` and `V`, the same symmetry family, and exchanged
  face/vertex orbit counts, as duality requires.
- Every row satisfies Euler's formula `F - E + V = 2`.
- The four chiral types add `sC'`, `sD'`, `dsC'`, and `dsD'` to the core without
  adding new solid types. Thus the UI lists 31 types while the core recognizes 35
  handed representations.
- `F / E / V` and orbit counts were read from each current `Seed.poly`; the core
  validation suite independently checks the stored counts against each generated
  mesh. All 57 recipe variants represented in the table were also evaluated by
  the Wasm core and recognized as the stated seed, including prime chirality.
- No Platonic, classical Archimedean, or Catalan solid is missing. Prisms,
  antiprisms, Johnson solids, and nonconvex uniform or Kepler-Poinsot solids are
  different classes and are intentionally outside this catalog.

## Sources of truth

- Project seed definitions and geometry: [`Seed.kt`](../core/src/commonMain/kotlin/poly/Seed.kt).
- UI seed types and prime variants: [`CoreOptions.kt`](../web/src/jsMain/kotlin/catalog/CoreOptions.kt).
- Operations used by recipes: [Transformations and macros](transformations.md).
- [Wolfram MathWorld: Platonic Solid](https://mathworld.wolfram.com/PlatonicSolid.html).
- [Wolfram MathWorld: Archimedean Solid](https://mathworld.wolfram.com/ArchimedeanSolid.html).
- [Wolfram MathWorld: Archimedean Dual](https://mathworld.wolfram.com/ArchimedeanDual.html).
- [George W. Hart: Archimedean Duals](https://georgehart.com/virtual-polyhedra/archimedean-duals-info.html).
