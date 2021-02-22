package polyhedra.js

import kotlinx.html.*
import kotlinx.html.js.*
import polyhedra.common.*
import polyhedra.common.util.*
import polyhedra.js.components.*
import polyhedra.js.poly.*
import react.*
import react.dom.*

private const val MAX_DISPLAY_EDGES = (1 shl 15) - 1

external interface RootPaneState : RState {
    var seed: Seed
    var transforms: List<Transform>
    var baseScale: Scale

    var poly: Polyhedron
    var polyName: String
    var geometryErrorIndex: Int
    var geometryErrorMessage: String
    
    var display: Display
    var rotate: Boolean
}

@Suppress("NON_EXPORTABLE_TYPE")
@JsExport
class RootPane(props: PComponentProps<RootParams>) : PComponent<RootParams, PComponentProps<RootParams>, RootPaneState>(props) {
    override fun RootPaneState.init(props: PComponentProps<RootParams>) {
        seed = props.param.seed.value
        transforms = props.param.transforms.value
        baseScale = props.param.baseScale.value

        var curPoly = seed.poly
        var curPolyName = seed.toString()
        var curIndex = 0
        var curMessage = ""
        try {
            for (transform in transforms) {
                val applicable = transform.isApplicable(curPoly)
                if (applicable != null) {
                    curMessage = applicable
                    break
                }
                val newPoly = curPoly.transformed(transform)
                val nEdges = newPoly.es.size
                if (nEdges > MAX_DISPLAY_EDGES) {
                    curMessage = "Polyhedron is too large to display ($nEdges edges)"
                    break
                }
                newPoly.validateGeometry()
                curPolyName = "$transform $curPolyName"
                curPoly = newPoly
                curIndex++
            }
        } catch (e: Exception) {
            curMessage = "Transform produces invalid polyhedron geometry"
        }
        poly = curPoly.scaled(baseScale)
        polyName = curPolyName
        geometryErrorIndex = curIndex
        geometryErrorMessage = curMessage
        
        display = props.param.poly.view.display.value
        rotate = props.param.poly.animation.rotate.value
    }

    override fun RBuilder.render() {
        div("main-layout") {
            div("control-column card") {
                renderControls()
            }
            div("canvas-column card") {
                // Canvas & Info
                header(state.polyName)
                polyCanvas("poly") {
                    params = props.param.poly
                    poly = state.poly
                }
                polyInfoPane {
                    poly = state.poly
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
            }
        }

        header("Transforms")
        tableBody {
            for ((i, transform) in state.transforms.withIndex()) {
                tr("control") {
                    td { +"${i + 1}:" }
                    td {
                        dropdown<Transform> {
                            disabled = i > state.geometryErrorIndex
                            value = transform
                            options = Transforms
                            onChange = { value ->
                                if (value != Transform.None) {
                                    props.param.transforms.value = props.param.transforms.value.updatedAt(i, value)
                                } else {
                                    props.param.transforms.value = props.param.transforms.value.removedAt(i)
                                }
                            }
                        }
                        if (i == state.geometryErrorIndex) {
                            span("tooltip desc") {
                                +"⚠️"
                                span("tooltip-text") { +state.geometryErrorMessage }
                            }
                        }
                    }
                }
            }
            tr("control") {
                td { +"${state.transforms.size + 1}:" }
                td {
                    dropdown<Transform> {
                        disabled = state.geometryErrorIndex < state.transforms.size
                        value = Transform.None
                        options = Transforms
                        onChange = { value ->
                            if (value != Transform.None) {
                                props.param.transforms.value = props.param.transforms.value + value
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
