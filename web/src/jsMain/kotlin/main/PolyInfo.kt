package polyhedra.js.main

import androidx.compose.runtime.Composable
import org.jetbrains.compose.web.dom.*
import polyhedra.common.poly.*
import polyhedra.common.util.fmtFix
import polyhedra.common.util.toDegrees
import polyhedra.js.catalog.Drop
import polyhedra.js.components.ObserveParam
import polyhedra.js.params.Param
import polyhedra.js.poly.*

@Composable
fun PolyInfo(params: RenderParams, popup: Popup?, togglePopup: (Popup?) -> Unit) {
    ObserveParam(params, Param.TargetValue).value
    val poly = requireNotNull(params.poly.poly)
    val fev = poly.fev()

    Div(attrs = { classes("fev") }) {
        Div(attrs = { classes("btn", "left", *activeWhen(popup, Popup.Faces)) }) {
            InfoButton("F: ${fev.f}", "Faces", popup == Popup.Faces) { togglePopup(Popup.Faces) }
        }
        Div(attrs = { classes("btn", "mid", *activeWhen(popup, Popup.Edges)) }) {
            Div(attrs = { classes("sep") })
            InfoButton("E: ${fev.e}", "Edges", popup == Popup.Edges) { togglePopup(Popup.Edges) }
            Div(attrs = { classes("sep") })
        }
        Div(attrs = { classes("btn", "right", *activeWhen(popup, Popup.Vertices)) }) {
            InfoButton("V: ${fev.v}", "Vertices", popup == Popup.Vertices) { togglePopup(Popup.Vertices) }
        }
    }

    when (popup) {
        Popup.Faces -> FacesPopup(params, poly)
        Popup.Edges -> EdgesPopup(params, poly)
        Popup.Vertices -> VerticesPopup(params, poly)
        else -> Unit
    }
}

@Composable
private fun InfoButton(text: String, tooltip: String, active: Boolean, onClick: () -> Unit) {
    Button(attrs = {
        classes("txt", *(if (active) arrayOf("active") else emptyArray()))
        onClick { onClick() }
    }) {
        Text(text)
        Aside(attrs = { classes("tooltip-text") }) { Text(tooltip) }
    }
}

@Composable
private fun InfoHeader(
    name: String,
    count: Int,
    distance: Double,
    distanceName: String,
    columns: Int,
    controls: @Composable () -> Unit = {},
) {
    Tr(attrs = { classes("header") }) {
        Td { controls() }
        Td { Text(name) }
        Td { Text(count.toString()) }
        Td { Text(distance.fmtFix) }
        Td(attrs = {
            classes("fill")
            attr("colspan", (columns - 4).toString())
        }) { Text(distanceName) }
    }
}

