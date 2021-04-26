package polyhedra.js.main

import polyhedra.common.poly.*
import polyhedra.common.transform.*
import polyhedra.common.util.*
import polyhedra.js.components.*
import polyhedra.js.params.*
import polyhedra.js.poly.*
import react.*
import react.dom.*

fun RBuilder.controlPane(handler: ControlPaneProps.() -> Unit) {
    child(ControlPane::class) {
        attrs(handler)
    }
}

external interface ControlPaneProps : PComponentProps<PolyParams> {
    var popup: Popup?
    var togglePopup: (Popup?) -> Unit
}

class ControlPane(props: ControlPaneProps) : RComponent<ControlPaneProps, RState>(props) {
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
                if (props.popup == Popup.AddTransform) {
                    transformsDropdown(transforms.size, Transforms.filter { it != Transform.None })
                }
                button(classes = "square") {
                    i("fa fa-plus") {
                        onClick { props.togglePopup(Popup.AddTransform) }
                    }
                }
            }
            // existing transforms
            for (index in transforms.lastIndex downTo 0) {
                val transform = transforms[index]
                div("btn") {
                    if (index == transforms.lastIndex) {
                        leftRightSpinner(::adjustLastTransform)
                    }
                    if (props.popup == Popup.ModifyTransform(index)) {
                        transformsDropdown(index, Transforms)
                    }
                    button(classes = "txt") {
                        onClick { props.togglePopup(Popup.ModifyTransform(index)) }
                        +transform.toString()
                    }
                }
            }
            // Seed
            div("btn") {
                if (transforms.isEmpty()) {
                    leftRightSpinner(::adjustSeed)
                }
                if (props.popup == Popup.Seed) {
                    seedsDropdown()
                }
                button(classes = "txt") {
                    onClick { props.togglePopup(Popup.Seed) }
                    +ctx.seed.toString()
                }
            }
        }
    }

    private fun RBuilder.leftRightSpinner(adjust: (Int) -> Unit) {
        button(classes = "square") {
            onClick { adjust(-1) }
            +"❮"
        }
        button {
            onClick { adjust(+1) }
            +"❯"
        }
    }

    private fun RBuilder.transformsDropdown(index: Int, values: List<Transform>) {
        aside("dropdown") {
            groupHeader("Transform")
            for (transform in values) {
                div("text-row") {
                    div("item") {
                        onClick { updateTransform(index, transform) }
                        +transform.toString()
                    }
                }
            }
        }
    }
    
    private fun RBuilder.seedsDropdown() {
        aside("dropdown") {
            var type: SeedType? = null
            for (seed in Seeds) {
                if (seed.type != type) {
                    type = seed.type
                    groupHeader(type.toString())
                }
                div("text-row") {
                    div("item") {
                        onClick { updateSeed(seed) }
                        +seed.toString()
                    }
                }
            }
        }
    }

    private fun adjustSeed(delta: Int) {
        props.togglePopup(null)
        val newSeed = Seeds.getOrNull(Seeds.indexOf(ctx.seed) + delta) ?: return
        props.params.seed.updateValue(newSeed)
    }

    private fun adjustLastTransform(delta: Int) {
        props.togglePopup(null)
        val curTransforms = ctx.transforms
        val curTransform = curTransforms.lastOrNull() ?: return
        val newTransform = Transforms.getOrNull(Transforms.indexOf(curTransform) + delta) ?: return
        if (newTransform == Transform.None) return
        props.params.transforms.updateValue(curTransforms.dropLast(1) + newTransform)
    }

    private fun updateTransform(index: Int, transform: Transform) {
        props.togglePopup(null)
        val curTransforms = ctx.transforms
        val newTransforms = when {
            index >= curTransforms.size -> curTransforms + transform
            transform != Transform.None -> curTransforms.updatedAt(index, transform)
            else -> curTransforms.removedAt(index)
        }
        props.params.transforms.updateValue(newTransforms)
    }

    private fun updateSeed(seed: Seed) {
        props.togglePopup(null)
        props.params.seed.updateValue(seed)
    }
}