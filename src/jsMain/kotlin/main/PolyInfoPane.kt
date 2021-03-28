/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.js.main

import polyhedra.common.poly.*
import polyhedra.common.util.*
import polyhedra.js.poly.*
import react.*
import react.dom.*

external interface PolyInfoPaneProps : RProps {
    var poly: Polyhedron
}

fun RBuilder.polyInfoPane(builder: PolyInfoPaneProps.() -> Unit) {
    child(PolyInfoPane::class) {
        attrs(builder)
    }
}

class PolyInfoPane : RPureComponent<PolyInfoPaneProps, RState>() {
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
        val poly = props.poly
        table {
            tbody {
                // Faces
                infoHeader("Faces", poly.fs.size, poly.inradius, "inradius")
                for ((fk, fs) in poly.faceKinds) {
                    val f0 = fs[0]
                    val fe = f0.essence()
                    tr("info") {
                        td {
                            if (!fe.isPlanar) messageSpan(FaceNotPlanar())
                        }
                        td("rt") { +fk.toString() }
                        td { +fs.size.toString() }
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
                for ((vk, vs) in poly.vertexKinds) {
                    val v0 = vs[0]
                    val ve = v0.essence()
                    tr("info") {
                        td("rt") {
                            attrs { colSpan = "2" }
                            +vk.toString()
                        }
                        td { +vs.size.toString() }
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
                for ((ek, es) in poly.edgeKinds) {
                    val ee = es[0].essence()
                    tr("info") {
                        td("rt") {
                            attrs { colSpan = "2" }
                            +ek.toString()
                        }
                        td { +es.size.toString() }
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