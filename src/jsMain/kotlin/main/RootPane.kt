/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.js.main

import kotlinx.html.*
import kotlinx.html.js.*
import polyhedra.common.transform.*
import polyhedra.common.util.*
import polyhedra.js.components.*
import polyhedra.js.params.*
import polyhedra.js.poly.*
import react.*
import react.dom.*

external interface RootPaneState : RState {
    var faceContext: FaceContext
    var context: RootPane.Context
}

@Suppress("NON_EXPORTABLE_TYPE")
@JsExport
class RootPane(props: PComponentProps<RootParams>) : RComponent<PComponentProps<RootParams>, RootPaneState>(props) {
    override fun RootPaneState.init(props: PComponentProps<RootParams>) {
        context = Context(props.param)
    }

    inner class Context(params: RootParams) : Param.Context(params, Param.TargetValue + Param.Progress) {
        val transforms by { params.render.poly.transforms.value }

        val poly by { params.render.poly.poly }
        val polyName by { params.render.poly.polyName }
        val transformWarnings by { params.render.poly.transformWarnings }
        val transformError by { params.render.poly.transformError }
        val transformProgress by { params.render.poly.transformProgress }

        val hasFaces by { params.render.view.display.value.hasFaces() }
        val animateUpdates by { params.animationParams.animateValueUpdates.value }
        val rotate by { params.animationParams.animatedRotation.value  }

        val exportWidth by { params.render.view.faceWidth.targetValue * params.export.size.targetValue }
        val exportRim by { params.render.view.faceRim.targetValue * params.export.size.targetValue }

        init { setup() }

        override fun update() {
            forceUpdate()
        }
    }

    override fun RBuilder.render() {
        div("main-layout") {
            div("control-column card") {
                renderControls()
            }
            div("canvas-column card") {
                // Canvas & Info
                header(state.context.polyName)
                polyCanvas("poly") {
                    params = props.param.render
                    poly = state.context.poly
                    faceContextSink = { setState { faceContext = it } }
                }
                polyInfoPane {
                    param = props.param.render.poly
                }
            }
        }
    }

    private fun RDOMBuilder<DIV>.renderControls() {
        val context = state.context

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
        tableBody {
            controlRow("Base scale") { pDropdown(props.param.render.poly.baseScale) }
            controlRow("View scale") { pSlider(props.param.render.view.scale) }
            controlRow("Expand") { pSlider(props.param.render.view.expandFaces) }
            controlRow("Display") { pDropdown(props.param.render.view.display) }
        }

        header("Faces")
        tableBody {
            controlRow("Transparent") { pSlider(props.param.render.view.transparentFaces, !context.hasFaces) }
            controlRow("Width") { pSlider(props.param.render.view.faceWidth, !context.hasFaces) }
            controlRow("Rim") { pSlider(props.param.render.view.faceRim, !context.hasFaces) }
        }

        header("Animation")
        tableBody {
            controlRow2("Rotation", { pCheckbox(props.param.animationParams.animatedRotation) }) {
                pSlider(props.param.animationParams.rotationSpeed, !context.rotate)
            }
            controlRow2("Angle", {}, {
                pSlider(props.param.animationParams.rotationAngle, !context.rotate)
            })
            controlRow2("Updates", { pCheckbox(props.param.animationParams.animateValueUpdates) }) {
                pSlider(props.param.animationParams.animationDuration, !context.animateUpdates)
            }
        }

        header("Lighting")
        tableBody {
            controlRow("Ambient") { pSlider(props.param.render.lighting.ambientLight, !context.hasFaces) }
            controlRow("Diffuse") { pSlider(props.param.render.lighting.diffuseLight, !context.hasFaces) }
            controlRow("Specular") { pSlider(props.param.render.lighting.specularLight, !context.hasFaces) }
            controlRow("Shininess") { pSlider(props.param.render.lighting.specularPower, !context.hasFaces) }
        }

        header("Export geometry")
        div("control row") {
            button {
                attrs {
                    onClickFunction = {
                        val name = exportName()
                        val description = props.param.toString()
                        download("$name.scad", context.poly.exportGeometryToScad(name, description))
                    }
                }
                +"Export to SCAD"
            }
        }

        header("Export solid")
        div("control row") {
            label { +"Export size" }
            pSlider(props.param.export.size, !context.hasFaces)
            span("suffix") { +"(mm)" }
        }
        div("control row") {
            +"Face width ${context.exportWidth.fmt(1)} (mm); rim ${context.exportRim.fmt(1)} (mm)"
        }
        div("control row") {
            button {
                attrs {
                    disabled = !context.hasFaces
                    onClickFunction = {
                        val name = exportName()
                        val description = props.param.toString()
                        val exportParams = FaceExportParams(
                            props.param.export.size.targetValue / 2,
                            props.param.render.view.faceWidth.targetValue,
                            props.param.render.view.faceRim.targetValue,
                            props.param.render.view.expandFaces.targetValue,
                        )
                        download("$name.stl",
                            state.faceContext.exportSolidToStl(name, description, exportParams)
                        )
                    }
                }
                +"Export to STL"
            }
        }
    }

    private fun exportName() = state.context.polyName.replace(' ', '_').lowercase()

    private fun RDOMBuilder<TBODY>.renderTransformsRows() {
        val context = state.context
        val transformError = context.transformError
        val errorIndex = transformError?.index ?: Int.MAX_VALUE
        for ((i, transform) in context.transforms.withIndex()) {
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
                    val isInProcess = transformError?.isAsync == true
                    if (isInProcess) {
                        span {
                            span("spinner") {}
                            +"${context.transformProgress}%"
                            span("tooltip-text") { +"Transformation is running" }
                        }
                    } else {
                        transformError?.msg?.let { messageSpan(it) }
                    }
                } else {
                    val warning = context.transformWarnings.getOrNull(i)
                    if (warning != null) messageSpan(warning)
                }
            }
        }
        controlRow("${context.transforms.size + 1}:") {
            dropdown<Transform> {
                disabled = errorIndex < context.transforms.size
                value = Transform.None
                options = Transforms
                onChange = { value ->
                    if (value != Transform.None) {
                        props.param.render.poly.transforms.updateValue(props.param.render.poly.transforms.value + value)
                    }
                }
            }
        }
        for (i in context.transforms.size + 1..8) controlRow("${i + 1}:") {}
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

private fun RBuilder.tableBody(block: RDOMBuilder<TBODY>.() -> Unit) {
    table {
        tbody(block = block)
    }
}

private fun RDOMBuilder<TBODY>.controlRow(label: String, block: RDOMBuilder<TD>.() -> Unit) {
    tr("control") {
        td { +label }
        td(block = block)
    }
}

private fun RDOMBuilder<TBODY>.controlRow2(
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
