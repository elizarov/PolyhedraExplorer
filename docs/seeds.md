# Seed catalog

Polyhedra Explorer contains the complete classical catalog of 31 convex fixed
seed types: 5 Platonic, 13 Archimedean, and 13 Catalan solids. It also provides
four parameterized families—Prism, Antiprism, Pyramid, and Bipyramid—for every
`n` from 3 through 100. The two chiral Archimedean solids and their Catalan duals
also have prime-tagged mirror forms.

## Categories

### Platonic solids

A Platonic solid is a convex regular polyhedron: every face is the same regular
polygon, and the same arrangement of faces meets at every vertex. Equivalently,
its symmetry group is transitive on faces, edges, and vertices. Exactly five
exist.

### Families

A family is an infinite polyhedron class represented here by the finite range
`3 <= n <= 100`, where `n` is the order of the base or equatorial polygon.
Prisms and antiprisms use uniform coordinates; pyramids place a regular base and
centered apex on one sphere with centroid at the origin; bipyramids use a regular
equator and opposite spherical poles. Popup entries show only the family name.
The remembered size starts at `n = 3`; up/down controls beside the selected seed
change it without changing family. Popup selection and left/right seed navigation
carry the remembered `n` to another family and retain it while navigating through
fixed seeds, including with a transform chain. Removing transforms does not clear
the memory; the delete/reset control clears it to 3 only when invoked with no
transforms. Some low-order members coincide with fixed seeds and receive optional
replacement suggestions: Prism 4 is Cube, Antiprism 3 and Bipyramid 4 are
Octahedron, and Pyramid 3 is Tetrahedron. Those coincident members display the
stronger polyhedral symmetry and its actual orbit counts rather than the
family's normal axial point group.

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
- Point groups use the full Schoenflies notation shown in the UI. Subscripts
  encode orientation-reversing symmetry: `C_nv` has vertical mirrors, `D_nh`
  has vertical and horizontal mirrors, and `D_nd` has diagonal mirrors.
  `T_d`, `O_h`, and `I_h` are the full tetrahedral, octahedral, and icosahedral
  point groups. Bare `T`, `O`, and `I` are chiral and have no reflection
  planes. See [Symmetries](symmetries.md).
- A construction `Cube + t` means: select Cube, then apply Truncated (`t`); an
  arrow separates consecutive primitive operations. Names and tags link to the
  complete [transformation reference](transformations.md).
- A slash in a chiral construction gives the unprimed/prime operation respectively.
  For example, `C + s/s'` builds `sC/sC'`. Prime marks distinguish the two
  project representations; they do not assign universal *laevo* or *dextro*
  labels, which can reverse under duality.

## Primitive construction graph

Every fixed seed can be reached from one basic seed—Tetrahedron—using only the
primitive Rectified, Truncated, Dual, and Snub operations. Labels containing two
operations are literal sequences, not macro names. The Archimedean and Catalan
columns are paired by bidirectional Dual edges: applying Dual in either direction
crosses between a solid and its dual. A slash on a chiral node or edge covers
both tested handed forms. The core's composition-aware evaluation gives the
adjacent `Rectified → Rectified` and `Rectified → Truncated` sequences their
regular uniform geometry while retaining the explicit primitive operations.

