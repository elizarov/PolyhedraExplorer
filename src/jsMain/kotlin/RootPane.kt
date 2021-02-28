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
    var animateUpdates: Boolean
    var rotate: Boolean
}

@Suppress("NON_EXPORTABLE_TYPE")
@JsExport
class RootPane(props: PComponentProps<RootParams>) :
    PComponent<RootParams, PComponentProps<RootParams>, RootPaneState>(props)
{
    override fun RootPaneState.init(props: PComponentProps<RootParams>) {
        seed = props.param.poly.seed.value
        transforms = props.param.poly.transforms.value
        baseScale = props.param.poly.baseScale.value

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
        animateUpdates = props.param.animation.animateValueUpdates.value
        rotate = props.param.animation.animatedRotation.value
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

    private fun RDOMBuilder<DIV>.renderControls() {
        header("Polyhedron")
        div("row control") {
            label { +"Seed" }
            pDropdown(props.param.poly.seed)
        }

        header("Transforms")
        tableBody {
            renderTransformsRows()
        }

        header("View")
        val lightingDisabled = !state.display.hasFaces()
        tableBody {
            controlRow("Base scale") { pDropdown(props.param.poly.baseScale) }
            controlRow("View scale") { pSlider(props.param.poly.view.scale) }
            controlRow("Expand") { pSlider(props.param.poly.view.expandFaces) }
            controlRow("Transparent") { pSlider(props.param.poly.view.transparentFaces, lightingDisabled) }
            controlRow("Display") { pDropdown(props.param.poly.view.display) }
        }

        header("Animation")
        tableBody {
            controlRow2("Rotation", { pCheckbox(props.param.animation.animatedRotation) }) {
                pSlider(props.param.animation.rotationSpeed, !state.rotate)
            }
            controlRow2("Angle", {}, {
                pSlider(props.param.animation.rotationAngle, !state.rotate)
            })
            controlRow2("Updates", { pCheckbox(props.param.animation.animateValueUpdates) }) {
                pSlider(props.param.animation.animationDuration, !state.animateUpdates)
            }
        }

        header("Lighting")
        tableBody {
            controlRow("Ambient") { pSlider(props.param.poly.lighting.ambientLight, lightingDisabled) }
            controlRow("Diffuse") { pSlider(props.param.poly.lighting.diffuseLight, lightingDisabled) }
            controlRow("Specular") { pSlider(props.param.poly.lighting.specularLight, lightingDisabled) }
            controlRow("Shininess") { pSlider(props.param.poly.lighting.specularPower, lightingDisabled) }
        }

        header("Export")
        div("control row") {
            button {
                attrs {
                    onClickFunction = {
                        val name = state.polyName.replace(' ', '_').lowercase()
                        download("$name.scad", state.poly.exportGeometryToScad(name))
                    }
                }
                +"Geometry to SCAD"
            }
        }
    }

    private fun RDOMBuilder<TBODY>.renderTransformsRows() {
        for ((i, transform) in state.transforms.withIndex()) {
            controlRow("${i + 1}:") {
                dropdown<Transform> {
                    disabled = i > state.geometryErrorIndex
                    value = transform
                    options = Transforms
                    onChange = { value ->
                        if (value != Transform.None) {
                            props.param.poly.transforms.updateValue(props.param.poly.transforms.value.updatedAt(i, value))
                        } else {
                            props.param.poly.transforms.updateValue(props.param.poly.transforms.value.removedAt(i))
                        }
                    }
                }
                if (i == state.geometryErrorIndex) {
                    span {
                        +"⚠️"
                        span("tooltip-text") { +state.geometryErrorMessage }
                    }
                }
            }
        }
        controlRow("${state.transforms.size + 1}:") {
            dropdown<Transform> {
                disabled = state.geometryErrorIndex < state.transforms.size
                value = Transform.None
                options = Transforms
                onChange = { value ->
                    if (value != Transform.None) {
                        props.param.poly.transforms.updateValue(props.param.poly.transforms.value + value)
                    }
                }
            }
        }
        for (i in state.transforms.size + 1..8) controlRow("${i + 1}:") {}
        controlRow("") {
            button {
                attrs {
                    onClickFunction = { props.param.poly.transforms.updateValue(emptyList()) }
                }
                +"Clear"
            }
        }
    }
}

private fun RDOMBuilder<DIV>.header(text: String) {
    div("header-container") {
        div("header") { +text }
    }
}

fun RBuilder.tableBody(block: RDOMBuilder<TBODY>.() -> Unit) {
    table {
        tbody(block = block)
    }
}

fun RDOMBuilder<TBODY>.controlRow(label: String, block: RDOMBuilder<TD>.() -> Unit) {
    tr("control") {
        td { +label }
        td(block = block)
    }
}

fun RDOMBuilder<TBODY>.controlRow2(
    label: String,
    block1: RDOMBuilder<TD>.() -> Unit,
    block2: RDOMBuilder<TD>.() -> Unit
) {
    tr("control") {
        td { +label }
        td(block = block1)
        td(block = block2)
    }
}
