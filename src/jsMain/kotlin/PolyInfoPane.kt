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
                    val fe = poly.faceEssence(fs[0])
                    div {
                        +"$fk faces: ${fs.size}, $fe"
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
                    val ve = poly.vertexEssence(vs[0])
                    div {
                        +"$vk vertices: ${vs.size}, $ve"
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
                        +"$ek edges: ${es.size}, "
                        +"distance ${e.midPoint(MidPoint.Closest).norm.fmt}, "
                        +"length ${(e.a.pt - e.b.pt).norm.fmt}"
                    }
                }
            }
        }
    }
}