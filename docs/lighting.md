# Lighting and plastic material

Polyhedra Explorer uses compact physically based models for opaque 3D-printing
plastics and transparent acrylic sheets. Both use a dielectric Cook-Torrance BRDF with an isotropic
GGX microfacet distribution, height-correlated Smith visibility, Schlick
Fresnel, and an energy-conserving Lambert diffuse lobe. Face colors are decoded
from sRGB before lighting and encoded back to sRGB for display.

Both materials are non-metallic. Transparent mode transmits the rendered scene instead of
reducing the opacity of an opaque plastic color. No metalness, clear-coat, anisotropy,
or material-texture controls are exposed.

## Surface response

For unit normal `n`, view direction `v`, light direction `l`, and half-vector
`h`, the direct response is

```text
f = (1 - F) baseColor / π + D_GGX V_Smith F
L_direct = f · max(n·l, 0) · keyLight · A(d)
A(d) = |lightPosition|² / d²
```

`D_GGX` distributes microscopic surface normals according to perceived
roughness. The correlated Smith term accounts for microfacets masking and
shadowing one another. Schlick Fresnel makes white surface reflection weak
head-on and stronger at grazing angles. Its normal-incidence value comes from
the physical index of refraction:

```text
F0 = ((IOR - 1) / (IOR + 1))²
F(v,h) = F0 + (1 - F0)(1 - |v·h|)⁵
```

The diffuse lobe is multiplied by `1 - F`, so light assigned to surface
reflection is not also counted as diffuse reflection. A cool constant
environment approximation supplies diffuse fill and a roughness-scaled grazing
reflection. It gives unlit faces and plastic edges readable shape without an
environment map, texture fetch, or additional rendering pass.

The neutral-warm key is a fixed nearby point light. Its inverse-square falloff
is normalized at the model origin, retaining physical spatial variation while
keeping the Key light control independent of the polyhedron's orientation.

The same fixed key position is used in every environment. The optional Table
environment adds a neutral-gray rough-plastic receiver with the same dielectric
model and projects the rendered face mesh onto it from that key. The one exact
projection produces a sharp geometry-dependent cast shadow. Toggling the table
therefore changes only the surroundings; polyhedron illumination remains
identical. The receiver remains fixed while the polyhedron rotates, so the
object reads as floating in a stable scene.

## Interior and cutaway lighting

Reverse faces and explicit undersides retain the same plastic BRDF and orbit
color, but receive less incident light. This is an analytic cavity-shadow
approximation, not a different material and not a ray-traced or shadow-mapped
visibility test. It makes exposed interiors read as recesses instead of bright
outer faces during Cut, rotation, and expansion. Acrylic does not use this opaque-cavity
occlusion proxy: its exposed reverse surfaces transmit scene light and retain dielectric reflections.

Cut faces defaults to `+0.5` of the selected base-scale radius, retaining more of the
front shell around the opening. Its range is `-1` (back) to `+1` (front); `0`
passes through the center. URLs omit the default position and use `cp(0)` for
an explicit center cut.

The opening has a narrow neutral-light band with a dark inner edge. This is a
diagrammatic inspection overlay on retained face fragments, not a cap or extra
material. Its surface-distance width is proportional to the displayed radius;
the shader corrects for face inclination and suppresses the band on parallel
faces. The band has smooth transitions and preserves face transparency. It does
not change exported geometry or cast additional shadows.

Edges in front of the cut plane remain as a faint wireframe of the removed
shell; retained edges keep their normal appearance. The existing Display control
governs this overlay: Faces-only hides it, and print preview also suppresses it.
The ghost uses the existing edge draw, with no face-direction culling in the
removed region. Each face occurrence contributes 12% opacity (about 23% for a
shared unexpanded edge). Opaque rendering depth-tests it against retained geometry;
acrylic draws it once after both optical passes, together with the other edge overlays.

`FaceProgram` treats every reverse-facing material boundary as interior. This
includes the reverse of a rim's underside and side walls seen through a cut:
flipping the BRDF normal toward the viewer does not make these surfaces exterior.
For front-facing surfaces, negative alignment of the lighting normal with the
original face's outward normal identifies an explicit underside. Front-facing
perpendicular rim walls retain their ordinary lighting; the vertex thickness
flag cannot identify an underside because it also varies across those walls.
Orientation comes from the source surface, not a resolved solid, so immersed
internal sheets are treated consistently.

For an interior fragment, let `d` be its nonnegative distance behind the cut
plane and `R` the displayed circumradius, including face expansion. The light
access proxy is `A = 1 / (1 + (d/R)²)`, inspired by the projected solid angle of
a circular aperture viewed along its axis. With Cut off, `d = 0`. Incident key
light is multiplied by `0.06 + 0.28 A`; environment fill and its reflection by
`0.25 + 0.40 A`. Exterior factors remain exactly `1`. These calibrated floors
preserve readable color in deep recesses, while attenuation of both diffuse and
specular light avoids bright exterior-like highlights on backsides. No extra
controls or serialization fields are needed.

