# Lighting and plastic material

Polyhedra Explorer uses a compact physically based model for opaque 3D-printing
plastics. It is a single-layer dielectric Cook-Torrance BRDF with an isotropic
GGX microfacet distribution, height-correlated Smith visibility, Schlick
Fresnel, and an energy-conserving Lambert diffuse lobe. Face colors are decoded
from sRGB before lighting and encoded back to sRGB for display.

The material is deliberately always non-metallic and opaque: metalness,
transmission, clear coat, anisotropy, and texture controls would not improve
the intended plastic exploration enough to justify their cost or UI surface.

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
and two are necessary to vary opaque plastics.

| Group | Control | Default | Range | Meaning |
| --- | --- | ---: | ---: | --- |
| View | Environment | `Table` | `None`, `Table` | Keep the background-only view, or add the lit table and cast shadow. |
| Lighting | Key light | `2.5` | `0.0–5.0` | Radiance multiplier for the fixed neutral-warm studio point light. |
| Lighting | Fill light | `0.22` | `0.0–1.0` | Intensity of the cool constant environment approximation. |
| Material | Roughness | `0.45` | `0.15–1.0` | Perceived microsurface roughness; lower is glossier and higher is more matte. |
| Material | IOR | `1.46` | `1.30–1.70` | Plastic index of refraction, converted to dielectric `F0`. |
| Export | Print preview | Off, red | Basic colors or OKLCH | Use one filament base color for every rendered surface and hide edge overlays; this does not alter exported geometry. |

The environment, all four lighting/material values, and print-preview color are
URL-backed. `None` is omitted; Table is stored as `env(t)` in the View
parameters. The old `Ambient` and
`Diffuse` URL tags remain compatible as Fill light and Key light respectively;
obsolete Specular and
Shininess tags are ignored because Fresnel and roughness now determine those
effects.

## Performance and limits

The face fragment shader evaluates one point light and one constant environment.
It uses no material textures, lookup tables, cubemaps, or shadow maps. Its main
non-polynomial work is normalizing the light vectors, two square roots for
correlated Smith visibility, and small fixed powers for Fresnel and color-space
conversion.

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

- [Khronos glTF 2.0 BRDF specification](https://registry.khronos.org/glTF/specs/2.0/glTF-2.0.html#appendix-b-brdf-implementation) — dielectric Fresnel mixing, GGX microfacets, and energy-conserving diffuse/specular composition.
- [Physically Based Rendering in Filament](https://google.github.io/filament/Filament.md.html) — real-time Cook-Torrance/GGX design and material parameterization.
- M. H. Hutchinson, J. R. Dorgan, D. M. Knauss, and S. B. Hait, [Optical Properties of Polylactides](https://doi.org/10.1007/s10924-006-0001-z), *Journal of Polymers and the Environment* 14 (2006) — measured PLA dispersion and Cauchy coefficients.
