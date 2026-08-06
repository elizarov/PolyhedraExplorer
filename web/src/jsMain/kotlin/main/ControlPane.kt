package polyhedra.js.main

import androidx.compose.runtime.Composable
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.dom.*
import polyhedra.common.util.updatedAt
import polyhedra.core.api.TransformPrefixReplacement
import polyhedra.core.api.findTransformPrefixReplacement
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
    val prefixReplacement = if (transformError == null) {
        findTransformPrefixReplacement(transforms.map(Transform::tag))
    } else {
        null
    }

    fun possibleTransformsAt(index: Int): Set<Transform> {
        val result = TransformOptions.toMutableSet()
        params.availableDropsAt(index).mapTo(result) { Drop(it) }
        transforms.getOrNull(index)?.takeIf { it !in Transforms }?.let { result += it }
        if (index == transforms.size) result -= Transform.None
        return result
    }

    fun operationOptionsAt(index: Int): List<Transform> {
        val options = possibleTransformsAt(index)
        val regular = options.filter { it.category != TransformCategory.OrbitTargeted }
        val orbitTargeted = DropTarget.entries.mapNotNull { target ->
            options.filter { it.dropKindOrNull()?.dropTarget() == target }
                .minByOrNull { it.dropKindOrNull().toString() }
        }
        return regular + orbitTargeted
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
        val currentIndex = SeedOptions.indexOfFirst { seed -> seed.baseTag == current.baseTag }
        SeedOptions.getOrNull(currentIndex + delta)?.let(params.seed::updateValue)
    }

    fun adjustLastTransform(delta: Int) {
        togglePopup(null)
        val current = transforms.lastOrNull() ?: return
        val currentDropTarget = current.dropKindOrNull()?.dropTarget()
        val options = operationOptionsAt(transforms.lastIndex)
        val currentIndex = options.indexOfFirst { option ->
            option == current ||
                current.isChiral && option.baseTag == current.baseTag ||
                currentDropTarget != null && option.dropKindOrNull()?.dropTarget() == currentDropTarget
        }
        val replacement = options.getOrNull(currentIndex + delta) ?: return
        if (replacement != Transform.None) params.transforms.updateValue(transforms.dropLast(1) + replacement)
    }

    fun adjustLastDropTarget(delta: Int) {
        togglePopup(null)
        val current = transforms.lastOrNull() ?: return
        val currentKind = current.dropKindOrNull() ?: return
        val target = currentKind.dropTarget()
        val supportedKinds = params.availableDropsAt(transforms.lastIndex)
            .filter { it.dropTarget() == target }
            .sortedBy(Any::toString)
        if (supportedKinds.isEmpty()) return
        val currentIndex = supportedKinds.indexOf(currentKind)
        val replacementIndex = if (currentIndex >= 0) {
            (currentIndex + delta + supportedKinds.size) % supportedKinds.size
        } else if (delta < 0) {
            supportedKinds.lastIndex
        } else {
            0
        }
        params.transforms.updateValue(transforms.dropLast(1) + Drop(supportedKinds[replacementIndex]))
    }

    fun flipTransformChirality(index: Int) {
        togglePopup(null)
        params.transforms.updateValue(transforms.updatedAt(index, transforms[index].flippedChirality()))
    }

    fun flipSeedChirality() {
        togglePopup(null)
        params.seed.updateValue(params.seed.value.flippedChirality())
    }

    fun acceptPrefixReplacement(replacement: TransformPrefixReplacement) {
        togglePopup(null)
        val transform = Transforms.single { it.tag == replacement.replacementTag }
        params.transforms.updateValue(transforms.take(replacement.startIndex) + transform)
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
                if (index == transforms.lastIndex && transforms[index].isChiral) {
                    ChiralityFlipButton { flipTransformChirality(index) }
                }
                if (index == transforms.lastIndex && transforms[index].dropKindOrNull() != null) {
                    DropOrbitControls(itemDisabled, ::adjustLastDropTarget)
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
            if (prefixReplacement?.startIndex == index) {
                PrefixReplacementSuggestion(prefixReplacement, ::acceptPrefixReplacement)
            }
        }

        Div(attrs = { classes("btn", *activeWhen(popup, Popup.Seed)) }) {
            if (transforms.isEmpty()) LeftRightSpinner(disabled = false, onAdjust = ::adjustSeed)
            if (popup == Popup.Seed) {
                Aside(attrs = { classes("dropdown") }) {
                    var previousType: SeedType? = null
                    for (seed in SeedOptions) {
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
            if (transforms.isEmpty() && params.seed.value.isChiral) {
                ChiralityFlipButton(::flipSeedChirality)
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
private fun ChiralityFlipButton(onFlip: () -> Unit) {
    Button(attrs = {
        classes("square", "chirality-flip")
        onClick { onFlip() }
    }) {
        I(attrs = { classes("fa", "fa-exchange") })
        Aside(attrs = { classes("tooltip-text") }) { Text("Flip chirality") }
    }
}

@Composable
private fun TransformDropdown(options: Set<Transform>, onSelect: (Transform) -> Unit) {
    Aside(attrs = { classes("dropdown") }) {
        for (category in TransformCategory.entries) {
            val categoryOptions = options.filter { it.category == category }
            if (categoryOptions.isNotEmpty()) {
                GroupHeader(category.toString())
                val displayedOptions = when (category) {
                    TransformCategory.OrbitTargeted -> DropTarget.entries.mapNotNull { target ->
                        categoryOptions.filter { it.dropKindOrNull()?.dropTarget() == target }
                            .minByOrNull { it.dropKindOrNull().toString() }
                            ?.let { it to target.optionName }
                    }
                    else -> categoryOptions.map { it to it.toString() }
                }
                for ((transform, name) in displayedOptions) {
                    Div(attrs = { classes("text-row") }) {
                        Div(attrs = {
                            classes("item")
                            onClick { onSelect(transform) }
                        }) { Text(name) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PrefixReplacementSuggestion(
    replacement: TransformPrefixReplacement,
    onAccept: (TransformPrefixReplacement) -> Unit,
) {
    val transform = Transforms.single { it.tag == replacement.replacementTag }
    Div(attrs = { classes("btn", "suggestion", "prefix-replacement-suggestion") }) {
        Button(attrs = {
            classes("txt")
            onClick { onAccept(replacement) }
        }) {
            Text("→ ${transform.name}")
            Aside(attrs = { classes("tooltip-text") }) {
                Text("Replace this transform prefix with the algebraically equivalent operation")
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
private fun DropOrbitControls(disabled: Boolean, onAdjust: (Int) -> Unit) {
    Div(attrs = { classes("drop-orbit-controls") }) {
        Button(attrs = {
            classes("drop-orbit-previous")
            attr("aria-label", "Previous drop orbit")
            if (disabled) disabled()
            onClick { onAdjust(-1) }
        }) { I(attrs = { classes("fa", "fa-angle-up") }) }
        Button(attrs = {
            classes("drop-orbit-next")
            attr("aria-label", "Next drop orbit")
            if (disabled) disabled()
            onClick { onAdjust(1) }
        }) { I(attrs = { classes("fa", "fa-angle-down") }) }
    }
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
