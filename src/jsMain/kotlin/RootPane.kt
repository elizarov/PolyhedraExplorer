package polyhedra.js

import kotlinx.html.InputType
import kotlinx.html.js.onChangeFunction
import org.w3c.dom.HTMLInputElement
import polyhedra.common.*
import react.RBuilder
import react.RComponent
import react.RProps
import react.RState
import react.dom.*

external interface RootPaneProps : RProps {
}

data class RootPaneState(
    val text: String = "Hello"
) : RState

@Suppress("NON_EXPORTABLE_TYPE")
@JsExport
class RootPane(props: RootPaneProps) : RComponent<RootPaneProps, RootPaneState>(props) {
    init {
        state = RootPaneState()
    }

    override fun RBuilder.render() {
        input {
            attrs {
                type = InputType.text
                value = state.text
                onChangeFunction = { event ->
                    setState(RootPaneState(text = (event.target as HTMLInputElement).value))
                }
            }
        }
        br {}
        child(Canvas::class) {
            attrs {
                text = state.text
                poly = icosahedron
                style = PolyStyle()
            }
        }
    }
}
