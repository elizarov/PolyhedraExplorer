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
        val transformedPolys by { params.transformedPolys }
        val seed by { params.seed.value }
        val transforms by { params.transforms.value }
        val transformWarnings by { params.transformWarnings }
        val transformError by { params.transformError }
        val transformProgress by { params.transformProgress }

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
        val transformError = ctx.transformError
        val errorIndex = transformError?.index ?: Int.MAX_VALUE
        div("ctrl-pane") {
            // new transform
            div("btn") {
                val disabled = transforms.size > errorIndex
                if (props.popup == Popup.AddTransform && !disabled) {
                    transformsDropdown(transforms.size)
                }
                button(classes = "square") {
                    attrs { this.disabled = disabled }
                    i("fa fa-plus") {
                        onClick { props.togglePopup(Popup.AddTransform) }
                    }
                }
            }
            // existing transforms
            for (index in transforms.lastIndex downTo 0) {
                div("btn") {
                    val disabled = index > errorIndex
                    if (index == transforms.lastIndex) {
                        leftRightSpinner(::adjustLastTransform, disabled)
                    }
                    if (props.popup == Popup.ModifyTransform(index) && !disabled) {
                        transformsDropdown(index)
                    }
                    button(classes = "txt") {
                        attrs { this.disabled = disabled }
                        onClick { props.togglePopup(Popup.ModifyTransform(index)) }
                        +transforms[index].toString()
                    }
                    if (index == errorIndex) {
                        val isInProcess = transformError?.isAsync == true
                        if (isInProcess) {
                            button(classes = "msg") {
                                onClick { updateTransform(index, Transform.None) }
                                span("spinner") {}
                                span { +"${ctx.transformProgress}%" }
                                aside("tooltip-text") { +"Transformation is running" }
                            }
                        } else {
                            transformError?.msg?.let { messageButton(index, it) }
                        }
                    } else {
                        val warning = ctx.transformWarnings.getOrNull(index)
                        if (warning != null) messageButton(index, warning)
                    }
                }
            }
            // Seed
            div("btn") {
                if (transforms.isEmpty()) {
                    leftRightSpinner(::adjustSeed, disabled = false)
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

    private fun <T> RBuilder.messageButton(index: Int, msg: IndicatorMessage<T>) {
        button(classes = "msg") {
            onClick {
                when(msg.indicator) {
                    TransformFailed, TransformNotApplicable, TransformIsId, TooLarge -> {
                        updateTransform(index, Transform.None)
                    }
                    SomeFacesNotPlanar -> {
                        updateTransform(ctx.transforms.size, Transform.Canonical)
                    }
                }
            }
            messageSpan(msg)
        }
    }

    private fun RBuilder.leftRightSpinner(adjust: (Int) -> Unit, disabled: Boolean) {
        button(classes = "square") {
            attrs { this.disabled = disabled }
            onClick { adjust(-1) }
            +"❮"
        }
        button {
            attrs { this.disabled = disabled }
            onClick { adjust(+1) }
            +"❯"
        }
    }

    private fun possibleTransformsAt(index: Int): Set<Transform> {
        val result = Transforms.toMutableSet()
        val poly = if (index == 0) ctx.seed.poly else ctx.transformedPolys.getOrNull(index - 1)
        poly?.canDrop?.mapTo(result) { Drop(it) }
        ctx.transforms.getOrNull(index)?.let { result += it }
        if (index == ctx.transforms.size) result -= Transform.None
        return result
    }

    private fun RBuilder.transformsDropdown(index: Int) {
        aside("dropdown") {
            groupHeader("Transform")
            for (transform in possibleTransformsAt(index)) {
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
        val possibleTransforms = possibleTransformsAt(curTransforms.lastIndex).toList()
        val newTransform = possibleTransforms.getOrNull(possibleTransforms.indexOf(curTransform) + delta) ?: return
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