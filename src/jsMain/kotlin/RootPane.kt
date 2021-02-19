package polyhedra.js

import polyhedra.common.*
import polyhedra.js.components.*
import polyhedra.js.poly.*
import react.*
import react.dom.*

external interface RootPaneState : RState {
    var seed: Seed
    var transforms: List<Transform>
    var scale: Scale
    var rotate: Boolean
    var viewScale: Double
}

fun RootPaneState.poly(): Polyhedron =
    seed.poly.transformed(transforms).scaled(scale)

fun RootPaneState.polyName(): String =
    transforms.reversed().joinToString("") { "$it " } + seed

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
class RootPane : RComponent<RProps, RootPaneState>() {
    override fun RootPaneState.init() {
        seed = Seed.Tetrahedron
        transforms = emptyList()
        scale = Scale.Midradius
        rotate = true
        viewScale = 0.0
    }

    override fun RBuilder.render() {
        div("main-layout") {
            div("canvas-column card") {
                // Canvas & Info
                val curPoly = state.poly()
                div("row header") { +state.polyName() }
                polyCanvas("poly") {
                    poly = curPoly
                    style = PolyStyle()
                    rotate = state.rotate
                    viewScale = state.viewScale
                    onRotateChange = { setState { rotate = it } }
                    onScaleChange = { setState { viewScale = it } }
                }
                polyInfoPane {
                    poly = curPoly
                }
            }
            div("control-column card") {
                renderControls()
            }
        }
    }

    private fun RBuilder.renderControls() {
        div("row header") { +"Polyhedron" }
        div("row") {
            label { +"Seed" }
            dropdown<Seed> {
                value = state.seed
                options = Seeds
                onChange = { setState { safeSeedUpdate(it) } }
            }
        }
        div("row header") { +"Transforms" }
        for ((i, transform) in state.transforms.withIndex()) {
            div("row") {
                label { +"${i + 1}:" }
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
        }
        div("row") {
            label { +"${state.transforms.size + 1}:" }
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
        div("row header") { +"View" }
        div("row") {
            label { +"Base scale" }
            dropdown<Scale> {
                value = state.scale
                options = Scales
                onChange = { setState { scale = it } }
            }
        }
        div("row") {
            label { +"Rotate" }
            checkbox {
                checked = state.rotate
                onChange = { setState { rotate = it } }
            }
        }
        div("row") {
            label { +"View scale" }
            slider {
                min = -2.0
                max = 2.0
                step = 0.05
                value = state.viewScale
                onChange = { setState { viewScale = it } }
            }
        }
    }
}
