package polyhedra.js

import kotlinx.html.js.onChangeFunction
import org.w3c.dom.*
import polyhedra.common.*
import react.*
import react.dom.*

external interface RootPaneState : RState {
    var seed: Seed
    var scale: Scale
}

@Suppress("NON_EXPORTABLE_TYPE")
@JsExport
class RootPane() : RComponent<RProps, RootPaneState>() {
    override fun RootPaneState.init() {
        seed = Seed.Tetrahedron
        scale = Scale.Midradius
    }

    override fun RBuilder.render() {
        dropdown<Seed> {
            value = state.seed
            options = Seed.values().toList()
            onChange = { value ->
                setState { seed = value }
            }
        }
        dropdown<Scale> {
            value = state.scale
            options = Scale.values().toList()
            onChange = { value ->
                setState { scale = value }
            }
        }
        br {}
        glCanvas {
            poly = state.seed.poly.scaled(state.scale)
            style = PolyStyle()
        }
    }
}
