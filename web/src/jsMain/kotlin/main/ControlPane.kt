package polyhedra.web.main

import androidx.compose.runtime.*
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.attributes.value
import org.jetbrains.compose.web.dom.*
import polyhedra.model.api.CoreTransformTweakRange
import polyhedra.model.api.MAX_FAMILY_SEED_N
import polyhedra.model.api.MIN_FAMILY_SEED_N
import polyhedra.model.api.TransformTweak
import polyhedra.model.api.TransformPrefixReplacement
import polyhedra.model.api.findTransformPrefixReplacement
import polyhedra.model.util.updatedAt
import polyhedra.web.catalog.*
import polyhedra.web.components.observe
import polyhedra.web.params.Param
import polyhedra.web.poly.*
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

@Composable
fun ControlPane(params: PolyParams, popup: Popup?, togglePopup: (Popup?) -> Unit) {
    params.observe(Param.TargetValue + Param.Progress)
    var lastFamilyN by remember {
        mutableStateOf(params.seed.value.familyId?.n ?: MIN_FAMILY_SEED_N)
    }
    val currentFamilyN = params.seed.value.familyId?.n
    SideEffect {
        if (currentFamilyN != null) lastFamilyN = currentFamilyN
    }
    val transforms = params.transforms.value
    val transformError = params.transformError
    val errorIndex = transformError?.index ?: Int.MAX_VALUE
    val prefixReplacement = if (transformError == null) {
        findTransformPrefixReplacement(transforms.map(Transform::spec))
    } else {
        null
    }

    fun possibleTransformsAt(index: Int): Set<Transform> {
        val result = TransformOptions.toMutableSet()
        result += params.availableOrbitTransformsAt(index)
        transforms.getOrNull(index)?.withoutTweaks()?.takeIf { it !in result }?.let { result += it }
        if (index == transforms.size) result -= Transform.None
        return result
    }

    fun operationOptionsAt(index: Int): List<Transform> {
        val options = possibleTransformsAt(index)
        val regular = options.filter { it.category != TransformCategory.OrbitTargeted }
        val orbitTargeted = OrbitTargetedOperation.entries.mapNotNull { operation ->
            options.filter { it.orbitTargetOrNull()?.operation == operation }
                .minByOrNull { it.orbitTargetOrNull()?.kind.toString() }
        }
        return regular + orbitTargeted
    }

    fun updateTransform(index: Int, transform: Transform) {
        togglePopup(null)
        params.rememberOrbitTarget(transforms.getOrNull(index))
        val current = transforms.getOrNull(index)
        val selectedTransform = params
            .reuseRememberedOrbitTarget(transform, possibleTransformsAt(index))
            .let { selected ->
                if (
                    current != null && selected.id == current.id
                ) selected.copy(tweaks = current.tweaks) else selected
            }
        val updated = when {
            index >= transforms.size -> transforms + selectedTransform
            selectedTransform != Transform.None -> transforms.updatedAt(index, selectedTransform)
            else -> transforms.filterIndexed { itemIndex, _ -> itemIndex != index }
        }
        params.transforms.updateValue(updated)
    }

    fun adjustSeed(delta: Int) {
        togglePopup(null)
        val current = params.seed.value
        val currentIndex = SeedOptions.indexOfFirst { seed -> seed.optionKey == current.optionKey }
        current.familyId?.let { lastFamilyN = it.n }
        SeedOptions.getOrNull(currentIndex + delta)?.let { option ->
            val seed = if (option.isFamily) option.withFamilyN(lastFamilyN) else option
            params.seed.updateValue(seed)
        }
    }

    fun selectSeed(option: Seed) {
        togglePopup(null)
        val seed = if (option.isFamily) option.withFamilyN(lastFamilyN) else option
        params.seed.updateValue(seed)
    }

    fun adjustFamilyN(delta: Int) {
        togglePopup(null)
        val current = params.seed.value
        val n = requireNotNull(current.familyId).n + delta
        if (n in MIN_FAMILY_SEED_N..MAX_FAMILY_SEED_N) {
            lastFamilyN = n
            params.seed.updateValue(current.withFamilyN(n))
        }
    }

    fun adjustLastTransform(delta: Int) {
        togglePopup(null)
        val current = transforms.lastOrNull() ?: return
        params.rememberOrbitTarget(current)
        val currentOrbitOperation = current.orbitTargetOrNull()?.operation
        val options = operationOptionsAt(transforms.lastIndex)
        val currentIndex = options.indexOfFirst { option ->
            option.id == current.id ||
                currentOrbitOperation != null && option.orbitTargetOrNull()?.operation == currentOrbitOperation
        }
        val replacement = options.getOrNull(currentIndex + delta) ?: return
        if (replacement != Transform.None) {
            val selected = params.reuseRememberedOrbitTarget(
                replacement,
                possibleTransformsAt(transforms.lastIndex),
            )
            params.transforms.updateValue(transforms.dropLast(1) + selected)
        }
    }

    fun adjustLastOrbitTarget(delta: Int) {
        togglePopup(null)
        val current = transforms.lastOrNull() ?: return
        val currentTarget = current.orbitTargetOrNull() ?: return
        val supportedTransforms = params.availableOrbitTransformsAt(transforms.lastIndex)
            .filter { it.orbitTargetOrNull()?.operation == currentTarget.operation }
            .sortedBy { it.orbitTargetOrNull()?.kind.toString() }
        if (supportedTransforms.isEmpty()) return
        val currentIndex = supportedTransforms.indexOf(current)
        val replacementIndex = if (currentIndex >= 0) {
            (currentIndex + delta + supportedTransforms.size) % supportedTransforms.size
        } else if (delta < 0) {
            supportedTransforms.lastIndex
        } else {
            0
        }
        params.transforms.updateValue(transforms.dropLast(1) + supportedTransforms[replacementIndex])
    }

    fun flipTransformChirality(index: Int) {
        params.transforms.updateValue(transforms.updatedAt(index, transforms[index].flippedChirality()))
    }

    fun updateTransformTweak(index: Int, setting: TransformSetting, value: Double) {
        params.transforms.updateValue(
            transforms.updatedAt(index, transforms[index].withTweak(setting.tweak, value))
        )
    }

    fun resetTransformSettings(index: Int) {
        params.transforms.updateValue(
            transforms.updatedAt(index, transforms[index].withDefaultSettings())
        )
    }

    fun flipSeedChirality() {
        togglePopup(null)
        params.seed.updateValue(params.seed.value.flippedChirality())
    }

    fun acceptPrefixReplacement(replacement: TransformPrefixReplacement) {
        togglePopup(null)
        val transform = Transforms.single { it.id == replacement.replacement }
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
            val settingsPopup = Popup.TransformSettings(index)
            val hasSettings = index == transforms.lastIndex &&
                (transforms[index].settings.isNotEmpty() || transforms[index].isChiral)
            val itemActive = popup == itemPopup || popup == settingsPopup
            Div(attrs = { classes("btn", *(if (itemActive) arrayOf("active") else emptyArray())) }) {
                if (index == transforms.lastIndex) {
                    LeftRightSpinner(itemDisabled) { adjustLastTransform(it) }
                }
                if (popup == itemPopup && !itemDisabled) {
                    TransformDropdown(possibleTransformsAt(index)) { updateTransform(index, it) }
                }
                if (popup == settingsPopup && hasSettings && !itemDisabled) {
                    TransformSettingsPopup(
                        transform = transforms[index],
                        safeRanges = params.transformTweakRangesAt(index),
                        canFlipChirality = index == transforms.lastIndex && transforms[index].isChiral,
                        onChange = { setting, value -> updateTransformTweak(index, setting, value) },
                        onFlipChirality = { flipTransformChirality(index) },
                        onReset = { resetTransformSettings(index) },
                    )
                }
                Button(attrs = {
                    classes("txt", *activeWhen(popup, itemPopup))
                    if (itemDisabled) disabled()
                    onClick { togglePopup(itemPopup) }
                }) {
                    Text(transforms[index].toString())
                    Aside(attrs = { classes("tooltip-text") }) { Text("Modify transform") }
                }
                if (hasSettings) {
                    Button(attrs = {
                        classes("square", "transform-settings-button", *activeWhen(popup, settingsPopup))
                        if (itemDisabled) disabled()
                        onClick { togglePopup(settingsPopup) }
                    }) {
                        I(attrs = { classes("fa", "fa-cog") })
                        Aside(attrs = { classes("tooltip-text") }) { Text("Transform settings") }
                    }
                }
                if (index == transforms.lastIndex && transforms[index].orbitTargetOrNull() != null) {
                    OrbitTargetControls(itemDisabled, ::adjustLastOrbitTarget)
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
                                onClick { selectSeed(seed) }
                            }) { Text(seed.name) }
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
            params.seed.value.familyId?.let { familyId ->
                FamilySeedControls(familyId.n, ::adjustFamilyN)
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
        val isReset = transforms.isEmpty() &&
            params.seed.value == Seed.Tetrahedron &&
            lastFamilyN == MIN_FAMILY_SEED_N
        Button(attrs = {
            classes("square")
            if (isReset) disabled()
            onClick {
                if (transforms.isNotEmpty()) params.transforms.updateValue(transforms.dropLast(1))
                else {
                    lastFamilyN = MIN_FAMILY_SEED_N
                    params.clearRememberedOrbitTargets()
                    params.seed.updateValue(Seed.Tetrahedron)
                }
            }
        }) {
            I(attrs = { classes("fa", "fa-trash-o") })
            Aside(attrs = { classes("tooltip-text") }) { Text("Delete transform/reset seed") }
        }
    }
}

@Composable
private fun TransformSettingsPopup(
    transform: Transform,
    safeRanges: Map<TransformTweak, CoreTransformTweakRange>?,
    canFlipChirality: Boolean,
    onChange: (TransformSetting, Double) -> Unit,
    onFlipChirality: () -> Unit,
    onReset: () -> Unit,
) {
    Aside(attrs = { classes("transform-settings") }) {
        GroupHeader("$transform settings")
        TableBody {
            for (setting in transform.settings) {
                val currentValue = transform.tweaks[setting.tweak] ?: 1.0
                val safeRange = safeRanges?.get(setting.tweak)
                val minimum = safeRange?.min ?: setting.min
                val maximum = safeRange?.max ?: setting.max
                val minTick = ceil(minimum / setting.step - 1e-9).toInt()
                val maxTick = floor(maximum / setting.step + 1e-9).toInt()
                val rangeAvailable = safeRanges == null || safeRange != null && minTick <= maxTick
                ControlRow(setting.label) {
                    Input(type = InputType.Range, attrs = {
                        classes("transform-setting-slider")
                        attr("aria-label", setting.label)
                        attr("min", minTick.toString())
                        attr("max", maxTick.coerceAtLeast(minTick).toString())
                        value(
                            (currentValue / setting.step).roundToInt()
                                .coerceIn(minTick, maxTick.coerceAtLeast(minTick))
                                .toString()
                        )
                        if (!rangeAvailable) disabled()
                        onInput { event ->
                            event.value?.toDouble()?.let { sliderValue ->
                                onChange(setting, sliderValue * setting.step)
                            }
                        }
                    })
                    Span(attrs = { classes("transform-setting-value") }) {
                        Text("${(currentValue * 100).roundToInt()}%")
                    }
                }
            }
            if (canFlipChirality) {
                ControlRow("Chirality") { ChiralityFlipButton(onFlipChirality) }
            }
        }
        Div(attrs = { classes("transform-settings-actions") }) {
            Button(attrs = {
                classes("transform-settings-reset")
                attr("aria-label", "Reset transform settings")
                if (transform == transform.withDefaultSettings()) disabled()
                onClick { onReset() }
            }) {
                I(attrs = { classes("fa", "fa-undo") })
                Aside(attrs = { classes("tooltip-text") }) { Text("Reset transform settings") }
            }
        }
    }
}

@Composable
private fun ChiralityFlipButton(onFlip: () -> Unit) {
    Button(attrs = {
        classes("square", "chirality-flip")
        attr("aria-label", "Flip chirality")
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
                    TransformCategory.OrbitTargeted -> OrbitTargetedOperation.entries.mapNotNull { operation ->
                        categoryOptions.filter { it.orbitTargetOrNull()?.operation == operation }
                            .minByOrNull { it.orbitTargetOrNull()?.kind.toString() }
                            ?.let { it to operation.optionName }
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
    val transform = Transforms.single { it.id == replacement.replacement }
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
private fun OrbitTargetControls(disabled: Boolean, onAdjust: (Int) -> Unit) {
    Div(attrs = { classes("vertical-controls", "drop-orbit-controls", "orbit-target-controls") }) {
        Button(attrs = {
            classes("drop-orbit-previous")
            attr("aria-label", "Previous target orbit")
            if (disabled) disabled()
            onClick { onAdjust(-1) }
        }) { I(attrs = { classes("fa", "fa-angle-up") }) }
        Button(attrs = {
            classes("drop-orbit-next")
            attr("aria-label", "Next target orbit")
            if (disabled) disabled()
            onClick { onAdjust(1) }
        }) { I(attrs = { classes("fa", "fa-angle-down") }) }
    }
}

@Composable
private fun FamilySeedControls(n: Int, onAdjust: (Int) -> Unit) {
    Div(attrs = { classes("vertical-controls", "family-seed-controls") }) {
        Button(attrs = {
            classes("family-seed-increment")
            attr("aria-label", "Increase family size")
            if (n >= MAX_FAMILY_SEED_N) disabled()
            onClick { onAdjust(1) }
        }) { I(attrs = { classes("fa", "fa-angle-up") }) }
        Button(attrs = {
            classes("family-seed-decrement")
            attr("aria-label", "Decrease family size")
            if (n <= MIN_FAMILY_SEED_N) disabled()
            onClick { onAdjust(-1) }
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
                TransformFailed, InvalidGeometry, TransformNotApplicable, TransformIsId, TooLarge ->
                    updateTransform(index, Transform.None)
                SomeFacesNotPlanar -> updateTransform(index + 1, Transform.Canonical)
            }
        }
    }) { MessageSpan(message) }
}
