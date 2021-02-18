package polyhedra.js

import polyhedra.common.*
import polyhedra.js.components.*
import react.*
import react.dom.*

external interface RootPaneState : RState {
    var seed: Seed
    var transforms: List<Transform>
    var scale: Scale
    var rotate: Boolean
}

fun RootPaneState.poly(): Polyhedron =
    seed.poly.transformed(transforms).scaled(scale)

private inline fun ifValidSeedTransforms(seed: Seed, transforms: List<Transform>, block: () -> Unit) {
    try {
        seed.poly.transformed(transforms).validateGeometry()
        block()
    } catch (e: Exception) {
        println("$seed $transforms is not valid: $e")
        e.printStackTrace()
    }
}

private fun RootPaneState.safeSeedUpdate(newSeed: Seed) {
    ifValidSeedTransforms(newSeed, transforms) {
        seed = newSeed
    }
}

private fun RootPaneState.safeTransformsUpdate(update: (List<Transform>) -> List<Transform>) {
    val newTransforms = update(transforms)
    ifValidSeedTransforms(seed, newTransforms) {
        transforms = newTransforms
    }
}

@Suppress("NON_EXPORTABLE_TYPE")
@JsExport
class RootPane() : RComponent<RProps, RootPaneState>() {
    override fun RootPaneState.init() {
        seed = Seed.Tetrahedron
        transforms = emptyList()
        scale = Scale.Midradius
        rotate = true
    }

    override fun RBuilder.render() {
        // State
        div {
            label {
                +"Seed"
                dropdown<Seed> {
                    value = state.seed
                    options = Seeds
                    onChange = { setState { safeSeedUpdate(it) } }
                }
            }
            label {
                +"Transform"
                for ((i, transform) in state.transforms.withIndex()) {
                    dropdown<Transform> {
                        value = transform
                        options = Transforms
                        onChange = { value ->
                            setState {
                                if (value != Transform.None) {
                                    safeTransformsUpdate { it.updatedAt(i, value) }
                                } else {
                                    safeTransformsUpdate { it.removedAt(i) }
                                }
                            }
                        }
                    }
                }
                dropdown<Transform> {
                    value = Transform.None
                    options = Transforms
                    onChange = { value ->
                        setState {
                            if (value != Transform.None) {
                                safeTransformsUpdate { it + value }
                            }
                        }
                    }
                }
            }
            label {
                +"Scaled by"
                dropdown<Scale> {
                    value = state.scale
                    options = Scales
                    onChange = { setState { scale = it } }
                }
            }
            label {
                +"Rotate"
                checkbox {
                    checked = state.rotate
                    onChange = { setState { rotate = it } }
                }
            }
        }
        // Canvas & Info
        val curPoly = state.poly()
        polyCanvas {
            poly = curPoly
            style = PolyStyle()
            rotate = state.rotate
        }
        polyInfoPane {
            poly = curPoly
        }
    }
}
