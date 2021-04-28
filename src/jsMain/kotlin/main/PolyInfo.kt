package polyhedra.js.main

import kotlinx.html.*
import kotlinx.html.js.*
import polyhedra.common.poly.*
import polyhedra.common.transform.*
import polyhedra.common.util.*
import polyhedra.js.components.*
import polyhedra.js.params.*
import polyhedra.js.poly.*
import react.*
import react.dom.*

external interface PolyInfoProps : PComponentProps<RenderParams> {
    var popup: Popup?
    var togglePopup: (Popup?) -> Unit
}

fun RBuilder.polyInfo(handler: PolyInfoProps.() -> Unit) {
    child(PolyInfo::class) {
        attrs(handler)
    }
}

class PolyInfo(props: PolyInfoProps) : RComponent<PolyInfoProps, RState>(props) {
    private inner class Context(params: RenderParams) : Param.Context(params, Param.TargetValue) {
        val poly by { params.poly.poly }
        val hideFaces by { params.poly.hideFaces.value }
        val faceRim by { params.view.faceRim.targetValue }

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
        val fev = ctx.poly.fev()
        div("fev") {
            div("btn") {
                button(classes = "txt") {
                    onClick { props.togglePopup(Popup.Faces) }
                    +"F: ${fev.f}"
                }
                div("sep") {}
                button(classes = "txt") {
                    onClick { props.togglePopup(Popup.Edges) }
                    +"E: ${fev.e}"
                }
                div("sep") {}
                button(classes = "txt") {
                    onClick { props.togglePopup(Popup.Vertices) }
                    +"V: ${fev.v}"
                }
            }
        }
        when (props.popup) {
            Popup.Faces -> facesPopup()
            Popup.Edges -> edgesPopup()
            Popup.Vertices -> verticesPopup()
        }
    }

    private fun RBuilder.infoHeader(
        name: String,
        cnt: Int,
        distValue: Double,
        distName: String,
        nColumns: Int,
        controls: RDOMBuilder<TD>.() -> Unit = {}
    ) {
        tr("header") {
            td { controls() }
            td { +name }
            td { +cnt.toString() }
            td { +distValue.fmtFix }
            td("fill") {
                attrs { colSpan = (nColumns - 4).toString() }
                +distName
            }
        }
    }
    
    private fun RBuilder.facesPopup() {
        aside("fev") {
            val poly = ctx.poly
            val hideFaces = ctx.hideFaces
            table {
                tbody {
                    // Faces
                    infoHeader("Faces", poly.fs.size, poly.inradius, "inradius", 9) {
                        val icon = when {
                            hideFaces.isEmpty() -> "fa-circle"
                            hideFaces.containsAll(poly.faceKinds.keys) -> "fa-circle-o"
                            else -> "fa-dot-circle-o"
                        }
                        i("fa $icon") {
                            attrs {
                                onClickFunction = {
                                    props.params.poly.hideFaces.updateValue(
                                        when {
                                            hideFaces.isEmpty() -> poly.faceKinds.keys
                                            else -> emptySet()
                                        }
                                    )
                                }
                            }
                        }
                    }
                    for ((fk, f0) in poly.faceKinds) {
                        val fe = f0.essence()
                        tr("info") {
                            attrs {
                                onMouseOverFunction = { props.params.poly.selectedFace.updateValue(fk) }
                                onMouseOutFunction = { props.params.poly.selectedFace.updateValue(null) }
                            }
                            td {
                                if (!fe.isPlanar) {
                                    span("msg") {
                                        messageSpan(FaceNotPlanar())
                                    }
                                } else {
                                    val hidden = fk in hideFaces
                                    val icon = when {
                                        hidden && ctx.faceRim >= poly.faceRim(f0).maxRim -> "fa-exclamation-circle face-attn"
                                        hidden -> "fa-circle-o"
                                        else -> "fa-circle"
                                    }
                                    i("fa $icon") {
                                        attrs {
                                            onClickFunction = {
                                                props.params.poly.hideFaces.updateValue(
                                                    if (hidden) hideFaces - fk else hideFaces + fk
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            td("rt") { +fk.toString() }
                            td { +poly.faceKindCount[fk].toString() }
                            td { +fe.dist.fmtFix }
                            td {
                                svgPolygon(
                                    classes = "figure",
                                    figure = fe.figure,
                                    stroke = PolyStyle.edgeColor,
                                    fill = PolyStyle.faceColor(f0)
                                )
                            }
                            td("rt") { +"adj" }
                            td { +fe.vfs.size.toString() }
                            td("fill") {
                                +fe.vfs.joinToString(" ", "[", "]")
                            }
                            td { drop(poly, fk) }
                        }
                    }
                }
            }
        }
    }

    private fun RBuilder.edgesPopup() {
        aside("fev") {
            val poly = ctx.poly
            table {
                tbody {
                    // Edges
                    infoHeader("Edges", poly.es.size, poly.midradius, "midradius", 7)
                    for ((ek, e0) in poly.edgeKinds) {
                        val ee = e0.essence()
                        tr("info") {
                            td("rt") {
                                attrs { colSpan = "2" }
                                +ek.toString()
                            }
                            td { +poly.edgeKindCount[ek].toString() }
                            td { +ee.dist.fmtFix }
                            td { +"len ${ee.len.fmtFix}" }
                            td("fill") { +"⦦ ${ee.dihedralAngle.toDegrees().fmtFix(2)}°"}
                            td { drop(poly, ek) }
                        }
                    }
                }
            }
        }
    }

    private fun RBuilder.verticesPopup() {
        aside("fev") {
            val poly = ctx.poly
            table {
                tbody {
                    // Vertices
                    infoHeader("Vertices", poly.vs.size, poly.circumradius, "circumradius", 9)
                    for ((vk, v0) in poly.vertexKinds) {
                        val ve = v0.essence()
                        tr("info") {
                            td("rt") {
                                attrs { colSpan = "2" }
                                +vk.toString()
                            }
                            td { +poly.vertexKindCount[vk].toString() }
                            td { +ve.dist.fmtFix }
                            td {
                                svgPolygon(
                                    classes = "figure",
                                    figure = ve.figure,
                                    stroke = PolyStyle.edgeColor,
                                    fill = PolyStyle.vertexColor(v0)
                                )
                            }
                            td("rt") { +"adj" }
                            td { +ve.vfs.size.toString() }
                            td("fill") {
                                +ve.vfs.joinToString(" ", "[", "]")
                            }
                            td { drop(poly, vk) }
                        }
                    }
                }
            }
        }
    }

    private fun RBuilder.drop(poly: Polyhedron, kind: AnyKind) {
        if (kind !in poly.canDrop) return
        i("fa fa-remove") {
            attrs {
                onClickFunction = {
                    props.params.poly.transforms.updateValue(props.params.poly.transforms.value + Drop(kind))
                }
            }
        }
    }
}