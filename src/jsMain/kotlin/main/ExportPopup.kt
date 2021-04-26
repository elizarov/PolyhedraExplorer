package polyhedra.js.main

import kotlinx.html.js.*
import polyhedra.common.util.*
import polyhedra.js.components.*
import polyhedra.js.params.*
import polyhedra.js.poly.*
import react.*
import react.dom.*

fun RBuilder.exportPopup(params: RootParams, faces: FaceContext?) {
    child(ExportPopup::class) {
        attrs {
            this.params = params
            this.faces = faces
        }
    }
}

external interface ExportPopupProps : PComponentProps<RootParams> {
    var faces: FaceContext?
}

class ExportPopup(props: ExportPopupProps) : RComponent<ExportPopupProps, RState>(props) {
    private inner class Context(params: RootParams) : Param.Context(params, Param.TargetValue) {
        val poly by { params.render.poly.poly }
        val polyName by { params.render.poly.polyName }
        val hasFaces by { params.render.view.display.value.hasFaces() }
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

    override fun componentWillUnmount() {
        ctx.destroy()
    }

    override fun RBuilder.render() {
        groupHeader("Export size")
        tableBody {
            controlRow("Width") {
                pSlider(props.params.render.view.faceWidth, !ctx.hasFaces, showValue = false)
                span { +"${(ctx.scale * ctx.faceWidth).fmt(1)} (mm)" }
            }
            controlRow("Rim") {
                pSlider(props.params.render.view.faceRim, !ctx.hasFaces, showValue = false)
                span { +"${(ctx.scale * ctx.faceRim).fmt(1)} (mm)" }
            }
            controlRow("Overall size") {
                pSlider(props.params.export.size, !ctx.hasFaces)
                span("suffix") { +"(mm)" }
            }
        }

        groupHeader("Export solid")
        div("control row") {
            button {
                attrs {
                    disabled = !ctx.hasFaces
                    onClickFunction = lambda@{
                        val name = exportName()
                        val description = props.params.toString()
                        val exportParams = FaceExportParams(ctx.scale, ctx.faceWidth, ctx.faceRim, ctx.expandFaces)
                        val faces = props.faces ?: return@lambda
                        download("$name.stl",
                            faces.exportSolidToStl(name, description, exportParams)
                        )
                    }
                }
                +"Export to STL"
            }
        }

        groupHeader("Export geometry")
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
    }

    private fun exportName() = ctx.polyName.replace(' ', '_').lowercase()
}