This proxy assumes inward surfaces have less access to illumination. It does
not measure real apertures or neighboring-face occlusion: open and expanded
models can be darker inside than a physical light-transport solution would
predict. There are no moving shadow silhouettes, contact shadows, or secondary
light bounces on the polyhedron itself.

## PLA default

The default IOR is `1.46`. Visible-wavelength ellipsometry measured PLA from
about `1.499` at 300 nm to `1.448` at 1300 nm, with the reported Cauchy model
giving approximately `1.46` around the middle of the visible spectrum. This
produces `F0 ≈ 0.035`, close to—but more PLA-specific than—the common dielectric
default of `0.04`.

Roughness defaults to `0.45`, representing a moderately glossy FDM PLA finish.
Roughness is a surface property rather than a polymer constant: layer height,
print temperature, mold/contact surface, additives, and post-processing can all
change it. The default is therefore a visual calibration, while IOR is the
physics-backed bulk-material calibration.

Face-orbit colors are the normal base colors. The export drawer's Print preview
can replace them with one material color, red by default, without changing this
optical model. Its picker uses OKLCH so brightness and colorfulness remain
predictable while hue changes; colors beyond display sRGB are gamut-mapped by
reducing chroma while preserving lightness and hue.

## Controls

The View group selects the scene environment. Two controls describe illumination
and two describe the active material. Opaque and acrylic Roughness/IOR values are remembered
independently; changing material mode never overwrites the other pair.

| Group | Control | Default | Range | Meaning |
| --- | --- | ---: | ---: | --- |
| View | Environment | `Table` | `None`, `Table` | Keep the background-only view, or add the lit table and cast shadow. |
| Faces | Transparent | Off; amount `0.85` | Checkbox and `0–1` slider | Enable acrylic; vary transmission while retaining surface reflections. Disabling it remembers the amount. |
| Lighting | Key light | `2.5` | `0.0–5.0` | Radiance multiplier for the fixed neutral-warm studio point light. |
| Lighting | Fill light | `0.22` | `0.0–1.0` | Intensity of the cool constant environment approximation. |
| Material | Roughness | PLA `0.45`; acrylic `0.12` | PLA `0.15–1.0`; acrylic `0.08–1.0` | Perceived microsurface roughness; acrylic also blurs transmitted scene detail. |
| Material | IOR | PLA `1.46`; acrylic `1.49` | `1.30–1.70` | Index of refraction, used for Fresnel reflection and acrylic refraction. |
| Export | Print preview | Off, red | Basic colors or OKLCH | Use one filament base color for every rendered surface and hide edge overlays; this does not alter exported geometry. |

The environment, both material profiles, transparency mode/amount, and print-preview color are
URL-backed. `Table` is omitted; None is stored as `env(n)` in the View
parameters. Deserialization maps `Ambient` to Fill light and `Diffuse` to Key
light. It ignores Specular and Shininess because Fresnel and roughness determine
those effects.

`v(t(y))` enables acrylic; `v(ta(...))` stores a non-default amount independently of the
checkbox. Numeric legacy `v(t(...))` values load as the corresponding acrylic amount, enabled
when positive. Material tags `l(r(...)i(...))` describe PLA and `l(ar(...)ai(...))` acrylic.
Defaults are omitted. Saved configurations use the same representation.

## Transparent acrylic

The calibrated acrylic IOR is `1.49`, giving approximately 3.9% reflection at a head-on
air/acrylic interface. A clear parallel sheet transmits approximately 92.5% through its two
interfaces, consistent with PLEXIGLAS optical-sheet specifications. Roughness `0.12` and
transmission amount `0.85` are visual defaults for polished, lightly colored acrylic, not
measured universal material constants.

Each visible source-face layer is approximated as a locally parallel sheet. For view cosine
`cosθ`, Snell's law gives the internal ray and `cosθt`; its material path is `Width / cosθt`.
The face/preview color is converted to linear RGB and defines the Beer–Lambert absorption tint:

```text
A = clamp(baseColor, 0.02, 1) ^ ((Width / cosθt) / 0.1 × (1 - amount))
R_sheet = 2 F / (1 + F)
T_sheet = (1 - F) / (1 + F)
L = reflected/scattered light + amount × T_sheet × A × sampled scene light
```

`F` is Schlick Fresnel at the viewing angle. The sheet factors include repeated interface
reflections in the zero-absorption limit; colored absorption is then applied once to the
transmission. Reflections are white and remain at amount `1`; the diffuse scattering lobe
is weighted by `1 - amount`. At amount `0` no background is transmitted. Increasing Width
deepens the color and increases refraction. Width and the `0.1` absorption reference are in
selected base-radius units, making zoom independent of material absorption; this is not a
millimeter-calibrated pigment concentration.