```mermaid
flowchart LR
    subgraph P["Platonic foundation"]
        direction TB
        T["Tetrahedron (T)"]:::basic
        O["Octahedron (O)"]:::platonic
        C["Cube (C)"]:::platonic
        I["Icosahedron (I)"]:::platonic
        D["Dodecahedron (D)"]:::platonic
        T -->|Rectified| O
        O -->|Dual| C
        T -->|Snub| I
        I -->|Dual| D
    end

    subgraph A["Archimedean"]
        direction TB
        tT["Truncated tetrahedron"]:::archimedean
        aC["Cuboctahedron"]:::archimedean
        tC["Truncated cube"]:::archimedean
        tO["Truncated octahedron"]:::archimedean
        eC["Rhombicuboctahedron"]:::archimedean
        bC["Rhombitruncated cuboctahedron"]:::archimedean
        sC["Snub cube / Snub cube′"]:::chiral
        aD["Icosidodecahedron"]:::archimedean
        tD["Truncated dodecahedron"]:::archimedean
        tI["Truncated icosahedron"]:::archimedean
        eD["Rhombicosidodecahedron"]:::archimedean
        bD["Rhombitruncated icosidodecahedron"]:::archimedean
        sD["Snub dodecahedron / Snub dodecahedron′"]:::chiral
    end

    subgraph K["Catalan"]
        direction TB
        dtT["Triakis tetrahedron"]:::catalan
        daC["Rhombic dodecahedron"]:::catalan
        dtC["Triakis octahedron"]:::catalan
        dtO["Tetrakis hexahedron"]:::catalan
        deC["Deltoidal icositetrahedron"]:::catalan
        dbC["Disdyakis dodecahedron"]:::catalan
        dsC["Pentagonal icositetrahedron / prime"]:::chiralDual
        daD["Rhombic triacontahedron"]:::catalan
        dtD["Triakis icosahedron"]:::catalan
        dtI["Pentakis dodecahedron"]:::catalan
        deD["Deltoidal hexecontahedron"]:::catalan
        dbD["Disdyakis triacontahedron"]:::catalan
        dsD["Pentagonal hexecontahedron / prime"]:::chiralDual
    end

    T -->|Truncated| tT
    C -->|Rectified| aC
    C -->|Truncated| tC
    O -->|Truncated| tO
    C -->|"Rectified → Rectified"| eC
    C -->|"Rectified → Truncated"| bC
    C -->|"Snub / Snub′"| sC
    D -->|Rectified| aD
    D -->|Truncated| tD
    I -->|Truncated| tI
    D -->|"Rectified → Rectified"| eD
    D -->|"Rectified → Truncated"| bD
    D -->|"Snub / Snub′"| sD

    tT <-->|Dual| dtT
    aC <-->|Dual| daC
    tC <-->|Dual| dtC
    tO <-->|Dual| dtO
    eC <-->|Dual| deC
    bC <-->|Dual| dbC
    sC <-->|Dual| dsC
    aD <-->|Dual| daD
    tD <-->|Dual| dtD
    tI <-->|Dual| dtI
    eD <-->|Dual| deD
    bD <-->|Dual| dbD
    sD <-->|Dual| dsD

    classDef basic fill:#ffe082,stroke:#9a6700,stroke-width:3px,color:#222
    classDef platonic fill:#fff3cd,stroke:#9a6700,color:#222
    classDef archimedean fill:#dbeafe,stroke:#2563eb,color:#172554
    classDef catalan fill:#ede9fe,stroke:#7c3aed,color:#2e1065
    classDef chiral fill:#cffafe,stroke:#0891b2,color:#164e63
    classDef chiralDual fill:#fae8ff,stroke:#a21caf,color:#4a044e
```

The construction test executes every arrow above through the core, including
both directions of every Dual edge and both chiralities. It also proves that all
fixed catalog tags are reachable from `T`, constructs each one along that full
path, and rejects macro tags in the edge list.

## Catalog summary

### Platonic

| Seed (tag) | Common alternative names | Dual | `F / E / V` | Full point group | `F / E / V` orbits | Primitive construction |
| --- | --- | --- | --- | --- | --- | --- |
| Tetrahedron (`T`) | Regular triangular pyramid | Tetrahedron | `4 / 6 / 4` | `T_d` | `1 / 1 / 1` | Basic seed |
| Cube (`C`) | Regular hexahedron | Octahedron | `6 / 12 / 8` | `O_h` | `1 / 1 / 1` | `O + d` |
| Octahedron (`O`) | — | Cube | `8 / 12 / 6` | `O_h` | `1 / 1 / 1` | `T + a` |
| Dodecahedron (`D`) | — | Icosahedron | `12 / 30 / 20` | `I_h` | `1 / 1 / 1` | `I + d` |
| Icosahedron (`I`) | — | Dodecahedron | `20 / 30 / 12` | `I_h` | `1 / 1 / 1` | `T + s` |

### Families

| Seed (tag) | Common alternative names | Dual | `F / E / V` | Full point group | `F / E / V` orbits | Construction |
| --- | --- | --- | --- | --- | --- | --- |
| Prism (`P3`–`P100`) | `n`-gonal prism | Bipyramid `n` | `n+2 / 3n / 2n` | `D_nh` | normally `2 / 2 / 1` | Family seed |
| Antiprism (`A3`–`A100`) | `n`-gonal antiprism | `n`-gonal trapezohedron | `2n+2 / 4n / 2n` | `D_nd` | normally `2 / 3 / 1` | Family seed |
| Pyramid (`Y3`–`Y100`) | `n`-gonal pyramid | Pyramid `n` | `n+1 / 2n / n+1` | `C_nv` | normally `2 / 2 / 2` | Family seed |
| Bipyramid (`B3`–`B100`) | Dipyramid; `n`-gonal bipyramid | Prism `n` | `2n / 3n / n+2` | `D_nh` | normally `1 / 2 / 2` | Family seed |

