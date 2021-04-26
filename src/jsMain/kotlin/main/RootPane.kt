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

@Suppress("NON_EXPORTABLE_TYPE")
@JsExport
class RootPane(props: PComponentProps<RootParams>) : RComponent<PComponentProps<RootParams>, RState>(props) {
    private inner class Context(params: RootParams) : Param.Context(params, Param.TargetValue + Param.Progress) {
        val transforms by { params.render.poly.transforms.value }

        val poly by { params.render.poly.poly }
        val polyName by { params.render.poly.polyName }
        val transformWarnings by { params.render.poly.transformWarnings }
        val transformError by { params.render.poly.transformError }
        val transformProgress by { params.render.poly.transformProgress }

        val hasFaces by { params.render.view.display.value.hasFaces() }
        val animateUpdates by { params.animationParams.animateValueUpdates.value }
        val rotate by { params.animationParams.animatedRotation.value  }

        val scale by { params.export.size.targetValue / 2 }
        val faceWidth by { params.render.view.faceWidth.targetValue }
        val faceRim by { params.render.view.faceRim.targetValue }
        val expandFaces by { params.render.view.expandFaces.targetValue }

        init { setup() }

        override fun update() {
            forceUpdate()
        }
    }

    private val ctx = Context(props.params)
    private lateinit var faces: FaceContext

    override fun componentWillUnmount() {
        ctx.destroy()
    }

    override fun RBuilder.render() {
        div("main-layout") {
            div("control-column card") {
                renderControls()
            }
            div("canvas-column card") {
                // Canvas & Info
                popupHeader(ctx.polyName)
                polyCanvas("poly") {
                    params = props.params.render
                    poly = ctx.poly
                    faceContextSink = { setState { faces = it } }
                }
                polyInfoPane {
                    params = props.params.render
                }
            }
        }
    }

    private fun RDOMBuilder<DIV>.renderControls() {
        popupHeader("Polyhedron")
        div("row control") {
            label { +"Seed" }
            pDropdown(props.params.render.poly.seed)
        }

        popupHeader("Transforms")
        tableBody {
            renderTransformsRows()
        }

        popupHeader("View")
        tableBody {
            controlRow("Base scale") { pDropdown(props.params.render.poly.baseScale) }
            controlRow("View scale") { pSlider(props.params.render.view.scale) }
            controlRow("Expand") { pSlider(props.params.render.view.expandFaces) }
            controlRow("Display") { pDropdown(props.params.render.view.display) }
        }

        popupHeader("Faces")
        tableBody {
            controlRow("Transparent") { pSlider(props.params.render.view.transparentFaces, !ctx.hasFaces) }
            controlRow("Width") {
                pSlider(props.params.render.view.faceWidth, !ctx.hasFaces, showValue = false)
                span { +"${(ctx.scale * ctx.faceWidth).fmt(1)} (mm)" }
            }
            controlRow("Rim") {
                pSlider(props.params.render.view.faceRim, !ctx.hasFaces, showValue = false)
                span { +"${(ctx.scale * ctx.faceRim).fmt(1)} (mm)" }
            }
        }

        popupHeader("Animation")
        tableBody {
            controlRow2("Rotation", { pCheckbox(props.params.animationParams.animatedRotation) }) {
                pSlider(props.params.animationParams.rotationSpeed, !ctx.rotate)
            }
            controlRow2("Angle", {}, {
                pSlider(props.params.animationParams.rotationAngle, !ctx.rotate)
            })
            controlRow2("Updates", { pCheckbox(props.params.animationParams.animateValueUpdates) }) {
                pSlider(props.params.animationParams.animationDuration, !ctx.animateUpdates)
            }
        }

        popupHeader("Lighting")
        tableBody {
            controlRow("Ambient") { pSlider(props.params.render.lighting.ambientLight, !ctx.hasFaces) }
            controlRow("Diffuse") { pSlider(props.params.render.lighting.diffuseLight, !ctx.hasFaces) }
            controlRow("Specular") { pSlider(props.params.render.lighting.specularLight, !ctx.hasFaces) }
            controlRow("Shininess") { pSlider(props.params.render.lighting.specularPower, !ctx.hasFaces) }
        }

        popupHeader("Export geometry")
        div("control row") {
            button {
                attrs {
                    onClickFunction = {
                        val name = exportName()
                        val description = props.params.toString()
                        download("$name.scad", ctx.poly.exportGeometryToScad(name, description))
                    }
                }
                +"Export to SCAD"
            }
        }

        popupHeader("Export solid")
        div("control row") {
            label { +"Export size" }
            pSlider(props.params.export.size, !ctx.hasFaces)
            span("suffix") { +"(mm)" }
        }
        div("control row") {
            button {
                attrs {
                    disabled = !ctx.hasFaces
                    onClickFunction = {
                        val name = exportName()
                        val description = props.params.toString()
                        val exportParams = FaceExportParams(ctx.scale, ctx.faceWidth, ctx.faceRim, ctx.expandFaces)
                        download("$name.stl",
                            faces.exportSolidToStl(name, description, exportParams)
                        )
                    }
                }
                +"Export to STL"
            }
        }
    }

    private fun exportName() = ctx.polyName.replace(' ', '_').lowercase()

    private fun RDOMBuilder<TBODY>.renderTransformsRows() {
        val transformError = ctx.transformError
        val errorIndex = transformError?.index ?: Int.MAX_VALUE
        for ((i, transform) in ctx.transforms.withIndex()) {
            controlRow("${i + 1}:") {
                dropdown<Transform> {
                    disabled = i > errorIndex
                    value = transform
                    options = Transforms.toSet() + transform
                    onChange = { value ->
                        if (value != Transform.None) {
                            props.params.render.poly.transforms.updateValue(props.params.render.poly.transforms.value.updatedAt(i, value))
                        } else {
                            props.params.render.poly.transforms.updateValue(props.params.render.poly.transforms.value.removedAt(i))
                        }
                    }
                }
                if (i == errorIndex) {
                    val isInProcess = transformError?.isAsync == true
                    if (isInProcess) {
                        span {
                            span("spinner") {}
                            +"${ctx.transformProgress}%"
                            span("tooltip-text") { +"Transformation is running" }
                        }
                    } else {
                        transformError?.msg?.let { messageSpan(it) }
                    }
                } else {
                    val warning = ctx.transformWarnings.getOrNull(i)
                    if (warning != null) messageSpan(warning)
                }
            }
        }
        controlRow("${ctx.transforms.size + 1}:") {
            dropdown<Transform> {
                disabled = errorIndex < ctx.transforms.size
                value = Transform.None
                options = Transforms
                onChange = { value ->
                    if (value != Transform.None) {
                        props.params.render.poly.transforms.updateValue(props.params.render.poly.transforms.value + value)
                    }
                }
            }
        }
        for (i in ctx.transforms.size + 1..8) controlRow("${i + 1}:") {}
        controlRow("") {
            button {
                attrs {
                    onClickFunction = { props.params.render.poly.transforms.updateValue(emptyList()) }
                }
                +"Clear"
            }
        }
    }
}