A parallel sheet's outgoing ray is parallel to its incoming ray but laterally displaced.
The shader projects this Snell-law displacement into screen coordinates, capped at 4% of
screen height. Four symmetric texture samples approximate roughness blur. The background is
decoded to linear RGB before absorption and reflection are combined, then encoded for display.
Transparent canvas regions use the same neutral page background as headless PNG rendering.

The renderer snapshots the table/background, renders the nearest back-facing material layer,
snapshots again, then renders the nearest front-facing material layer. Layer selection uses the
rasterized triangle's facing, including rim walls and undersides, never the hidden source face's
orientation or a vertex-interpolated visibility mask. GPU depth testing chooses each layer independently
of triangle order. Culling is disabled, supporting cut faces, rims, and immersed surfaces.
Edges are drawn once over the completed acrylic image, including edges visible through the
material. They are never put into different optical layers according to source-face direction;
Cut's optional removed-shell wireframe is likewise drawn only once. Geometry,
export, and core computations are unchanged. The table shadow is reduced by estimated sheet
transmission, with a nonzero reflection loss even for clear acrylic.

## Performance and limits

The face fragment shader evaluates one point light and one constant environment.
The opaque path uses no textures, lookup tables, cubemaps, or shadow maps. Its main
non-polynomial work is normalizing the light vectors, two square roots for
correlated Smith visibility, and small fixed powers for Fresnel and color-space
conversion.

Acrylic uses a separately compiled shader and one lazily allocated RGBA8 screen-sized texture
(`4 × width × height` bytes). Each frame makes two GPU framebuffer copies and two face draws,
with four texture reads per covered fragment. The texture is reused until resize and deleted
with its drawing context. There is no CPU readback, triangle sorting, geometry rebuild, ray
marching, float framebuffer requirement, or additional WASM work. The same WebGL 1 path runs
in browsers and the Node/headless-gl renderer.

This is a bounded two-layer screen-space approximation, not volumetric ray tracing: compounds
and immersed models may have more layers than are represented; offscreen objects cannot be
refracted; edges can reveal screen-space discontinuities. It does not simulate solid-lens
refraction, total-internal-reflection paths across unrelated faces, colored caustics, or exact
multiple scattering. Roughness blur and the neutral table shadow are inexpensive approximations.

Interior shading adds one scalar varying, one radius uniform, and fixed-cost
arithmetic per fragment. It reuses existing normals and depth, adds no draw
calls, textures, geometry rebuilds, or core work, and follows animated position
and normal interpolation directly. Headless WebGL pixel tests cover reversed
faces, explicit undersides, walls, cut depth, scale, material extremes, and alpha.

`None` adds no scene pass. `Table` adds one inexpensive receiver pass and one
draw of the already-uploaded animated face mesh. The shadow vertex shader uses
analytic point-to-plane projection, so it needs no framebuffer texture, depth
texture, or shadow-map allocation. A stencil union prevents overlapping faces
and triangulation from darkening the sharp shadow multiple times.

The model represents the optical response of a smooth aggregate surface. It
does not synthesize FDM layer lines, scratches, subsurface scattering,
self-reflections, or spatially varying roughness. Table shadows are planar
projections, so they do not create self-shadowing and can only be received by
the table plane.

## References

- [Khronos transmission extension](https://github.com/KhronosGroup/glTF/blob/main/extensions/2.0/Khronos/KHR_materials_transmission/README.md) — dielectric transmission is distinct from alpha coverage and preserves specular reflection.
- [Khronos volume extension](https://github.com/KhronosGroup/glTF/blob/main/extensions/2.0/Khronos/KHR_materials_volume/README.md) — thickness, refraction, and Beer–Lambert absorption.
- [PLEXIGLAS Optical sheet data](https://www.plexiglas.de/files/plexiglas-content/pdf/technische-informationen/232-25-EN-PLEXIGLAS-Optical-0Z024.pdf) — IOR `1.49` and 92% transmittance at 3 mm.

- [Khronos glTF 2.0 BRDF specification](https://registry.khronos.org/glTF/specs/2.0/glTF-2.0.html#appendix-b-brdf-implementation) — dielectric Fresnel mixing, GGX microfacets, and energy-conserving diffuse/specular composition.
- [Physically Based Rendering in Filament](https://google.github.io/filament/Filament.md.html) — real-time Cook-Torrance/GGX design and material parameterization.
- [PBRT: A Simple Path Tracer](https://www.pbr-book.org/4ed/Light_Transport_I_Surface_Reflection/A_Simple_Path_Tracer) — incident-light visibility is separate from the material response; the interior proxy approximates that visibility instead of tracing shadow rays.
- M. H. Hutchinson, J. R. Dorgan, D. M. Knauss, and S. B. Hait, [Optical Properties of Polylactides](https://doi.org/10.1007/s10924-006-0001-z), *Journal of Polymers and the Environment* 14 (2006) — measured PLA dispersion and Cauchy coefficients.
