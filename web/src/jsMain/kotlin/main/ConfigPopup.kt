package polyhedra.web.main

import androidx.compose.runtime.Composable
import polyhedra.web.components.observe
import polyhedra.web.components.PCheckbox
import polyhedra.web.components.PDropdown
import polyhedra.web.components.PSlider

@Composable
fun ConfigPopup(params: RootParams) {
    params.observe()
    val hasFaces = params.render.view.display.value.hasFaces()
    val animateUpdates = params.animationParams.animateValueUpdates.value
    val rotate = params.animationParams.animatedRotation.value
    val scale = params.export.size.targetValue / 2

    GroupHeader("View")
    TableBody {
        ControlRow("Base scale") { PDropdown(params.render.poly.baseScale) }
        ControlRow("View scale") { PSlider(params.render.view.scale, ariaLabel = "View scale") }
        ControlRow("Expand") { PSlider(params.render.view.expandFaces, ariaLabel = "Expand") }
        ControlRow("Display") { PDropdown(params.render.view.display) }
        ControlRow("Environment") { PDropdown(params.render.view.environment) }
    }

    GroupHeader("Faces")
    TableBody {
        ControlRow("Transparent") {
            PSlider(params.render.view.transparentFaces, !hasFaces, ariaLabel = "Transparency")
        }
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
    }

    GroupHeader("Symmetry")
    TableBody {
        ControlRow("Plane size") { PSlider(params.render.view.symmetryPlaneSize, ariaLabel = "Plane size") }
        ControlRow("Axis size") { PSlider(params.render.view.symmetryAxisSize, ariaLabel = "Axis size") }
    }

    GroupHeader("Animation")
    TableBody {
        ControlRow2("Rotation", { PCheckbox(params.animationParams.animatedRotation) }) {
            PSlider(params.animationParams.rotationSpeed, !rotate, ariaLabel = "Rotation speed")
        }
        ControlRow2("Angle", {}) {
            PSlider(params.animationParams.rotationAngle, !rotate, ariaLabel = "Rotation angle")
        }
        ControlRow2("Updates", { PCheckbox(params.animationParams.animateValueUpdates) }) {
            PSlider(params.animationParams.animationDuration, !animateUpdates, ariaLabel = "Update duration")
        }
    }

    GroupHeader("Lighting")
    TableBody {
        ControlRow("Key light") { PSlider(params.render.lighting.keyLight, !hasFaces, ariaLabel = "Key light") }
        ControlRow("Fill light") { PSlider(params.render.lighting.fillLight, !hasFaces, ariaLabel = "Fill light") }
    }

    GroupHeader("Material")
    TableBody {
        ControlRow("Roughness") { PSlider(params.render.lighting.roughness, !hasFaces, ariaLabel = "Roughness") }
        ControlRow("IOR") { PSlider(params.render.lighting.ior, !hasFaces, ariaLabel = "Index of refraction") }
    }
}
