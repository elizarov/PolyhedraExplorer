/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.js.main

import kotlinx.html.js.*
import polyhedra.common.poly.*
import polyhedra.common.util.*
import polyhedra.js.components.*
import polyhedra.js.poly.*
import react.*
import react.dom.*

external interface PolyInfoState : RState {
    var poly: Polyhedron
}

fun RBuilder.polyInfoPane(builder: PComponentProps<PolyParams>.() -> Unit) {
    child(PolyInfoPane::class) {
        attrs(builder)
    }
}

class PolyInfoPane(params: PComponentProps<PolyParams>) : PComponent<PolyParams, PComponentProps<PolyParams>, PolyInfoState>(params) {
    override fun PolyInfoState.init(props: PComponentProps<PolyParams>) {
        poly = props.param.poly
    }

    private fun RBuilder.infoHeader(name: String, cnt: Int, distValue: Double, distName: String) {
        tr("header") {
            td {
                attrs { colSpan = "2" }
                +name
            }
            td { +cnt.toString() }
            td { +distValue.fmtFix }
            td {
                attrs { colSpan = "5" }
                +distName
            }
        }
    }

    override fun RBuilder.render() {
        val poly = state.poly
        val hideFaces = props.param.hideFaces.value
        table {
            tbody {
                // Faces
                infoHeader("Faces", poly.fs.size, poly.inradius, "inradius")
                for ((fk, f0) in poly.faceKinds) {
                    val fe = f0.essence()
                    tr("info") {
                        attrs {
                            onMouseOverFunction = { props.param.selectedFace.updateValue(fk) }
                            onMouseOutFunction = { props.param.selectedFace.updateValue(null) }
                        }
                        td {
                            if (!fe.isPlanar) {
                                messageSpan(FaceNotPlanar())
                            } else {
                                val hidden = fk in hideFaces
                                val icon = if (hidden) "fa-eye-slash" else "fa-eye"
                                i("far $icon") {
                                    attrs {
                                        onClickFunction = {
                                            props.param.hideFaces.updateValue(
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