### Archimedean

| Seed (tag) | Common alternative names | Dual | `F / E / V` | Full point group | `F / E / V` orbits | Primitive construction |
| --- | --- | --- | --- | --- | --- | --- |
| Truncated tetrahedron (`tT`) | — | Triakis tetrahedron | `8 / 18 / 12` | `T_d` | `2 / 2 / 1` | `T + t` |
| Cuboctahedron (`aC`) | — | Rhombic dodecahedron | `14 / 24 / 12` | `O_h` | `2 / 1 / 1` | `C + a` |
| Truncated cube (`tC`) | — | Triakis octahedron | `14 / 36 / 24` | `O_h` | `2 / 2 / 1` | `C + t` |
| Truncated octahedron (`tO`) | — | Tetrakis hexahedron | `14 / 36 / 24` | `O_h` | `2 / 2 / 1` | `O + t` |
| Rhombicuboctahedron (`eC`) | Small rhombicuboctahedron | Deltoidal icositetrahedron | `26 / 48 / 24` | `O_h` | `3 / 2 / 1` | `C + a → a` |
| Rhombitruncated cuboctahedron (`bC`) | Truncated cuboctahedron; great rhombicuboctahedron | Disdyakis dodecahedron | `26 / 72 / 48` | `O_h` | `3 / 3 / 2` | `C + a → t` |
| Snub cube (`sC`, `sC'`) | — | Pentagonal icositetrahedron | `38 / 60 / 24` | `O` | `3 / 3 / 1` | `C + s/s'` |
| Icosidodecahedron (`aD`) | — | Rhombic triacontahedron | `32 / 60 / 30` | `I_h` | `2 / 1 / 1` | `D + a` |
| Truncated dodecahedron (`tD`) | — | Triakis icosahedron | `32 / 90 / 60` | `I_h` | `2 / 2 / 1` | `D + t` |
| Truncated icosahedron (`tI`) | Soccer-ball solid; buckyball shape | Pentakis dodecahedron | `32 / 90 / 60` | `I_h` | `2 / 2 / 1` | `I + t` |
| Rhombicosidodecahedron (`eD`) | Small rhombicosidodecahedron | Deltoidal hexecontahedron | `62 / 120 / 60` | `I_h` | `3 / 2 / 1` | `D + a → a` |
| Rhombitruncated icosidodecahedron (`bD`) | Truncated icosidodecahedron; great rhombicosidodecahedron | Disdyakis triacontahedron | `62 / 180 / 120` | `I_h` | `3 / 3 / 2` | `D + a → t` |
| Snub dodecahedron (`sD`, `sD'`) | — | Pentagonal hexecontahedron | `92 / 150 / 60` | `I` | `3 / 3 / 1` | `D + s/s'` |

### Catalan

