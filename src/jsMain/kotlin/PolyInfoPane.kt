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

class PolyInfoPane : RComponent<PolyInfoPaneProps, RState>() {
    override fun RBuilder.render() {
        val poly = props.poly
        div {
            // Faces
            div {
                +"Faces: ${poly.fs.size}"
                +", inradius: ${poly.inradius.fmt}"
            }
            div {
                for ((fk, fs) in poly.faceKinds) {
                    val f = fs[0]
                    val vKinds = f.fvs.map { it.kind }
                    div {
                        +"$fk-faces: ${fs.size}"
                        +", distance ${f.plane.d.fmt}"
                        +", vertices ${vKinds.size} [${vKinds.joinToString("")}]"
                    }
                }
            }
            // Vertices
            div {
                +"Vertices: ${poly.vs.size}"
                +", circumradius: ${poly.circumradius.fmt}"
            }
            div {
                for ((vk, vs) in poly.vertexKinds) {
                    val v = vs[0]
                    val fKinds = poly.vertexFaces[v]!!.map { it.kind }
                    div {
                        +"$vk-vertices: ${vs.size}"
                        +", distance ${v.pt.norm.fmt}"
                        +", faces ${fKinds.size} [${fKinds.joinToString("")}]"
                    }
                }
            }
            // Edges
            div {
                +"Edges: ${poly.es.size}"
                +", midradius: ${poly.midradius.fmt}"
            }
            div {
                for ((ek, es) in poly.edgeKinds) {
                    val e = es[0]
                    div {
                        +"$ek edges: ${es.size}"
                        +", distance ${e.midPoint(MidPoint.Closest).norm.fmt}"
                        +", length ${(e.a.pt - e.b.pt).norm.fmt}"
                    }
                }
            }
        }
    }
}