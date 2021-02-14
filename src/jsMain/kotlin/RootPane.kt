package polyhedra.js

import kotlinx.html.js.onChangeFunction
import org.w3c.dom.*
import polyhedra.common.*
import react.*
import react.dom.*

external interface RootPaneState : RState {
    var seed: Seed
    var transforms: List<Transform>
    var scale: Scale
}

fun RootPaneState.poly(): Polyhedron {
    var poly = seed.poly
    for (transform in transforms) poly = poly.transformed(transform)
    return poly.scaled(scale)
}

@Suppress("NON_EXPORTABLE_TYPE")
@JsExport
class RootPane() : RComponent<RProps, RootPaneState>() {
    override fun RootPaneState.init() {
        seed = Seed.Tetrahedron
        transforms = emptyList()
        scale = Scale.Midradius

    }

    override fun RBuilder.render() {
        label {
            +"Seed"
            dropdown<Seed> {
                value = state.seed
                options = Seed.values().toList()
                onChange = { value ->
                    setState { seed = value }
                }
            }
        }
        label {
            +"Transform"
            for ((i, transform) in state.transforms.withIndex()) {
                dropdown<Transform> {
                    value = transform
                    options = Transform.values().toList()
                    onChange = { value ->
                        setState {
                            if (value != Transform.None) {
                                transforms = state.transforms.updatedAt(i, value)
                            } else {
                                transforms = state.transforms.removedAt(i)
                            }
                        }
                    }
                }
            }
            dropdown<Transform> {
                value = Transform.None
                options = Transform.values().toList()
                onChange = { value ->
                    setState {
                        if (value != Transform.None) {
                            transforms = state.transforms + value
                        }
                    }
                }
            }
        }
        label {
            +"Scaled by"
            dropdown<Scale> {
                value = state.scale
                options = Scale.values().toList()
                onChange = { value ->
                    setState { scale = value }
                }
            }
        }
        br {}
        glCanvas {
            poly = state.poly()
            style = PolyStyle()
        }
    }
}
