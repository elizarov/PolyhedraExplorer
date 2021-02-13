package polyhedra.js

import kotlinx.html.*
import kotlinx.html.js.onChangeFunction
import org.w3c.dom.*
import polyhedra.common.*
import react.*
import react.dom.*

external interface RootPaneState : RState {
    var seed: Seed
}

@Suppress("NON_EXPORTABLE_TYPE")
@JsExport
class RootPane() : RComponent<RProps, RootPaneState>() {
    override fun RootPaneState.init() {
        seed = Seed.Tetrahedron
    }

    override fun RBuilder.render() {
        select {
            attrs {
                this["value"] = state.seed.name
                onChangeFunction = { event ->
                    val value = (event.target as HTMLSelectElement).value
                    setState { seed = Seed.valueOf(value) }
                }
            }
            for (seed in Seed.values()) {
                option {
                    attrs {
                        value = seed.name
                    }
                    +seed.name
                }
            }
        }
        br {}
        child(Canvas::class) {
            attrs {
                poly = state.seed.poly
                style = PolyStyle()
            }
        }
    }
}
