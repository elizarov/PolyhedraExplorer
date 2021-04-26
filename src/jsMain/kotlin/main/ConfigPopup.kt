package polyhedra.js.main

import polyhedra.common.util.*
import polyhedra.js.components.*
import polyhedra.js.params.*
import react.*
import react.dom.*

fun RBuilder.configPopup(params: RootParams) {
    child(ConfigPopup::class) {
        attrs {
            this.params = params
        }
    }
}

class ConfigPopup(props: PComponentProps<RootParams>) : RComponent<PComponentProps<RootParams>, RState>(props) {
    private inner class Context(params: RootParams) : Param.Context(params, Param.TargetValue) {
        val hasFaces by { params.render.view.display.value.hasFaces() }
        val animateUpdates by { params.animationParams.animateValueUpdates.value }
        val rotate by { params.animationParams.animatedRotation.value  }

        val scale by { params.export.size.targetValue / 2 }
        val faceWidth by { params.render.view.faceWidth.targetValue }
        val faceRim by { params.render.view.faceRim.targetValue }

        init { setup() }

        override fun update() {
            forceUpdate()
        }
    }

    private val ctx = Context(props.params)

    override fun componentWillUnmount() {
        ctx.destroy()
    }

    override fun RBuilder.render() {
        popupHeader("View")
        tableBody {
            controlRow("Base scale") { pDropdown(props.params.render.poly.baseScale) }
            controlRow("View scale") { pSlider(props.params.render.view.scale) }
            controlRow("Expand") { pSlider(props.params.render.view.expandFaces) }
            controlRow("Display") { pDropdown(props.params.render.view.display) }
        }

        popupHeader("Faces")
        tableBody {
            controlRow("Transparent") { pSlider(props.params.render.view.transparentFaces, !ctx.hasFaces) }
            controlRow("Width") {
                pSlider(props.params.render.view.faceWidth, !ctx.hasFaces, showValue = false)
                span { +"${(ctx.scale * ctx.faceWidth).fmt(1)} (mm)" }
            }
            controlRow("Rim") {
                pSlider(props.params.render.view.faceRim, !ctx.hasFaces, showValue = false)
                span { +"${(ctx.scale * ctx.faceRim).fmt(1)} (mm)" }
            }
        }

        popupHeader("Animation")
        tableBody {
            controlRow2("Rotation", { pCheckbox(props.params.animationParams.animatedRotation) }) {
                pSlider(props.params.animationParams.rotationSpeed, !ctx.rotate)
            }
            controlRow2("Angle", {}, {
                pSlider(props.params.animationParams.rotationAngle, !ctx.rotate)
            })
            controlRow2("Updates", { pCheckbox(props.params.animationParams.animateValueUpdates) }) {
                pSlider(props.params.animationParams.animationDuration, !ctx.animateUpdates)
            }
        }

        popupHeader("Lighting")
        tableBody {
            controlRow("Ambient") { pSlider(props.params.render.lighting.ambientLight, !ctx.hasFaces) }
            controlRow("Diffuse") { pSlider(props.params.render.lighting.diffuseLight, !ctx.hasFaces) }
            controlRow("Specular") { pSlider(props.params.render.lighting.specularLight, !ctx.hasFaces) }
            controlRow("Shininess") { pSlider(props.params.render.lighting.specularPower, !ctx.hasFaces) }
        }
    }
}