| Seed (tag) | Common alternative names | Dual | `F / E / V` | Full point group | `F / E / V` orbits | Primitive construction |
| --- | --- | --- | --- | --- | --- | --- |
| Triakis tetrahedron (`dtT`) | Tristetrahedron | Truncated tetrahedron | `12 / 18 / 8` | `T_d` | `1 / 2 / 2` | `tT + d` |
| Rhombic dodecahedron (`daC`) | — | Cuboctahedron | `12 / 24 / 14` | `O_h` | `1 / 1 / 2` | `aC + d` |
| Triakis octahedron (`dtC`) | Small triakis octahedron; trisoctahedron | Truncated cube | `24 / 36 / 14` | `O_h` | `1 / 2 / 2` | `tC + d` |
| Tetrakis hexahedron (`dtO`) | Tetrahexahedron | Truncated octahedron | `24 / 36 / 14` | `O_h` | `1 / 2 / 2` | `tO + d` |
| Deltoidal icositetrahedron (`deC`) | Trapezoidal icositetrahedron; strombic icositetrahedron | Rhombicuboctahedron | `24 / 48 / 26` | `O_h` | `1 / 2 / 3` | `eC + d` |
| Disdyakis dodecahedron (`dbC`) | Hexakis octahedron; hexoctahedron | Rhombitruncated cuboctahedron | `48 / 72 / 26` | `O_h` | `2 / 3 / 3` | `bC + d` |
| Pentagonal icositetrahedron (`dsC`, `dsC'`) | — | Snub cube | `24 / 60 / 38` | `O` | `1 / 3 / 3` | `sC/sC' + d` |
| Rhombic triacontahedron (`daD`) | — | Icosidodecahedron | `30 / 60 / 32` | `I_h` | `1 / 1 / 2` | `aD + d` |
| Triakis icosahedron (`dtD`) | — | Truncated dodecahedron | `60 / 90 / 32` | `I_h` | `1 / 2 / 2` | `tD + d` |
| Pentakis dodecahedron (`dtI`) | — | Truncated icosahedron | `60 / 90 / 32` | `I_h` | `1 / 2 / 2` | `tI + d` |
| Deltoidal hexecontahedron (`deD`) | Trapezoidal hexecontahedron; strombic hexecontahedron | Rhombicosidodecahedron | `60 / 120 / 62` | `I_h` | `1 / 2 / 3` | `eD + d` |
| Disdyakis triacontahedron (`dbD`) | Hexakis icosahedron | Rhombitruncated icosidodecahedron | `120 / 180 / 62` | `I_h` | `2 / 3 / 3` | `bD + d` |
| Pentagonal hexecontahedron (`dsD`, `dsD'`) | — | Snub dodecahedron | `60 / 150 / 92` | `I` | `1 / 3 / 3` | `sD/sD' + d` |

## Completeness and validation

- The fixed catalog contains 31 unique base tags: exactly 5 Platonic, 13
  Archimedean, and 13 Catalan types. This matches the complete classical
  enumerations. The four family selectors add 392 concrete family members.
- The 13 Catalan rows pair one-to-one with the 13 Archimedean rows. Every pair
  has the same `E`, exchanged `F` and `V`, the same point group, and exchanged
  face/vertex orbit counts, as duality requires.
- Every row satisfies Euler's formula `F - E + V = 2`.
- The four chiral types add `sC'`, `sD'`, `dsC'`, and `dsD'` to the core without
  adding new solid types. Thus the UI lists 31 fixed types plus four family
  selectors, while the core supports 35 handed fixed representations and 392
  family members.
- Fixed-row `F / E / V` and orbit counts were read from each current `Seed.poly`.
  The validation suite checks representative members of every family through
  `n = 100` against their formulas and independently validates each generated
  mesh. The construction suite evaluates all 49 handed directed variants of the
  diagram edges through the core and recognizes the stated target, including
  both directions of Dual and prime chirality. Separate path assertions prove
  that Tetrahedron alone reaches and constructs all 35 handed fixed
  representations.
- Runtime symmetry tests independently recover the full point group,
  proper-rotation orbit counts, and mirror-plane counts of every fixed seed plus
  representative axial families directly from geometry, including chiral and
  strengthened cases.
- No Platonic, classical Archimedean, or Catalan solid is missing. The prism and
  antiprism families plus the pyramid and bipyramid families are included;
  Johnson solids beyond pyramids and nonconvex uniform or Kepler-Poinsot solids
  remain outside this catalog.

## Sources of truth

- Fixed seed definitions: [`Seed.kt`](../core/src/commonMain/kotlin/poly/Seed.kt).
- Family seed definitions and geometry: [`FamilySeed.kt`](../core/src/commonMain/kotlin/poly/FamilySeed.kt).
- UI seed types and prime variants: [`CoreOptions.kt`](../web/src/jsMain/kotlin/catalog/CoreOptions.kt).
- Tested construction edges: [`SeedConstructionTest.kt`](../core/src/commonTest/kotlin/SeedConstructionTest.kt).
- Operation definitions: [Transformations and macros](transformations.md).
- [Wolfram MathWorld: Platonic Solid](https://mathworld.wolfram.com/PlatonicSolid.html).
- [Wolfram MathWorld: Archimedean Solid](https://mathworld.wolfram.com/ArchimedeanSolid.html).
- [Wolfram MathWorld: Archimedean Dual](https://mathworld.wolfram.com/ArchimedeanDual.html).
- [George W. Hart: Archimedean Duals](https://georgehart.com/virtual-polyhedra/archimedean-duals-info.html).
