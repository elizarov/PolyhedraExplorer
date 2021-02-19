package polyhedra.js

import polyhedra.common.*
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
    private fun RBuilder.infoHeader(name: String, cnt: Int, distName: String, distValue: Double) {
        tr("info-header") {
            td { +name }
            td { +cnt.toString() }
            td("rt") { +distName }
            td { +distValue.fmtFix }
            repeat(3) { td {} }
        }
    }

    override fun RBuilder.render() {
        val poly = props.poly
        table {
            tbody {
                // Faces
                infoHeader("Faces", poly.fs.size, "inradius", poly.inradius)
                for ((fk, fs) in poly.faceKinds) {
                    val fe = poly.faceEssence(fs[0])
                    tr {
                        td("rt") { +"$fk faces" }
                        td { +fs.size.toString() }
                        td("rt") { +"distance" }
                        td { +fe.dist.fmtFix }
                        td("rt") { +"adj" }
                        td { +fe.vfs.size.toString() }
                        td { +fe.vfs.joinToString(" ", "[", "]") }
                    }
                }
                // Vertices
                infoHeader("Vertices", poly.vs.size, "circumradius", poly.circumradius)
                for ((vk, vs) in poly.vertexKinds) {
                    val ve = poly.vertexEssence(vs[0])
                    tr {
                        td("rt") { +"$vk vertices" }
                        td { +vs.size.toString() }
                        td("rt") { +"distance" }
                        td { +ve.dist.fmtFix }
                        td("rt") { +"adj" }
                        td { +ve.vfs.size.toString() }
                        td { +ve.vfs.joinToString(" ", "[", "]") }
                    }
                }
                // Edges
                infoHeader("Edges", poly.es.size, "midradius", poly.midradius)
                for ((ek, es) in poly.edgeKinds) {
                    val e = es[0]
                    tr {
                        td("rt") { +"$ek edges" }
                        td { +es.size.toString() }
                        td("rt") { +"distance" }
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