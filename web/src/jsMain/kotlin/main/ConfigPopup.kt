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
    val cutEnabled = params.render.view.cutEnabled.value
    val animateUpdates = params.animationParams.animateValueUpdates.value
    val rotate = params.animationParams.animatedRotation.value
    val scale = params.export.size.targetValue / 2

    GroupHeader("View")
    TableBody {
        ConfigControlRow("Base scale") { PDropdown(params.render.poly.baseScale) }
        ConfigControlRow("View scale") { PSlider(params.render.view.scale, ariaLabel = "View scale") }
        ConfigControlRow("Expand") { PSlider(params.render.view.expandFaces, ariaLabel = "Expand") }
        ControlRow2("Cut", { PCheckbox(params.render.view.cutEnabled) }) {
            PSlider(params.render.view.cutPosition, !cutEnabled, ariaLabel = "Cut plane position")
        }
        ConfigControlRow("Display") { PDropdown(params.render.view.display) }
        ConfigControlRow("Environment") { PDropdown(params.render.view.environment) }
    }

    GroupHeader("Faces")
    TableBody {
        ConfigControlRow("Transparent") {
            PSlider(params.render.view.transparentFaces, !hasFaces, ariaLabel = "Transparency")
        }
        ConfigControlRow("Width") {
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
        ConfigControlRow("Rim") {
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
        ConfigControlRow("Plane size") { PSlider(params.render.view.symmetryPlaneSize, ariaLabel = "Plane size") }
        ConfigControlRow("Axis size") { PSlider(params.render.view.symmetryAxisSize, ariaLabel = "Axis size") }
    }

    GroupHeader("Animation")
    TableBody {
        ControlRow2("Rotation", { PCheckbox(params.animationParams.animatedRotation) }) {
            PSlider(params.animationParams.rotationSpeed, !rotate, ariaLabel = "Rotation speed")
        }
        ConfigControlRow("Angle") {
            PSlider(params.animationParams.rotationAngle, !rotate, ariaLabel = "Rotation angle")
        }
        ControlRow2("Updates", { PCheckbox(params.animationParams.animateValueUpdates) }) {
            PSlider(params.animationParams.animationDuration, !animateUpdates, ariaLabel = "Update duration")
        }
    }

    GroupHeader("Lighting")
    TableBody {
        ConfigControlRow("Key light") { PSlider(params.render.lighting.keyLight, !hasFaces, ariaLabel = "Key light") }
        ConfigControlRow("Fill light") { PSlider(params.render.lighting.fillLight, !hasFaces, ariaLabel = "Fill light") }
    }

    GroupHeader("Material")
    TableBody {
        ConfigControlRow("Roughness") { PSlider(params.render.lighting.roughness, !hasFaces, ariaLabel = "Roughness") }
        ConfigControlRow("IOR") { PSlider(params.render.lighting.ior, !hasFaces, ariaLabel = "Index of refraction") }
    }
}

@Composable
private fun ConfigControlRow(label: String, content: @Composable () -> Unit) {
    ControlRow(label, labelColumnSpan = 2, content = content)
}
