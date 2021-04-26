package polyhedra.js.main

import kotlinx.html.js.*
import polyhedra.common.poly.*
import polyhedra.js.components.*
import polyhedra.js.params.*
import polyhedra.js.poly.*
import react.*
import react.dom.*

fun RBuilder.controlPane(params: PolyParams) {
    child(ControlPane::class) {
        attrs {
            this.params = params
        }
    }
}

external interface ControlPaneState : RState {
    var popupIndex: Int?
}

class ControlPane(props: PComponentProps<PolyParams>) : RComponent<PComponentProps<PolyParams>, ControlPaneState>(props) {
    override fun ControlPaneState.init(props: PComponentProps<PolyParams>) {
        popupIndex = null
    }

    private inner class Context(params: PolyParams) : Param.Context(params, Param.TargetValue + Param.Progress) {
        val seed by { params.seed.value }
        val transforms by { params.transforms.value }

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
        val transforms = ctx.transforms
        div("ctrl-pane") {
            // new transform
            div("btn") {
                button(classes = "square") {
                    i("fa fa-plus") {}
                }
            }
            // existing transforms

            // Seed
            div("btn") {
                button(classes = "square") {
                    attrs { onClickFunction = { adjustSeed(-1) } }
                    +"❮"
                }
                button {
                    attrs { onClickFunction = { adjustSeed(+1) } }
                    +"❯"
                }
                button(classes = "txt") {
                    +ctx.seed.toString()
                }
            }
        }
    }

    private fun adjustSeed(delta: Int) {
        val newSeed = Seeds.getOrNull(Seeds.indexOf(ctx.seed) + delta) ?: return
        props.params.seed.updateValue(newSeed)
    }
}