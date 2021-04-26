package polyhedra.js.main

import kotlinx.html.js.*
import polyhedra.common.poly.*
import polyhedra.common.transform.*
import polyhedra.common.util.*
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
    var dropdownIndex: Int?
}

class ControlPane(props: PComponentProps<PolyParams>) : RComponent<PComponentProps<PolyParams>, ControlPaneState>(props) {
    override fun ControlPaneState.init(props: PComponentProps<PolyParams>) {
        dropdownIndex = null
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
                if (state.dropdownIndex == transforms.size) {
                    transformsDropdown(transforms.size, Transforms.filter { it != Transform.None })
                }
                button(classes = "square") {
                    i("fa fa-plus") {
                        onClick { toggleDropdown(transforms.size) }
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
                    if (state.dropdownIndex == index) {
                        transformsDropdown(index, Transforms)
                    }
                    button(classes = "txt") {
                        onClick { toggleDropdown(index) }
                        +transform.toString()
                    }
                }
            }
            // Seed
            div("btn") {
                if (transforms.isEmpty()) {
                    leftRightSpinner(::adjustSeed)
                }
                if (state.dropdownIndex == -1) {
                    seedsDropdown()
                }
                button(classes = "txt") {
                    onClick { toggleDropdown(-1) }
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

    private fun toggleDropdown(index: Int?) {
        setState {
            dropdownIndex = if (index == dropdownIndex) null else index
        }
    }

    private fun adjustSeed(delta: Int) {
        toggleDropdown(null)
        val newSeed = Seeds.getOrNull(Seeds.indexOf(ctx.seed) + delta) ?: return
        props.params.seed.updateValue(newSeed)
    }

    private fun adjustLastTransform(delta: Int) {
        toggleDropdown(null)
        val curTransforms = ctx.transforms
        val curTransform = curTransforms.lastOrNull() ?: return
        val newTransform = Transforms.getOrNull(Transforms.indexOf(curTransform) + delta) ?: return
        if (newTransform == Transform.None) return
        props.params.transforms.updateValue(curTransforms.dropLast(1) + newTransform)
    }

    private fun updateTransform(index: Int, transform: Transform) {
        toggleDropdown(null)
        val curTransforms = ctx.transforms
        val newTransforms = when {
            index >= curTransforms.size -> curTransforms + transform
            transform != Transform.None -> curTransforms.updatedAt(index, transform)
            else -> curTransforms.removedAt(index)
        }
        props.params.transforms.updateValue(newTransforms)
    }

    private fun updateSeed(seed: Seed) {
        toggleDropdown(null)
        props.params.seed.updateValue(seed)
    }
}