package polyhedra.js

import kotlinx.html.*
import kotlinx.html.js.*
import polyhedra.common.*
import polyhedra.common.util.*
import polyhedra.js.components.*
import polyhedra.js.poly.*
import react.*
import react.dom.*

external interface RootPaneState : RState {
    // Polyhedra
    var seed: Seed
    var transforms: List<Transform>
    // View
    var baseScale: Scale
    var viewScale: Double
    var expand: Double
    var rotationAngle: Double
    var rotate: Boolean
    // Style
    var transparent: Double
    var display: Display
    // Lighting
    var ambientLight: Double
    var pointLight: Double
    var specularLight: Double
    var specularPower: Double
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
                    rotationAngle = state.rotationAngle
                    rotate = state.rotate
                    viewScale = state.viewScale
                    expand = state.expand
                    transparent = if (state.display.hasFaces()) state.transparent else 0.0
                    ambientLight = state.ambientLight
                    pointLight = state.pointLight
                    specularLight = state.specularLight
                    specularPower = state.specularPower
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
        tableBody {
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

        header("View")
        tableBody {
            tr("control") {
                td { +"Base scale" }
                td {
                    dropdown<Scale> {
                        value = state.baseScale
                        options = Scales
                        onChange = { setState { baseScale = it } }
                    }
                }
            }
            tr("control") {
                td { +"View scale" }
                td {
                    slider {
                        min = -2.0
                        max = 2.0
                        step = 0.01
                        value = state.viewScale
                        onChange = { setState { viewScale = it } }
                    }
                }
            }
            tr("control") {
                td { +"Expand" }
                td {
                    slider {
                        min = 0.0
                        max = 2.0
                        step = 0.01
                        value = state.expand
                        onChange = { setState { expand = it } }
                    }
                }
            }
            tr("control") {
                td { +"Rotate" }
                td {
                    slider {
                        disabled = !state.rotate
                        min = 0.0
                        max = 360.0
                        step = 1.0
                        value = state.rotationAngle
                        onChange = { setState { rotationAngle = it } }
                    }
                    checkbox {
                        checked = state.rotate
                        onChange = { setState { rotate = it } }
                    }
                }
            }
        }

        header("Style")
        tableBody {
            tr("control") {
                td { +"Display" }
                td {
                    dropdown<Display> {
                        value = state.display
                        options = Displays
                        onChange = { setState { display = it } }
                    }
                }
            }
            tr("control") {
                td { +"Transparent" }
                td {
                    slider {
                        disabled = !state.display.hasFaces()
                        min = 0.0
                        max = 1.0
                        step = 0.01
                        value = state.transparent
                        onChange = { setState { transparent = it } }
                    }
                }
            }
        }

        header("Lighting")
        tableBody {
            tr("control") {
                td { +"Ambient" }
                td {
                    slider {
                        disabled = !state.display.hasFaces()
                        min = 0.0
                        max = 1.0
                        step = 0.01
                        value = state.ambientLight
                        onChange = { setState { ambientLight = it } }
                    }
                }
            }
            tr("control") {
                td { +"Point" }
                td {
                    slider {
                        disabled = !state.display.hasFaces()
                        min = 0.0
                        max = 1.0
                        step = 0.01
                        value = state.pointLight
                        onChange = { setState { pointLight = it } }
                    }
                }
            }
            tr("control") {
                td { +"Specular" }
                td {
                    slider {
                        disabled = !state.display.hasFaces()
                        min = 0.0
                        max = 1.0
                        step = 0.01
                        value = state.specularLight
                        onChange = { setState { specularLight = it } }
                    }
                }
            }
            tr("control") {
                td { +"Shininess" }
                td {
                    slider {
                        disabled = !state.display.hasFaces()
                        min = 1.0
                        max = 100.0
                        step = 0.1
                        value = state.specularPower
                        onChange = { setState { specularPower = it } }
                    }
                }
            }
        }
    }
}

fun RBuilder.tableBody(block: RDOMBuilder<TBODY>.() -> Unit) {
    table {
        tbody(block = block)
    }
}
