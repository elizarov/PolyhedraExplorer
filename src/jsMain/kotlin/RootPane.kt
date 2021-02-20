package polyhedra.js

import kotlinx.html.js.*
import polyhedra.common.*
import polyhedra.common.util.*
import polyhedra.js.components.*
import polyhedra.js.poly.*
import react.*
import react.dom.*

external interface RootPaneState : RState {
    var seed: Seed
    var transforms: List<Transform>
    var baseScale: Scale
    var rotate: Boolean
    var viewScale: Double
    var expand: Double
    var display: Display
}

fun RootPaneState.poly(): Polyhedron =
    seed.poly.transformed(transforms).scaled(baseScale)

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
        assign(loadRootState())
    }

    private fun setState(transformState: RootPaneState.() -> Unit) {
        (this as RComponent<RProps, RootPaneState>).setState {
            transformState()
            pushRootState(this)
        }
    }

    override fun RBuilder.render() {
        div("main-layout") {
            div("control-column card") {
                renderControls()
            }
            div("canvas-column card") {
                // Canvas & Info
                val curPoly = state.poly()
                header(state.polyName())
                polyCanvas("poly") {
                    poly = curPoly
                    style = PolyStyle(state.display)
                    rotate = state.rotate
                    viewScale = state.viewScale
                    expand = state.expand
                    onRotateChange = { setState { rotate = it } }
                    onScaleChange = { setState { viewScale = it } }
                }
                polyInfoPane {
                    poly = curPoly
                }
            }
        }
    }

    private fun RBuilder.header(text: String) {
        div("header-container") {
            div("header") { +text }
        }
    }

    private fun RBuilder.renderControls() {
        header("Polyhedron")
        div("row control") {
            label { +"Seed" }
            dropdown<Seed> {
                value = state.seed
                options = Seeds
                onChange = { setState { safeSeedUpdate(it) } }
            }
        }

        header("Transforms")
        table {
            tbody {
                for ((i, transform) in state.transforms.withIndex()) {
                    tr("control") {
                        td { +"${i + 1}:" }
                        td {
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
                }
                tr("control") {
                    td { +"${state.transforms.size + 1}:" }
                    td {
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
                }
                for (i in state.transforms.size + 1..8) {
                    tr("control") {
                        td { +"${i + 1}:" }
                        td {}
                    }
                }
                tr {
                    td {}
                    td {
                        button {
                            attrs {
                                onClickFunction = { setState { transforms = emptyList() } }
                            }
                            +"Clear"
                        }
                    }
                }
            }
        }

        header("View")
        div("row control") {
            label { +"Base scale" }
            dropdown<Scale> {
                value = state.baseScale
                options = Scales
                onChange = { setState { baseScale = it } }
            }
        }
        div("row control") {
            label { +"Rotate" }
            checkbox {
                checked = state.rotate
                onChange = { setState { rotate = it } }
            }
        }
        div("row control") {
            label { +"View scale" }
            slider {
                min = -2.0
                max = 2.0
                step = 0.01
                value = state.viewScale
                onChange = { setState { viewScale = it } }
            }
        }
        div("row control") {
            label { +"Expand" }
            slider {
                min = 0.0
                max = 2.0
                step = 0.01
                value = state.expand
                onChange = { setState { expand = it } }
            }
        }

        header("Style")
        div("row control") {
            label { +"Display" }
            dropdown<Display> {
                value = state.display
                options = Displays
                onChange = { setState { display = it } }
            }
        }
    }
}
