package polyhedra.js.main

import androidx.compose.runtime.Composable
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.dom.*
import polyhedra.common.util.updatedAt
import polyhedra.js.catalog.*
import polyhedra.js.components.observe
import polyhedra.js.params.Param
import polyhedra.js.poly.*

@Composable
fun ControlPane(params: PolyParams, popup: Popup?, togglePopup: (Popup?) -> Unit) {
    params.observe(Param.TargetValue + Param.Progress)
    val transforms = params.transforms.value
    val transformError = params.transformError
    val errorIndex = transformError?.index ?: Int.MAX_VALUE

    fun possibleTransformsAt(index: Int): Set<Transform> {
        val result = Transforms.toMutableSet()
        params.availableDropsAt(index).mapTo(result) { Drop(it) }
        transforms.getOrNull(index)?.let { result += it }
        if (index == transforms.size) result -= Transform.None
        return result
    }

    fun updateTransform(index: Int, transform: Transform) {
        togglePopup(null)
        val updated = when {
            index >= transforms.size -> transforms + transform
            transform != Transform.None -> transforms.updatedAt(index, transform)
            else -> transforms.filterIndexed { itemIndex, _ -> itemIndex != index }
        }
        params.transforms.updateValue(updated)
    }

    fun adjustSeed(delta: Int) {
        togglePopup(null)
        val current = params.seed.value
        Seeds.getOrNull(Seeds.indexOf(current) + delta)?.let(params.seed::updateValue)
    }

    fun adjustLastTransform(delta: Int) {
        togglePopup(null)
        val current = transforms.lastOrNull() ?: return
        val options = possibleTransformsAt(transforms.lastIndex).toList()
        val replacement = options.getOrNull(options.indexOf(current) + delta) ?: return
        if (replacement != Transform.None) params.transforms.updateValue(transforms.dropLast(1) + replacement)
    }

    Div(attrs = { classes("ctrl-pane") }) {
        val addPopup = Popup.AddTransform
        val addDisabled = transforms.size > errorIndex
        Div(attrs = { classes("btn", *activeWhen(popup, addPopup)) }) {
            if (popup == addPopup && !addDisabled) {
                TransformDropdown(possibleTransformsAt(transforms.size)) { updateTransform(transforms.size, it) }
            }
            Button(attrs = {
                classes("square", *activeWhen(popup, addPopup))
                if (addDisabled) disabled()
                onClick { togglePopup(addPopup) }
            }) {
                I(attrs = { classes("fa", "fa-plus") })
                Aside(attrs = { classes("tooltip-text") }) { Text("Add transform") }
            }
        }

        for (index in transforms.lastIndex downTo 0) {
            val itemDisabled = index > errorIndex
            val itemPopup = Popup.ModifyTransform(index)
            Div(attrs = { classes("btn", *activeWhen(popup, itemPopup)) }) {
                if (index == transforms.lastIndex) {
                    LeftRightSpinner(itemDisabled) { adjustLastTransform(it) }
                }
                if (popup == itemPopup && !itemDisabled) {
                    TransformDropdown(possibleTransformsAt(index)) { updateTransform(index, it) }
                }
                Button(attrs = {
                    classes("txt", *activeWhen(popup, itemPopup))
                    if (itemDisabled) disabled()
                    onClick { togglePopup(itemPopup) }
                }) {
                    Text(transforms[index].toString())
                    Aside(attrs = { classes("tooltip-text") }) { Text("Modify transform") }
                }
                if (index == errorIndex) {
                    if (transformError?.isAsync == true) {
                        Button(attrs = {
                            classes("msg", *activeWhen(popup, itemPopup))
                            onClick { updateTransform(index, Transform.None) }
                        }) {
                            Span(attrs = { classes("spinner") })
                            Span { Text("${params.transformProgress}%") }
                            Aside(attrs = { classes("tooltip-text") }) { Text("Transformation is running") }
                        }
                    } else {
                        transformError?.msg?.let { MessageButton(index, it, ::updateTransform) }
                    }
                } else {
                    params.transformWarnings.getOrNull(index)?.let { MessageButton(index, it, ::updateTransform) }
                }
            }
        }

        Div(attrs = { classes("btn", *activeWhen(popup, Popup.Seed)) }) {
            if (transforms.isEmpty()) LeftRightSpinner(disabled = false, onAdjust = ::adjustSeed)
            if (popup == Popup.Seed) {
                Aside(attrs = { classes("dropdown") }) {
                    var previousType: SeedType? = null
                    for (seed in Seeds) {
                        if (seed.type != previousType) {
                            previousType = seed.type
                            GroupHeader(seed.type.toString())
                        }
                        Div(attrs = { classes("text-row") }) {
                            Div(attrs = {
                                classes("item")
                                onClick { togglePopup(null); params.seed.updateValue(seed) }
                            }) { Text(seed.toString()) }
                        }
                    }
                }
            }
            Button(attrs = {
                classes("txt", *activeWhen(popup, Popup.Seed))
                onClick { togglePopup(Popup.Seed) }
            }) {
                Text(params.seed.value.toString())
                Aside(attrs = { classes("tooltip-text") }) { Text("Seed") }
            }
        }

        params.suggestedSeed?.let { suggestedSeed ->
            Div(attrs = { classes("btn", "suggestion") }) {
                Button(attrs = {
                    classes("txt")
                    onClick {
                        togglePopup(null)
                        params.acceptSuggestedSeed()
                    }
                }) {
                    Text("→ $suggestedSeed")
                    Aside(attrs = { classes("tooltip-text") }) {
                        Text("Replace the current seed and transform chain with this catalog solid")
                    }
                }
            }
        }
    }

    Div(attrs = { classes("btn", "reset") }) {
        val isReset = transforms.isEmpty() && params.seed.value == Seed.Tetrahedron
        Button(attrs = {
            classes("square")
            if (isReset) disabled()
            onClick {
                if (transforms.isNotEmpty()) params.transforms.updateValue(transforms.dropLast(1))
                else params.seed.updateValue(Seed.Tetrahedron)
            }
        }) {
            I(attrs = { classes("fa", "fa-trash-o") })
            Aside(attrs = { classes("tooltip-text") }) { Text("Delete transform/reset seed") }
        }
    }
}

@Composable
private fun TransformDropdown(options: Set<Transform>, onSelect: (Transform) -> Unit) {
    Aside(attrs = { classes("dropdown") }) {
        GroupHeader("Transform")
        for (transform in options) {
            Div(attrs = { classes("text-row") }) {
                Div(attrs = {
                    classes("item")
                    onClick { onSelect(transform) }
                }) { Text(transform.toString()) }
            }
        }
    }
}

@Composable
private fun LeftRightSpinner(disabled: Boolean, onAdjust: (Int) -> Unit) {
    Button(attrs = {
        classes("square")
        if (disabled) disabled()
        onClick { onAdjust(-1) }
    }) { Text("❮") }
    Button(attrs = {
        if (disabled) disabled()
        onClick { onAdjust(1) }
    }) { Text("❯") }
}

@Composable
private fun MessageButton(
    index: Int,
    message: IndicatorMessage<*>,
    updateTransform: (Int, Transform) -> Unit,
) {
    Button(attrs = {
        classes("msg")
        onClick {
            when (message.indicator) {
                TransformFailed, TransformNotApplicable, TransformIsId, TooLarge ->
                    updateTransform(index, Transform.None)
                SomeFacesNotPlanar -> updateTransform(index + 1, Transform.Canonical)
            }
        }
    }) { MessageSpan(message) }
}
