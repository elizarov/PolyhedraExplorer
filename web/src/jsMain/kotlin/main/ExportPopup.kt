package polyhedra.web.main

import androidx.compose.runtime.*
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import polyhedra.model.api.PolyhedronContract
import polyhedra.web.components.observe
import polyhedra.web.components.PCheckbox
import polyhedra.web.components.PSlider
import polyhedra.web.poly.*
import polyhedra.web.util.oklchColor
import polyhedra.web.util.toHexString
import polyhedra.web.worker.convertStlInWasm

internal fun String.toExportFileBaseName(): String = replace(' ', '_').lowercase()

@Composable
fun ExportPopup(params: RootParams, faces: FaceContext?, onPickColor: () -> Unit = {}) {
    params.observe()
    val poly = requireNotNull(params.render.poly.poly)
    val polyName = params.render.poly.polyName
    val hasFaces = params.render.view.display.value.hasFaces()
    val scale = params.export.size.targetValue / 2
    val faceWidth = params.render.view.faceWidth.targetValue
    val faceRim = params.render.view.faceRim.targetValue
    val expandFaces = params.render.view.expandFaces.targetValue
    val effectiveHiddenFaceKinds = params.render.poly.hideFaces.value + poly.nonPlanarFaceKinds
    var stlBusy by remember { mutableStateOf(false) }
    var stlProgress by remember { mutableStateOf(0) }
    var stlError by remember { mutableStateOf<String?>(null) }
    var scadError by remember { mutableStateOf<String?>(null) }
    val stlJob = remember { arrayOfNulls<() -> Unit>(1) }

    DisposableEffect(Unit) {
        onDispose { stlJob[0]?.invoke() }
    }

    GroupHeader("Print preview")
    PrintPreviewControl(params.render.printPreview, onPickColor)

    GroupHeader("Export size")
    TableBody {
        ControlRow("Width") {
            PSlider(
                params.render.view.faceWidth,
                !hasFaces,
                valueScale = scale,
                valuePrecision = 3,
                unit = "(mm)",
                snapInputToStep = false,
                ariaLabel = "Face width in millimeters",
            )
        }
        ControlRow("Rim") {
            PSlider(
                params.render.view.faceRim,
                !hasFaces,
                valueScale = scale,
                valuePrecision = 3,
                unit = "(mm)",
                snapInputToStep = false,
                ariaLabel = "Face rim in millimeters",
            )
        }
        ControlRow("Overall size") {
            PSlider(
                params.export.size,
                !hasFaces,
                valuePrecision = 1,
                unit = "(mm)",
                ariaLabel = "Overall size in millimeters",
            )
        }
    }

    GroupHeader("Export solid")
    Div(attrs = { classes("control", "row") }) {
        Button(attrs = {
            if (!hasFaces || stlBusy) disabled()
            onClick {
                val faceContext = faces ?: return@onClick
                val name = polyName.toExportFileBaseName()
                val exportParams = FaceExportParams(scale, faceWidth, faceRim, expandFaces)
                stlError = null
                stlProgress = 0
                stlBusy = true
                runCatching { faceContext.buildStlRequest(exportParams) }
                    .onSuccess { request ->
                        stlJob[0] = convertStlInWasm(
                            request = request,
                            reportProgress = { done -> stlProgress = done },
                            onSuccess = { response ->
                                stlJob[0] = null
                                stlBusy = false
                                response.error?.let { error ->
                                    stlError = buildString {
                                        append("STL export failed during ${error.stage.name.lowercase()}: ${error.reason}. ")
                                        append("No file was created. Use Export to SCAD below for native geometry processing.")
                                    }
                                } ?: runCatching { response.toAsciiStl(name) }
                                    .onSuccess { stl -> download("$name.stl", stl) }
                                    .onFailure { cause ->
                                        stlError = "STL serialization failed: ${cause.message}. No file was created. " +
                                            "Use Export to SCAD below for native geometry processing."
                                    }
                            },
                            onFailure = { cause ->
                                stlJob[0] = null
                                stlBusy = false
                                stlError = "STL export failed: ${cause.message}. No file was created. " +
                                    "Use Export to SCAD below for native geometry processing."
                            },
                        )
                    }
                    .onFailure { cause ->
                        stlBusy = false
                        stlError = "Could not prepare STL geometry: ${cause.message}. No file was created. " +
                            "Use Export to SCAD below for native geometry processing."
                    }
            }
        }) { Text(if (stlBusy) "Preparing STL $stlProgress%" else "Export to STL") }
        stlError?.let { message ->
            Div(attrs = { classes("save-error") }) { Text(message) }
        }
    }

    GroupHeader("Export geometry")
    Div(attrs = { classes("control", "row") }) {
        Button(attrs = {
            onClick {
                val name = polyName.toExportFileBaseName()
                val exportParams = FaceExportParams(scale, faceWidth, faceRim, expandFaces)
                scadError = null
                runCatching {
                    poly.exportSolidToScad(
                        name = name,
                        description = params.toString(),
                        exportParams = exportParams,
                        hiddenFaceKinds = effectiveHiddenFaceKinds,
                        resolvedRims = params.render.poly.resolvedRims,
                        embeddedBoundary = params.render.poly.geometryAnalysis?.strongestContract ==
                            PolyhedronContract.EmbeddedBoundary,
                    )
                }.onSuccess { scad ->
                    download("$name.scad", scad)
                }.onFailure { cause ->
                    scadError = "OpenSCAD export failed: ${cause.message}. No file was created."
                }
            }
        }) { Text("Export to SCAD") }
        scadError?.let { message ->
            Div(attrs = { classes("save-error") }) { Text(message) }
        }
    }
}

@Composable
internal fun PrintPreviewControl(preview: PrintPreviewParams, onPickColor: () -> Unit) {
    preview.observe()
    val color = oklchColor(
        preview.lightness.targetValue,
        preview.chroma.targetValue,
        preview.hue.targetValue,
    )
    TableBody {
        ControlRow2("Preview", { PCheckbox(preview.enabled) }) {
            Span(attrs = {
                classes("print-color-sample")
                attr("style", "background-color: ${color.toHexString()}")
                attr("aria-label", "Selected print color ${color.toHexString()}")
            })
            Button(attrs = {
                classes("pick-print-color")
                onClick {
                    preview.enabled.updateValue(true)
                    onPickColor()
                }
            }) { Text("Pick color") }
        }
    }
}
