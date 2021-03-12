/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.js.main

import kotlinx.html.*
import kotlinx.html.js.*
import polyhedra.common.poly.*
import polyhedra.common.transform.*
import polyhedra.common.util.*
import polyhedra.js.components.*
import polyhedra.js.poly.*
import react.*
import react.dom.*

external interface RootPaneState : RState {
    var seed: Seed
    var transforms: List<Transform>
    var baseScale: Scale

    var poly: Polyhedron
    var polyName: String
    var transformError: TransformError?
    var transformProgress: Int

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
        seed = props.param.render.poly.seed.value
        transforms = props.param.render.poly.transforms.value
        baseScale = props.param.render.poly.baseScale.value

        poly = props.param.render.poly.poly
        polyName = props.param.render.poly.polyName
        transformError = props.param.render.poly.transformError
        transformProgress = props.param.render.poly.transformProgress

        display = props.param.render.view.display.value
        animateUpdates = props.param.animationParams.animateValueUpdates.value
        rotate = props.param.animationParams.animatedRotation.value
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
                    params = props.param.render
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
            pDropdown(props.param.render.poly.seed)
        }

        header("Transforms")
        tableBody {
            renderTransformsRows()
        }

        header("View")
        val lightingDisabled = !state.display.hasFaces()
        tableBody {
            controlRow("Base scale") { pDropdown(props.param.render.poly.baseScale) }
            controlRow("View scale") { pSlider(props.param.render.view.scale) }
            controlRow("Expand") { pSlider(props.param.render.view.expandFaces) }
            controlRow("Transparent") { pSlider(props.param.render.view.transparentFaces, lightingDisabled) }
            controlRow("Display") { pDropdown(props.param.render.view.display) }
        }

        header("Animation")
        tableBody {
            controlRow2("Rotation", { pCheckbox(props.param.animationParams.animatedRotation) }) {
                pSlider(props.param.animationParams.rotationSpeed, !state.rotate)
            }
            controlRow2("Angle", {}, {
                pSlider(props.param.animationParams.rotationAngle, !state.rotate)
            })
            controlRow2("Updates", { pCheckbox(props.param.animationParams.animateValueUpdates) }) {
                pSlider(props.param.animationParams.animationDuration, !state.animateUpdates)
            }
        }

        header("Lighting")
        tableBody {
            controlRow("Ambient") { pSlider(props.param.render.lighting.ambientLight, lightingDisabled) }
            controlRow("Diffuse") { pSlider(props.param.render.lighting.diffuseLight, lightingDisabled) }
            controlRow("Specular") { pSlider(props.param.render.lighting.specularLight, lightingDisabled) }
            controlRow("Shininess") { pSlider(props.param.render.lighting.specularPower, lightingDisabled) }
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
        val errorIndex = state.transformError?.index ?: Int.MAX_VALUE
        for ((i, transform) in state.transforms.withIndex()) {
            controlRow("${i + 1}:") {
                dropdown<Transform> {
                    disabled = i > errorIndex
                    value = transform
                    options = Transforms
                    onChange = { value ->
                        if (value != Transform.None) {
                            props.param.render.poly.transforms.updateValue(props.param.render.poly.transforms.value.updatedAt(i, value))
                        } else {
                            props.param.render.poly.transforms.updateValue(props.param.render.poly.transforms.value.removedAt(i))
                        }
                    }
                }
                if (i == errorIndex) {
                    val isInProcess = state.transformError?.isAsync == true
                    span {
                        if (isInProcess) {
                            span("spinner") {}
                        } else {
                            +"⚠️"
                        }
                        span("tooltip-text") {
                            if (isInProcess) {
                                +"Transformation is running, done ${state.transformProgress}%"
                            } else {
                                +"${state.transformError?.message}"
                            }
                        }
                    }
                }
            }
        }
        controlRow("${state.transforms.size + 1}:") {
            dropdown<Transform> {
                disabled = errorIndex < state.transforms.size
                value = Transform.None
                options = Transforms
                onChange = { value ->
                    if (value != Transform.None) {
                        props.param.render.poly.transforms.updateValue(props.param.render.poly.transforms.value + value)
                    }
                }
            }
        }
        for (i in state.transforms.size + 1..8) controlRow("${i + 1}:") {}
        controlRow("") {
            button {
                attrs {
                    onClickFunction = { props.param.render.poly.transforms.updateValue(emptyList()) }
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
