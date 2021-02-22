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
    var seed: Seed
    var transforms: List<Transform>
    var baseScale: Scale
    var display: Display
    var rotate: Boolean
}

fun RootPaneState.poly(): Polyhedron =
    seed.poly.transformed(transforms).scaled(baseScale)

fun RootPaneState.polyName(): String =
    transforms.reversed().joinToString("") { "$it " } + seed

private fun isValidSeedTransforms(seed: Seed, transforms: List<Transform>): Boolean {
    try {
        seed.poly.transformed(transforms).validateGeometry()
        return true
    } catch (e: Exception) {
        println("$seed $transforms is not valid: $e")
        e.printStackTrace()
        return false
    }
}

@Suppress("NON_EXPORTABLE_TYPE")
@JsExport
class RootPane(props: PComponentProps<RootParams>) : PComponent<RootParams, PComponentProps<RootParams>, RootPaneState>(props) {
    override fun RootPaneState.init(props: PComponentProps<RootParams>) {
        seed = props.param.seed.value
        transforms = props.param.transforms.value
        baseScale = props.param.baseScale.value
        display = props.param.poly.view.display.value
        rotate = props.param.poly.animation.rotate.value
    }

    private fun safeTransformsUpdate(update: (List<Transform>) -> List<Transform>) {
        val newTransforms = update(props.param.transforms.value)
        if (isValidSeedTransforms(props.param.seed.value, newTransforms)) {
                props.param.transforms.value = newTransforms
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
                    params = props.param.poly
                    poly = curPoly
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
            pDropdown<Seed> {
                param = props.param.seed
                validateChange = { it -> isValidSeedTransforms(it, props.param.transforms.value) }
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
            tr("control") {
                td { +"${state.transforms.size + 1}:" }
                td {
                    dropdown<Transform> {
                        value = Transform.None
                        options = Transforms
                        onChange = { value ->
                            if (value != Transform.None) {
                                    safeTransformsUpdate { it + value }
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
                            onClickFunction = { props.param.transforms.value = emptyList() }
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
                    pDropdown<Scale> {
                        param = props.param.baseScale
                    }
                }
            }
            tr("control") {
                td { +"View scale" }
                td {
                    pSlider {
                        param = props.param.poly.view.scale
                    }
                }
            }
            tr("control") {
                td { +"Expand" }
                td {
                    pSlider {
                        param = props.param.poly.view.expand
                    }
                }
            }
            tr("control") {
                td { +"Transparent" }
                td {
                    pSlider {
                        disabled = !state.display.hasFaces()
                        param = props.param.poly.view.transparent
                    }
                }
            }
            tr("control") {
                td { +"Display" }
                td {
                    pDropdown<Display> {
                        param = props.param.poly.view.display
                    }
                }
            }
            tr("control") {
                td { +"Rotate" }
                td {
                    pSlider {
                        disabled = !state.rotate
                        param = props.param.poly.animation.rotationAngle
                    }
                    pCheckbox {
                        param = props.param.poly.animation.rotate
                    }
                }
            }
        }

        header("Lighting")
        tableBody {
            tr("control") {
                td { +"Ambient" }
                td {
                    pSlider {
                        disabled = !state.display.hasFaces()
                        param = props.param.poly.lighting.ambientLight
                    }
                }
            }
            tr("control") {
                td { +"Point" }
                td {
                    pSlider {
                        disabled = !state.display.hasFaces()
                        param = props.param.poly.lighting.pointLight
                    }
                }
            }
            tr("control") {
                td { +"Specular" }
                td {
                    pSlider {
                        disabled = !state.display.hasFaces()
                        param = props.param.poly.lighting.specularLight
                    }
                }
            }
            tr("control") {
                td { +"Shininess" }
                td {
                    pSlider {
                        disabled = !state.display.hasFaces()
                        param = props.param.poly.lighting.specularPower
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
