/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.js.main

import kotlinx.html.*
import kotlinx.html.js.*
import polyhedra.common.poly.*
import polyhedra.common.util.*
import polyhedra.js.components.*
import polyhedra.js.params.*
import polyhedra.js.poly.*
import react.*
import react.dom.*

fun RBuilder.polyInfoPane(builder: PComponentProps<RenderParams>.() -> Unit) {
    child(PolyInfoPane::class) {
        attrs(builder)
    }
}

class PolyInfoPane(props: PComponentProps<RenderParams>) : RComponent<PComponentProps<RenderParams>, RState>(props) {
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

    private fun RBuilder.infoHeader(
        name: String,
        cnt: Int,
        distValue: Double,
        distName: String,
        controls: RDOMBuilder<TD>.() -> Unit = {}
    ) {
        tr("header") {
            td { controls() }
            td { +name }
            td { +cnt.toString() }
            td { +distValue.fmtFix }
            td {
                attrs { colSpan = "5" }
                +distName
            }
        }
    }

    override fun RBuilder.render() {
        val poly = ctx.poly
        val hideFaces = ctx.hideFaces
        table {
            tbody {
                // Faces
                infoHeader("Faces", poly.fs.size, poly.inradius, "inradius") {
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
                                messageSpan(FaceNotPlanar())
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
                        td {
                            attrs { colSpan = "2" }
                            +fe.vfs.joinToString(" ", "[", "]")
                        }
                    }
                }
                // Vertices
                infoHeader("Vertices", poly.vs.size, poly.circumradius, "circumradius")
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
                        td {
                            attrs { colSpan = "2" }
                            +ve.vfs.joinToString(" ", "[", "]")
                        }
                    }
                }
                // Edges
                infoHeader("Edges", poly.es.size, poly.midradius, "midradius")
                for ((ek, e0) in poly.edgeKinds) {
                    val ee = e0.essence()
                    tr("info") {
                        td("rt") {
                            attrs { colSpan = "2" }
                            +ek.toString()
                        }
                        td { +poly.edgeKindCount[ek].toString() }
                        td { +ee.dist.fmtFix }
                        td {}
                        td {}
                        td {}
                        td { +"len ${ee.len.fmtFix}" }
                        td { +"⦦ ${ee.dihedralAngle.toDegrees().fmtFix(2)}°"}
                    }
                }
            }
        }
    }
}