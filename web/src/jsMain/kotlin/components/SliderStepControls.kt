package polyhedra.web.components

import androidx.compose.runtime.Composable
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.I

/** Tap-friendly one-step navigation for a discrete range input. */
@Composable
fun SliderStepControls(
    canStepBackward: Boolean,
    canStepForward: Boolean,
    previousLabel: String = "Previous value",
    nextLabel: String = "Next value",
    onStep: (Int) -> Unit,
) {
    Div(attrs = { classes("slider-step-controls") }) {
        Button(attrs = {
            classes("slider-step-previous")
            attr("aria-label", previousLabel)
            if (!canStepBackward) disabled()
            onClick { onStep(-1) }
        }) {
            I(attrs = { classes("fa", "fa-angle-left") })
        }
        Button(attrs = {
            classes("slider-step-next")
            attr("aria-label", nextLabel)
            if (!canStepForward) disabled()
            onClick { onStep(1) }
        }) {
            I(attrs = { classes("fa", "fa-angle-right") })
        }
    }
}
