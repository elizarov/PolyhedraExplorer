package polyhedra.js.main

import androidx.compose.runtime.Composable
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import polyhedra.common.util.fmt
import polyhedra.js.components.ObserveParam
import polyhedra.js.components.PCheckbox
import polyhedra.js.components.PDropdown
import polyhedra.js.components.PSlider
import polyhedra.js.params.Param

@Composable
fun ConfigPopup(params: RootParams) {
    ObserveParam(params, Param.TargetValue).value
    val hasFaces = params.render.view.display.value.hasFaces()
    val animateUpdates = params.animationParams.animateValueUpdates.value
    val rotate = params.animationParams.animatedRotation.value
    val scale = params.export.size.targetValue / 2
    val faceWidth = params.render.view.faceWidth.targetValue
    val faceRim = params.render.view.faceRim.targetValue

    GroupHeader("View")
    TableBody {
        ControlRow("Base scale") { PDropdown(params.render.poly.baseScale) }
        ControlRow("View scale") { PSlider(params.render.view.scale) }
        ControlRow("Expand") { PSlider(params.render.view.expandFaces) }
        ControlRow("Display") { PDropdown(params.render.view.display) }
    }

    GroupHeader("Faces")
    TableBody {
        ControlRow("Transparent") { PSlider(params.render.view.transparentFaces, !hasFaces) }
        ControlRow("Width") {
            PSlider(params.render.view.faceWidth, !hasFaces, showValue = false)
            Span { Text("${(scale * faceWidth).fmt(1)} (mm)") }
        }
        ControlRow("Rim") {
            PSlider(params.render.view.faceRim, !hasFaces, showValue = false)
            Span { Text("${(scale * faceRim).fmt(1)} (mm)") }
        }
    }

    GroupHeader("Animation")
    TableBody {
        ControlRow2("Rotation", { PCheckbox(params.animationParams.animatedRotation) }) {
            PSlider(params.animationParams.rotationSpeed, !rotate)
        }
        ControlRow2("Angle", {}) { PSlider(params.animationParams.rotationAngle, !rotate) }
        ControlRow2("Updates", { PCheckbox(params.animationParams.animateValueUpdates) }) {
            PSlider(params.animationParams.animationDuration, !animateUpdates)
        }
    }

    GroupHeader("Lighting")
    TableBody {
        ControlRow("Ambient") { PSlider(params.render.lighting.ambientLight, !hasFaces) }
        ControlRow("Diffuse") { PSlider(params.render.lighting.diffuseLight, !hasFaces) }
        ControlRow("Specular") { PSlider(params.render.lighting.specularLight, !hasFaces) }
        ControlRow("Shininess") { PSlider(params.render.lighting.specularPower, !hasFaces) }
    }
}
