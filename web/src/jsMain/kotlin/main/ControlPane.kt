package polyhedra.web.main

import androidx.compose.runtime.*
import kotlinx.browser.document
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.attributes.value
import org.jetbrains.compose.web.dom.*
import polyhedra.model.api.CoreTransformTweakRange
import polyhedra.model.api.DEFAULT_STAR_FAMILY_SEED_N
import polyhedra.model.api.DEFAULT_STAR_FAMILY_SEED_Q
import polyhedra.model.api.MAX_FAMILY_SEED_N
import polyhedra.model.api.MAX_STAR_FAMILY_SEED_Q
import polyhedra.model.api.MIN_FAMILY_SEED_N
import polyhedra.model.api.StarFamilySeedId
import polyhedra.model.api.TransformTweak
import polyhedra.model.api.TransformOperation
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
internal fun ControlPane(
    params: PolyParams,
    popup: Popup?,
    togglePopup: (Popup?) -> Unit,
    keyboardActions: ControlKeyboardActions? = null,
) {
    params.observe(Param.TargetValue + Param.Progress)
    var lastFamilyN by remember {
        mutableStateOf(params.seed.value.familyId?.n ?: MIN_FAMILY_SEED_N)
    }
    var lastStarFamilyValues by remember {
        mutableStateOf(
            params.seed.value.starFamilyId?.let { id -> id.n to id.q }
                ?: (DEFAULT_STAR_FAMILY_SEED_N to DEFAULT_STAR_FAMILY_SEED_Q)
        )
    }
    var addTransformSelection by remember { mutableStateOf(0) }
    val currentFamilyN = params.seed.value.familyId?.n
    val currentStarFamilyId = params.seed.value.starFamilyId
    SideEffect {
        if (currentFamilyN != null) lastFamilyN = currentFamilyN
        if (currentStarFamilyId != null) {
            lastStarFamilyValues = currentStarFamilyId.n to currentStarFamilyId.q
        }
    }
    val transforms = params.transforms.value
    val transformError = params.transformError
    val errorIndex = transformError?.index ?: Int.MAX_VALUE
    val prefixReplacement = if (transformError == null) {
        findTransformPrefixReplacement(transforms.map(Transform::spec))
    } else {
        null
    }
    val intersectionStatus = params.geometryAnalysis?.toIntersectionIndicatorOrNull()
        ?.takeIf {
            transformError == null && transforms.lastOrNull()?.operation != TransformOperation.Resolve
        }

    fun appendResolve() {
        togglePopup(null)
        params.transforms.updateValue(transforms + Transform.Resolve)
    }

    fun possibleTransformsAt(index: Int): Set<Transform> {
        val result = TransformOptions.toMutableSet()
        result += params.availableOrbitTransformsAt(index)
        transforms.getOrNull(index)?.withoutTweaks()?.takeIf { it !in result }?.let { result += it }
        if (index == transforms.size) result -= Transform.None
        return result
    }

    fun operationOptionsAt(index: Int): List<Transform> {
        return displayedTransformOptions(possibleTransformsAt(index)).map { option -> option.transform }
    }

    val addTransformOptions = displayedTransformOptions(possibleTransformsAt(transforms.size))

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

    fun adjustSeed(delta: Int): Boolean {
        val current = params.seed.value
        val currentIndex = SeedOptions.indexOfFirst { seed -> seed.optionKey == current.optionKey }
        current.familyId?.let { lastFamilyN = it.n }
        current.starFamilyId?.let { lastStarFamilyValues = it.n to it.q }
        val option = SeedOptions.getOrNull(currentIndex + delta) ?: return false
        togglePopup(null)
        val seed = when {
            option.familyId != null -> option.withFamilyN(lastFamilyN)
            option.starFamilyId != null -> option.withStarFamilyValues(
                lastStarFamilyValues.first,
                lastStarFamilyValues.second,
            )
            else -> option
        }
        params.seed.updateValue(seed)
        return true
    }

    fun selectSeed(option: Seed) {
        togglePopup(null)
        val seed = when {
            option.familyId != null -> option.withFamilyN(lastFamilyN)
            option.starFamilyId != null -> option.withStarFamilyValues(
                lastStarFamilyValues.first,
                lastStarFamilyValues.second,
            )
            else -> option
        }
        params.seed.updateValue(seed)
    }

    fun adjustFamilyN(delta: Int): Boolean {
        val current = params.seed.value
        val n = requireNotNull(current.familyId).n + delta
        if (n !in MIN_FAMILY_SEED_N..MAX_FAMILY_SEED_N) return false
        togglePopup(null)
        lastFamilyN = n
        params.seed.updateValue(current.withFamilyN(n))
        return true
    }

    fun adjustStarFamilyN(delta: Int): Boolean {
        val current = params.seed.value
        val id = requireNotNull(current.starFamilyId)
        val replacement = adjacentValidStarFamilyN(id, delta) ?: return false
        togglePopup(null)
        lastStarFamilyValues = replacement.n to replacement.q
        params.seed.updateValue(current.withStarFamilyValues(replacement.n, replacement.q))
        return true
    }

    fun updateStarFamilyQ(q: Int) {
        val current = params.seed.value
        val id = requireNotNull(current.starFamilyId)
        val replacementQ = nearestValidStarFamilyQ(id, q) ?: return
        lastStarFamilyValues = id.n to replacementQ
        params.seed.updateValue(current.withStarFamilyValues(id.n, replacementQ))
    }

    fun adjustLastTransform(delta: Int): Boolean {
        val current = transforms.lastOrNull() ?: return false
        params.rememberOrbitTarget(current)
        val currentOrbitOperation = current.orbitTargetOrNull()?.operation
        val options = operationOptionsAt(transforms.lastIndex)
        val currentIndex = options.indexOfFirst { option ->
            option.id == current.id ||
                currentOrbitOperation != null && option.orbitTargetOrNull()?.operation == currentOrbitOperation
        }
        val replacement = options.getOrNull(currentIndex + delta)
            ?.takeIf { it != Transform.None } ?: return false
        togglePopup(null)
        val selected = params.reuseRememberedOrbitTarget(
            replacement,
            possibleTransformsAt(transforms.lastIndex),
        )
        params.transforms.updateValue(transforms.dropLast(1) + selected)
        return true
    }

    fun adjustLastOrbitTarget(delta: Int): Boolean {
        val current = transforms.lastOrNull() ?: return false
        val currentTarget = current.orbitTargetOrNull() ?: return false
        val supportedTransforms = params.availableOrbitTransformsAt(transforms.lastIndex)
            .filter { it.orbitTargetOrNull()?.operation == currentTarget.operation }
            .sortedBy { it.orbitTargetOrNull()?.kind.toString() }
        if (supportedTransforms.isEmpty()) return false
        val currentIndex = supportedTransforms.indexOf(current)
        val replacementIndex = if (currentIndex >= 0) {
            (currentIndex + delta + supportedTransforms.size) % supportedTransforms.size
        } else if (delta < 0) {
            supportedTransforms.lastIndex
        } else {
            0
        }
        val replacement = supportedTransforms[replacementIndex]
        if (replacement == current) return false
        togglePopup(null)
        params.transforms.updateValue(transforms.dropLast(1) + replacement)
        return true
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

    fun navigateAddTransform(delta: Int): Boolean {
        if (popup != Popup.AddTransform || addTransformOptions.isEmpty()) return false
        addTransformSelection = (
            addTransformSelection + delta + addTransformOptions.size
        ) % addTransformOptions.size
        return true
    }

    fun confirmAddTransform(): Boolean {
        if (popup != Popup.AddTransform) return false
        val selectedIndex = addTransformSelection.coerceAtMost(addTransformOptions.lastIndex)
        val option = addTransformOptions.getOrNull(selectedIndex) ?: return false
        updateTransform(transforms.size, option.transform)
        return true
    }

    fun acceptVisibleSuggestion(): Boolean {
        if (popup != null) return false
        prefixReplacement?.let { replacement ->
            acceptPrefixReplacement(replacement)
            return true
        }
        if (params.suggestedSeed != null) {
            togglePopup(null)
            params.acceptSuggestedSeed()
            return true
        }
        if (intersectionStatus != null) {
            appendResolve()
            return true
        }
        return false
    }

    val addDisabled = transforms.size > errorIndex
    val isReset = transforms.isEmpty() &&
        params.seed.value == Seed.Tetrahedron &&
        lastFamilyN == MIN_FAMILY_SEED_N &&
        lastStarFamilyValues == (DEFAULT_STAR_FAMILY_SEED_N to DEFAULT_STAR_FAMILY_SEED_Q)

    fun toggleAddTransform(): Boolean {
        if (addDisabled) return false
        if (popup != Popup.AddTransform) addTransformSelection = 0
        togglePopup(Popup.AddTransform)
        return true
    }

    fun deleteLast(): Boolean {
        if (isReset) return false
        togglePopup(null)
        if (transforms.isNotEmpty()) {
            params.transforms.updateValue(transforms.dropLast(1))
        } else {
            lastFamilyN = MIN_FAMILY_SEED_N
            lastStarFamilyValues = DEFAULT_STAR_FAMILY_SEED_N to DEFAULT_STAR_FAMILY_SEED_Q
            params.clearRememberedOrbitTargets()
            params.seed.updateValue(Seed.Tetrahedron)
        }
        return true
    }

    SideEffect {
        if (popup == Popup.AddTransform) {
            val selectedIndex = addTransformSelection.coerceAtMost(addTransformOptions.lastIndex)
            document.getElementById(addTransformOptionId(selectedIndex))?.scrollIntoView()
        }
        keyboardActions?.adjustHorizontal = { delta ->
            if (transforms.isEmpty()) adjustSeed(delta) else adjustLastTransform(delta)
        }
        keyboardActions?.adjustVertical = { delta ->
            when {
                transforms.lastOrNull()?.orbitTargetOrNull() != null -> adjustLastOrbitTarget(delta)
                transforms.isEmpty() && params.seed.value.familyId != null -> adjustFamilyN(-delta)
                transforms.isEmpty() && params.seed.value.starFamilyId != null -> adjustStarFamilyN(-delta)
                else -> false
            }
        }
        keyboardActions?.addTransform = ::toggleAddTransform
        keyboardActions?.navigateAddTransform = ::navigateAddTransform
        keyboardActions?.confirmAddTransform = ::confirmAddTransform
        keyboardActions?.acceptSuggestion = ::acceptVisibleSuggestion
        keyboardActions?.deleteTransform = ::deleteLast
    }

    Div(attrs = { classes("ctrl-pane") }) {
        val addPopup = Popup.AddTransform
        Div(attrs = { classes("btn", *activeWhen(popup, addPopup)) }) {
            if (popup == addPopup && !addDisabled) {
                TransformDropdown(
                    options = addTransformOptions,
                    selectedIndex = addTransformSelection.coerceAtMost(addTransformOptions.lastIndex),
                    onHighlight = { addTransformSelection = it },
                ) { updateTransform(transforms.size, it) }
            }
            Button(attrs = {
                classes("square", *activeWhen(popup, addPopup))
                if (addDisabled) disabled()
                onClick { toggleAddTransform() }
            }) {
                I(attrs = { classes("fa", "fa-plus") })
                Aside(attrs = { classes("tooltip-text") }) { Text("Add transform") }
            }
        }

        for (index in transforms.lastIndex downTo 0) {
            val itemDisabled = index > errorIndex
            val itemPopup = Popup.ModifyTransform(index)
            val settingsPopup = Popup.TransformSettings(index)
            val transformSafeRanges = params.transformTweakRangesAt(index)
            val hasVariableStellationResult = transformSafeRanges
                ?.get(TransformTweak.StellationResult)
                ?.let { range -> range.max > range.min } == true
            val hasSettings = index == transforms.lastIndex && (
                transforms[index].settings.any { setting ->
                    setting.tweak != TransformTweak.StellationResult || hasVariableStellationResult
                } || transforms[index].isChiral
                )
            val itemActive = popup == itemPopup || popup == settingsPopup
            Div(attrs = { classes("btn", *(if (itemActive) arrayOf("active") else emptyArray())) }) {
                if (index == transforms.lastIndex) {
                    LeftRightSpinner(itemDisabled) { adjustLastTransform(it) }
                }
                if (popup == itemPopup && !itemDisabled) {
                    TransformDropdown(displayedTransformOptions(possibleTransformsAt(index))) {
                        updateTransform(index, it)
                    }
                }
                if (popup == settingsPopup && hasSettings && !itemDisabled) {
                    TransformSettingsPopup(
                        transform = transforms[index],
                        safeRanges = transformSafeRanges,
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
                    OrbitTargetControls(itemDisabled) { adjustLastOrbitTarget(it) }
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
                if (index == transforms.lastIndex && intersectionStatus != null) {
                    IntersectionButton(intersectionStatus, ::appendResolve)
                }
            }
            if (prefixReplacement?.startIndex == index) {
                PrefixReplacementSuggestion(prefixReplacement, ::acceptPrefixReplacement)
            }
        }

        val seedSettingsAvailable = params.seed.value.starFamilyId != null
        val seedActive = popup == Popup.Seed || popup == Popup.SeedSettings
        Div(attrs = { classes("btn", *(if (seedActive) arrayOf("active") else emptyArray())) }) {
            if (transforms.isEmpty()) LeftRightSpinner(disabled = false) { adjustSeed(it) }
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
                            }) {
                                Text(seed.name)
                            }
                        }
                    }
                }
            }
            if (popup == Popup.SeedSettings && seedSettingsAvailable) {
                SeedSettingsPopup(
                    seed = params.seed.value,
                    onChangeQ = ::updateStarFamilyQ,
                )
            }
            Button(attrs = {
                classes("txt", *activeWhen(popup, Popup.Seed))
                onClick { togglePopup(Popup.Seed) }
            }) {
                Text(params.seed.value.toString())
                Aside(attrs = { classes("tooltip-text") }) { Text("Seed") }
            }
            if (seedSettingsAvailable) {
                Button(attrs = {
                    classes("square", "seed-settings-button", *activeWhen(popup, Popup.SeedSettings))
                    onClick { togglePopup(Popup.SeedSettings) }
                }) {
                    I(attrs = { classes("fa", "fa-cog") })
                    Aside(attrs = { classes("tooltip-text") }) { Text("Seed settings") }
                }
            }
            params.seed.value.familyId?.let { familyId ->
                FamilySeedControls(familyId.n) { adjustFamilyN(it) }
            }
            params.seed.value.starFamilyId?.let { starFamilyId ->
                FamilySeedControls(
                    canIncrement = adjacentValidStarFamilyN(starFamilyId, 1) != null,
                    canDecrement = adjacentValidStarFamilyN(starFamilyId, -1) != null,
                ) { adjustStarFamilyN(it) }
            }
            if (transforms.isEmpty() && params.seed.value.isChiral) {
                ChiralityFlipButton(::flipSeedChirality)
            }
            if (transforms.isEmpty() && intersectionStatus != null) {
                IntersectionButton(intersectionStatus, ::appendResolve)
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
        Button(attrs = {
            classes("square")
            if (isReset) disabled()
            onClick { deleteLast() }
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
                        if (setting.tweak == TransformTweak.StellationResult) {
                            val current = currentValue.roundToInt()
                            val count = maximum.roundToInt()
                            val fev = safeRange?.options?.singleOrNull { option -> option.value == current }?.fev
                            Text(buildString {
                                append("$current of $count")
                                if (fev != null) append(" · F ${fev.f}, E ${fev.e}, V ${fev.v}")
                            })
                        } else {
                            Text("${(currentValue * 100).roundToInt()}%")
                        }
                    }
                }
            }
            if (canFlipChirality) {
                ControlRow("Chirality") { ChiralityFlipButton(onFlipChirality) }
            }
        }
        Div(attrs = { classes("transform-settings-actions") }) {
            for (setting in transform.settings) {
                for (snap in safeRanges?.get(setting.tweak)?.snaps.orEmpty()) {
                    Button(attrs = {
                        classes("transform-setting-snap")
                        attr("aria-label", "Snap ${setting.label} to ${snap.label}")
                        onClick { onChange(setting, snap.value) }
                    }) {
                        Text(snap.label)
                        Aside(attrs = { classes("tooltip-text") }) {
                            Text("Snap ${setting.label} to ${snap.label}")
                        }
                    }
                }
            }
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
private fun SeedSettingsPopup(
    seed: Seed,
    onChangeQ: (Int) -> Unit,
) {
    val id = requireNotNull(seed.starFamilyId)
    Aside(attrs = { classes("transform-settings", "seed-settings") }) {
        GroupHeader("${seed.name} settings")
        TableBody {
            ControlRow("Step q") {
                Input(type = InputType.Range, attrs = {
                    classes("seed-setting-slider")
                    attr("aria-label", "Star polygon step")
                    attr("min", "2")
                    attr("max", MAX_STAR_FAMILY_SEED_Q.toString())
                    value(id.q.toString())
                    onInput { event ->
                        event.value?.toDouble()?.roundToInt()?.let(onChangeQ)
                    }
                })
                Span(attrs = { classes("transform-setting-value", "seed-setting-value") }) {
                    Text(id.q.toString())
                }
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
private fun TransformDropdown(
    options: List<DisplayedTransformOption>,
    selectedIndex: Int? = null,
    onHighlight: ((Int) -> Unit)? = null,
    onSelect: (Transform) -> Unit,
) {
    Aside(attrs = {
        classes("dropdown")
        if (selectedIndex != null) {
            attr("role", "listbox")
            attr("aria-label", "Transform options")
        }
    }) {
        for (category in TransformCategory.entries) {
            val categoryOptions = options.withIndex().filter { it.value.category == category }
            if (categoryOptions.isNotEmpty()) {
                GroupHeader(category.toString())
                for ((index, option) in categoryOptions) {
                    val selected = index == selectedIndex
                    Div(attrs = { classes("text-row") }) {
                        Div(attrs = {
                            classes("item", *(if (selected) arrayOf("keyboard-selected") else emptyArray()))
                            attr("role", "option")
                            if (selectedIndex != null) attr("id", addTransformOptionId(index))
                            if (selected) attr("aria-selected", "true")
                            onMouseOver { onHighlight?.invoke(index) }
                            onClick { onSelect(option.transform) }
                        }) { Text(option.name) }
                    }
                }
            }
        }
    }
}

private data class DisplayedTransformOption(
    val transform: Transform,
    val name: String,
    val category: TransformCategory,
)

private fun displayedTransformOptions(options: Set<Transform>): List<DisplayedTransformOption> = buildList {
    for (category in TransformCategory.entries) {
        val categoryOptions = options.filter { it.category == category }
        when (category) {
            TransformCategory.OrbitTargeted -> OrbitTargetedOperation.entries.mapNotNullTo(this) { operation ->
                categoryOptions.filter { it.orbitTargetOrNull()?.operation == operation }
                    .minByOrNull { it.orbitTargetOrNull()?.kind.toString() }
                    ?.let { transform -> DisplayedTransformOption(transform, operation.optionName, category) }
            }
            else -> categoryOptions.mapTo(this) { transform ->
                DisplayedTransformOption(transform, transform.toString(), category)
            }
        }
    }
}

private fun addTransformOptionId(index: Int): String = "add-transform-option-$index"

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
private fun FamilySeedControls(
    n: Int? = null,
    canIncrement: Boolean = n != null && n < MAX_FAMILY_SEED_N,
    canDecrement: Boolean = n != null && n > MIN_FAMILY_SEED_N,
    onAdjust: (Int) -> Unit,
) {
    Div(attrs = { classes("vertical-controls", "family-seed-controls") }) {
        Button(attrs = {
            classes("family-seed-increment")
            attr("aria-label", "Increase family size")
            if (!canIncrement) disabled()
            onClick { onAdjust(1) }
        }) { I(attrs = { classes("fa", "fa-angle-up") }) }
        Button(attrs = {
            classes("family-seed-decrement")
            attr("aria-label", "Decrease family size")
            if (!canDecrement) disabled()
            onClick { onAdjust(-1) }
        }) { I(attrs = { classes("fa", "fa-angle-down") }) }
    }
}

private fun adjacentValidStarFamilyN(id: StarFamilySeedId, delta: Int): StarFamilySeedId? {
    require(delta == -1 || delta == 1)
    var n = id.n + delta
    while (n in MIN_FAMILY_SEED_N..MAX_FAMILY_SEED_N) {
        runCatching { StarFamilySeedId(id.family, n, id.q) }.getOrNull()?.let { return it }
        n += delta
    }
    return null
}

private fun nearestValidStarFamilyQ(id: StarFamilySeedId, requestedQ: Int): Int? =
    (2..MAX_STAR_FAMILY_SEED_Q)
        .filter { q -> runCatching { StarFamilySeedId(id.family, id.n, q) }.isSuccess }
        .minWithOrNull(compareBy<Int> { q -> kotlin.math.abs(q - requestedQ) }.thenBy { q -> q })

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

@Composable
private fun IntersectionButton(
    message: IndicatorMessage<String>,
    appendResolve: () -> Unit,
) {
    Button(attrs = {
        classes("msg", "intersection-indicator")
        attr("aria-label", "Add Resolved transform")
        onClick { appendResolve() }
    }) { MessageSpan(message) }
}
