/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.js.main

import polyhedra.common.poly.*
import polyhedra.common.util.*
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
            td { +name }
            td { +cnt.toString() }
            td { +distValue.fmtFix }
            td {
                attrs { colSpan = "3" }
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
                    val fe = poly.faceEssence(fs[0])
                    tr("info") {
                        td("rt") { +"$fk faces" }
                        td { +fs.size.toString() }
                        td { +fe.dist.fmtFix }
                        td("rt") { +"adj" }
                        td { +fe.vfs.size.toString() }
                        td { +fe.vfs.joinToString(" ", "[", "]") }
                    }
                }
                // Vertices
                infoHeader("Vertices", poly.vs.size, poly.circumradius, "circumradius")
                for ((vk, vs) in poly.vertexKinds) {
                    val ve = poly.vertexEssence(vs[0])
                    tr("info") {
                        td("rt") { +"$vk vertices" }
                        td { +vs.size.toString() }
                        td { +ve.dist.fmtFix }
                        td("rt") { +"adj" }
                        td { +ve.vfs.size.toString() }
                        td { +ve.vfs.joinToString(" ", "[", "]") }
                    }
                }
                // Edges
                infoHeader("Edges", poly.es.size, poly.midradius, "midradius")
                for ((ek, es) in poly.edgeKinds) {
                    val e = es[0]
                    tr("info") {
                        td("rt") { +"$ek edges" }
                        td { +es.size.toString() }
                        td { +e.midPoint(MidPoint.Closest).norm.fmtFix }
                        td {}
                        td {}
                        td { +"len ${e.len.fmtFix}" }
                    }
                }
            }
        }
    }
}