@Composable
private fun FacesPopup(params: RenderParams, poly: Polyhedron) {
    val hiddenFaces = params.poly.hideFaces.value
    val faceRim = params.view.faceRim.targetValue
    Aside(attrs = { classes("fev") }) {
        Table {
            Tbody {
                InfoHeader("Faces", poly.fs.size, poly.inradius, "inradius", 9) {
                    val icon = when {
                        hiddenFaces.isEmpty() -> "fa-circle"
                        hiddenFaces.containsAll(poly.faceKinds.keys) -> "fa-circle-o"
                        else -> "fa-dot-circle-o"
                    }
                    I(attrs = {
                        classes("fa", icon)
                        onClick {
                            params.poly.hideFaces.updateValue(
                                if (hiddenFaces.isEmpty()) poly.faceKinds.keys else emptySet(),
                            )
                        }
                    })
                }
                for ((kind, face) in poly.faceKinds) {
                    val essence = face.essence()
                    Tr(attrs = {
                        classes("info")
                        onMouseOver { params.poly.selectedFace.updateValue(kind) }
                        onMouseOut { params.poly.selectedFace.updateValue(null) }
                    }) {
                        Td {
                            if (!essence.isPlanar) {
                                Span(attrs = { classes("msg") }) { MessageSpan(FaceNotPlanar()) }
                            } else {
                                val hidden = kind in hiddenFaces
                                val icon = when {
                                    hidden && faceRim >= poly.faceRim(face).maxRim ->
                                        arrayOf("fa", "fa-exclamation-circle", "face-attn")
                                    hidden -> arrayOf("fa", "fa-circle-o")
                                    else -> arrayOf("fa", "fa-circle")
                                }
                                I(attrs = {
                                    classes(*icon)
                                    onClick {
                                        params.poly.hideFaces.updateValue(
                                            if (hidden) hiddenFaces - kind else hiddenFaces + kind,
                                        )
                                    }
                                })
                            }
                        }
                        Td(attrs = { classes("rt") }) { Text(kind.toString()) }
                        Td { Text(poly.faceKindCount[kind].toString()) }
                        Td { Text(essence.dist.fmtFix) }
                        Td {
                            SvgPolygon("figure", essence.figure, PolyStyle.edgeColor, PolyStyle.faceColor(face))
                        }
                        Td(attrs = { classes("rt") }) { Text("adj") }
                        Td { Text(essence.vfs.size.toString()) }
                        Td(attrs = { classes("fill") }) { Text(essence.vfs.joinToString(" ", "[", "]")) }
                        Td { DropAction(params, poly, kind) }
                    }
                }
            }
        }
    }
}

@Composable
private fun EdgesPopup(params: RenderParams, poly: Polyhedron) {
    Aside(attrs = { classes("fev") }) {
        Table {
            Tbody {
                InfoHeader("Edges", poly.es.size, poly.midradius, "midradius", 7)
                for ((kind, edge) in poly.edgeKinds) {
                    val essence = edge.essence()
                    Tr(attrs = { classes("info") }) {
                        Td(attrs = { classes("rt"); attr("colspan", "2") }) { Text(kind.toString()) }
                        Td { Text(poly.edgeKindCount[kind].toString()) }
                        Td { Text(essence.dist.fmtFix) }
                        Td { Text("len ${essence.len.fmtFix}") }
                        Td(attrs = { classes("fill") }) { Text("∠ ${essence.dihedralAngle.toDegrees().fmtFix(2)}°") }
                        Td { DropAction(params, poly, kind) }
                    }
                }
            }
        }
    }
}

@Composable
private fun VerticesPopup(params: RenderParams, poly: Polyhedron) {
    Aside(attrs = { classes("fev") }) {
        Table {
            Tbody {
                InfoHeader("Vertices", poly.vs.size, poly.circumradius, "circumradius", 9)
                for ((kind, vertex) in poly.vertexKinds) {
                    val essence = vertex.essence()
                    Tr(attrs = { classes("info") }) {
                        Td(attrs = { classes("rt"); attr("colspan", "2") }) { Text(kind.toString()) }
                        Td { Text(poly.vertexKindCount[kind].toString()) }
                        Td { Text(essence.dist.fmtFix) }
                        Td {
                            SvgPolygon("figure", essence.figure, PolyStyle.edgeColor, PolyStyle.vertexColor(vertex))
                        }
                        Td(attrs = { classes("rt") }) { Text("adj") }
                        Td { Text(essence.vfs.size.toString()) }
                        Td(attrs = { classes("fill") }) { Text(essence.vfs.joinToString(" ", "[", "]")) }
                        Td { DropAction(params, poly, kind) }
                    }
                }
            }
        }
    }
}

@Composable
private fun DropAction(params: RenderParams, poly: Polyhedron, kind: AnyKind) {
    if (kind !in params.poly.currentCanDrop) return
    I(attrs = {
        classes("fa", "fa-remove")
        onClick { params.poly.transforms.updateValue(params.poly.transforms.value + Drop(kind)) }
    })
}
