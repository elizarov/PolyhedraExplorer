package polyhedra.web.main

import androidx.compose.runtime.Composable
import org.jetbrains.compose.web.dom.*
import polyhedra.model.api.CoreSymmetry
import polyhedra.model.poly.*
import polyhedra.model.util.fmtFix
import polyhedra.model.util.toDegrees
import polyhedra.web.catalog.OrbitTargetedOperation
import polyhedra.web.catalog.Transform
import polyhedra.web.catalog.orbitTargetOrNull
import polyhedra.web.components.observe
import polyhedra.web.params.BooleanParam
import polyhedra.web.params.SetParam
import polyhedra.web.params.TransientParam
import polyhedra.web.poly.*

@Composable
fun PolyInfo(params: RenderParams, popup: Popup?, togglePopup: (Popup?) -> Unit) {
    params.poly.observe()
    val poly = requireNotNull(params.poly.poly)
    val fev = poly.fev()
    val symmetry = requireNotNull(params.poly.symmetry)

    Div(attrs = { classes("bottom-inspection-controls") }) {
        BottomFacesVisibilityControl(params.poly.hideFaces, poly.faceKinds.keys)
        Div(attrs = { classes("fev") }) {
            Div(attrs = { classes("btn", "left", *activeWhen(popup, Popup.Faces)) }) {
                InfoButton(
                    "F: ${elementCount(fev.f, symmetry.orbitCounts.f)}",
                    "Faces",
                    popup == Popup.Faces,
                ) { togglePopup(Popup.Faces) }
            }
            Div(attrs = { classes("btn", "mid", *activeWhen(popup, Popup.Edges)) }) {
                Div(attrs = { classes("sep") })
                InfoButton(
                    "E: ${elementCount(fev.e, symmetry.orbitCounts.e)}",
                    "Edges",
                    popup == Popup.Edges,
                ) { togglePopup(Popup.Edges) }
                Div(attrs = { classes("sep") })
            }
            Div(attrs = { classes("btn", "right", *activeWhen(popup, Popup.Vertices)) }) {
                InfoButton(
                    "V: ${elementCount(fev.v, symmetry.orbitCounts.v)}",
                    "Vertices",
                    popup == Popup.Vertices,
                ) { togglePopup(Popup.Vertices) }
            }
        }
        SymmetryControl(symmetry, params.poly.showSymmetry)
    }

    when (popup) {
        Popup.Faces -> FacesPopup(params, poly)
        Popup.Edges -> EdgesPopup(params, poly)
        Popup.Vertices -> VerticesPopup(params, poly)
        else -> Unit
    }
}

internal fun elementCount(total: Int, orbits: Int): String =
    if (orbits > 1) "$total/$orbits" else total.toString()

@Composable
internal fun SymmetryControl(symmetry: CoreSymmetry, showSymmetry: BooleanParam) {
    showSymmetry.observe()
    val planes = symmetry.reflectionPlaneNormals.size
    val axes = symmetry.rotationAxisDirections.size
    val showingSymmetry = showSymmetry.value
    val elements = buildList {
        add("$axes rotation ${if (axes == 1) "axis" else "axes"}")
        if (planes > 0) add("$planes reflection ${if (planes == 1) "plane" else "planes"}")
        else add("no reflection planes")
    }.joinToString(" and ")
    val action = "${if (showingSymmetry) "hide" else "show"} $elements"
    val tooltip = "${symmetry.group.fullName}; $action"
    Div(attrs = {
        classes("btn", "symmetry", *(if (showingSymmetry) arrayOf("active") else emptyArray()))
    }) {
        Button(attrs = {
            classes("txt")
            attr("aria-label", tooltip)
            onClick { showSymmetry.updateValue(!showingSymmetry) }
        }) {
            Text(symmetry.group.compactName)
            Aside(attrs = { classes("tooltip-text") }) { Text(tooltip) }
        }
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
                        OrbitTargetActions(params.poly, kind)
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
    floating: Boolean = false,
) {
    hiddenFacesParam.observe()
    val hiddenFaces = hiddenFacesParam.value
    val icon = when {
        hiddenFaces.isEmpty() -> "fa-circle"
        hiddenFaces.containsAll(faceKinds) -> "fa-circle-o"
        else -> "fa-dot-circle-o"
    }
    val tooltip = if (hiddenFaces.isEmpty()) "Hide all face orbits" else "Show all face orbits"
    Button(attrs = {
        classes("face-visibility-toggle", *(if (floating) arrayOf("square") else emptyArray()))
        attr("aria-label", tooltip)
        onClick {
            hiddenFacesParam.updateValue(if (hiddenFaces.isEmpty()) faceKinds else emptySet())
        }
    }) {
        I(attrs = { classes("fa", icon) })
        Aside(attrs = { classes("tooltip-text") }) { Text(tooltip) }
    }
}

@Composable
internal fun BottomFacesVisibilityControl(
    hiddenFacesParam: SetParam<FaceKind>,
    faceKinds: Set<FaceKind>,
) {
    Div(attrs = { classes("btn", "faces-visibility") }) {
        AllFacesVisibilityControl(hiddenFacesParam, faceKinds, floating = true)
    }
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
                InfoHeader("Edges", poly.es.size, poly.midradius, "midradius", 8)
                for ((kind, edge) in poly.edgeKinds) {
                    val essence = edge.essence()
                    OrbitInfoRow(kind, params.poly.selectedEdge) {
                        Td(attrs = { classes("rt"); attr("colspan", "2") }) { Text(kind.toString()) }
                        Td { Text(poly.edgeKindCount[kind].toString()) }
                        Td { Text(essence.dist.fmtFix) }
                        Td { SvgEdgeNet("figure edge-figure", edge, PolyStyle.edgeColor) }
                        Td { Text("len ${essence.len.fmtFix}") }
                        Td(attrs = { classes("fill") }) { Text("∠ ${essence.dihedralAngle.toDegrees().fmtFix(2)}°") }
                        OrbitTargetActions(params.poly, kind)
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
                        OrbitTargetActions(params.poly, kind)
                    }
                }
            }
        }
    }
}

@Composable
internal fun OrbitTargetActions(params: PolyParams, kind: AnyKind) {
    val transformsByOperation = params.currentOrbitTransforms.mapNotNull { transform ->
        transform.orbitTargetOrNull()
            ?.takeIf { target -> target.kind == kind }
            ?.let { target -> target.operation to transform }
    }.toMap()

    Td(attrs = { classes("orbit-target-actions") }) {
        for (operation in OrbitTargetedOperation.entries) {
            transformsByOperation[operation]?.let { transform ->
                OrbitTargetAction(params, operation, transform, kind)
            }
        }
    }
}

@Composable
private fun OrbitTargetAction(
    params: PolyParams,
    operation: OrbitTargetedOperation,
    transform: Transform,
    kind: AnyKind,
) {
    val tooltip = "${operation.optionName} orbit $kind"
    Button(attrs = {
        classes("orbit-target-action")
        attr("aria-label", tooltip)
        onClick { params.transforms.updateValue(params.transforms.value + transform) }
    }) {
        I(attrs = { classes("fa", operation.iconClass) })
        Aside(attrs = { classes("tooltip-text") }) { Text(tooltip) }
    }
}
