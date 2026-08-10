package polyhedra.web.main

import androidx.compose.runtime.Composable
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import polyhedra.model.util.fmt
import polyhedra.web.components.observe
import polyhedra.web.components.PCheckbox
import polyhedra.web.components.PSlider
import polyhedra.web.poly.*
import polyhedra.web.util.oklchColor
import polyhedra.web.util.toHexString

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

    GroupHeader("Print preview")
    PrintPreviewControl(params.render.printPreview, onPickColor)

    GroupHeader("Export size")
    TableBody {
        ControlRow("Width") {
            PSlider(params.render.view.faceWidth, !hasFaces, showValue = false)
            Span { Text("${(scale * faceWidth).fmt(1)} (mm)") }
        }
        ControlRow("Rim") {
            PSlider(params.render.view.faceRim, !hasFaces, showValue = false)
            Span { Text("${(scale * faceRim).fmt(1)} (mm)") }
        }
        ControlRow("Overall size") {
            PSlider(params.export.size, !hasFaces)
            Span(attrs = { classes("suffix") }) { Text("(mm)") }
        }
    }

    GroupHeader("Export solid")
    Div(attrs = { classes("control", "row") }) {
        Button(attrs = {
            if (!hasFaces) disabled()
            onClick {
                val faceContext = faces ?: return@onClick
                val name = polyName.replace(' ', '_').lowercase()
                val exportParams = FaceExportParams(scale, faceWidth, faceRim, expandFaces)
                download("$name.stl", faceContext.exportSolidToStl(name, exportParams))
            }
        }) { Text("Export to STL") }
    }

    GroupHeader("Export geometry")
    Div(attrs = { classes("control", "row") }) {
        Button(attrs = {
            onClick {
                val name = polyName.replace(' ', '_').lowercase()
                download("$name.scad", poly.exportGeometryToScad(name, params.toString()))
            }
        }) { Text("Export to SCAD") }
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
