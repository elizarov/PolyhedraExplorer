package polyhedra.web.main

import androidx.compose.runtime.Composable
import org.jetbrains.compose.web.dom.*
import polyhedra.model.poly.*
import polyhedra.model.util.fmtFix
import polyhedra.model.util.toDegrees
import polyhedra.web.catalog.Drop
import polyhedra.web.components.observe
import polyhedra.web.params.SetParam
import polyhedra.web.params.TransientParam
import polyhedra.web.poly.*

@Composable
fun PolyInfo(params: RenderParams, popup: Popup?, togglePopup: (Popup?) -> Unit) {
    params.poly.observe()
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
internal fun <K : AnyKind> OrbitInfoRow(
    kind: K,
    selectedKind: TransientParam<K?>,
    content: @Composable () -> Unit,
) {
    selectedKind.observe()
    val selected = selectedKind.value == kind
    Tr(attrs = {
        classes("info", *(if (selected) arrayOf("selected") else emptyArray()))
        onMouseOver { selectedKind.updateValue(kind) }
        onMouseOut {
            if (selectedKind.value == kind) selectedKind.updateValue(null)
        }
    }) {
        content()
    }
}

@Composable
private fun FacesPopup(params: RenderParams, poly: Polyhedron) {
    params.view.faceRim.observe()
    val faceRim = params.view.faceRim.targetValue
    Aside(attrs = { classes("fev") }) {
        Table {
            Tbody {
                InfoHeader("Faces", poly.fs.size, poly.inradius, "inradius", 9) {
                    AllFacesVisibilityControl(params.poly.hideFaces, poly.faceKinds.keys)
                }
                for ((kind, face) in poly.faceKinds) {
                    val essence = face.essence()
                    OrbitInfoRow(kind, params.poly.selectedFace) {
                        Td {
                            if (!essence.isPlanar) {
                                Span(attrs = { classes("msg") }) { MessageSpan(FaceNotPlanar()) }
                            } else {
                                FaceVisibilityControl(
                                    params.poly.hideFaces,
                                    kind,
                                    faceRim >= poly.faceRim(face).maxRim,
                                )
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
internal fun AllFacesVisibilityControl(
    hiddenFacesParam: SetParam<FaceKind>,
    faceKinds: Set<FaceKind>,
) {
    hiddenFacesParam.observe()
    val hiddenFaces = hiddenFacesParam.value
    val icon = when {
        hiddenFaces.isEmpty() -> "fa-circle"
        hiddenFaces.containsAll(faceKinds) -> "fa-circle-o"
        else -> "fa-dot-circle-o"
    }
    I(attrs = {
        classes("fa", icon)
        onClick {
            hiddenFacesParam.updateValue(if (hiddenFaces.isEmpty()) faceKinds else emptySet())
        }
    })
}

@Composable
internal fun FaceVisibilityControl(
    hiddenFacesParam: SetParam<FaceKind>,
    kind: FaceKind,
    attentionWhenHidden: Boolean,
) {
    hiddenFacesParam.observe()
    val hiddenFaces = hiddenFacesParam.value
    val hidden = kind in hiddenFaces
    val icon = when {
        hidden && attentionWhenHidden -> arrayOf("fa", "fa-exclamation-circle", "face-attn")
        hidden -> arrayOf("fa", "fa-circle-o")
        else -> arrayOf("fa", "fa-circle")
    }
    I(attrs = {
        classes(*icon)
        onClick {
            hiddenFacesParam.updateValue(if (hidden) hiddenFaces - kind else hiddenFaces + kind)
        }
    })
}

@Composable
private fun EdgesPopup(params: RenderParams, poly: Polyhedron) {
    Aside(attrs = { classes("fev") }) {
        Table {
            Tbody {
                InfoHeader("Edges", poly.es.size, poly.midradius, "midradius", 7)
                for ((kind, edge) in poly.edgeKinds) {
                    val essence = edge.essence()
                    OrbitInfoRow(kind, params.poly.selectedEdge) {
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
                    OrbitInfoRow(kind, params.poly.selectedVertex) {
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
