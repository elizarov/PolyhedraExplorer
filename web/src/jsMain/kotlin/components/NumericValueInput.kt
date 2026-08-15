package polyhedra.web.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.attributes.value
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import org.w3c.dom.HTMLInputElement
import polyhedra.model.util.fmt
import kotlin.math.abs
import kotlin.math.round

/** A fixed-width, directly editable value shown beside a slider. */
@Composable
fun NumericValueInput(
    value: Double,
    min: Double,
    max: Double,
    step: Double,
    ariaLabel: String,
    disabled: Boolean = false,
    precision: Int = 6,
    unit: String? = null,
    snapToStep: Boolean = true,
    extraClasses: Array<String> = emptyArray(),
    onValueChange: (Double) -> Double,
) {
    require(value.isFinite() && min.isFinite() && max.isFinite())
    require(step > 0.0 && step.isFinite())
    require(min <= max)
    require(precision >= 0)

    fun format(number: Double): String {
        val zeroThreshold = 0.5 / tenTo(precision)
        return (if (abs(number) < zeroThreshold) 0.0 else number).fmt(precision)
    }

    var editing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf(format(value)) }
    val formattedValue = format(value)

    fun commit(input: HTMLInputElement) {
        val parsed = input.value.trim().replace(',', '.').toDoubleOrNull()
        val committed = if (parsed == null || !parsed.isFinite()) {
            value
        } else {
            val bounded = if (snapToStep) round(parsed / step) * step else parsed
            onValueChange(bounded.coerceIn(min, max))
        }
        draft = format(committed)
        input.value = draft
        editing = false
    }

    Input(type = InputType.Text, attrs = {
        classes("slider-value-input", *extraClasses)
        attr("inputmode", "decimal")
        attr("role", "spinbutton")
        attr("aria-label", ariaLabel)
        attr("aria-valuemin", format(min))
        attr("aria-valuemax", format(max))
        attr("aria-valuenow", formattedValue)
        attr("data-step", format(step))
        if (disabled) disabled()
        value(if (editing) draft else formattedValue)
        onFocus { event ->
            editing = true
            draft = formattedValue
            (event.target as HTMLInputElement).select()
        }
        onInput { event -> draft = event.value.orEmpty() }
        onChange { event -> commit(event.target) }
        onKeyDown { event ->
            val input = event.target as HTMLInputElement
            when (event.key) {
                "Enter" -> {
                    event.preventDefault()
                    commit(input)
                    input.blur()
                }

                "Escape" -> {
                    event.preventDefault()
                    draft = formattedValue
                    input.value = formattedValue
                    editing = false
                    input.blur()
                }
            }
        }
    })
    unit?.let {
        Span(attrs = { classes("slider-unit") }) { Text(it) }
    }
}

private fun tenTo(exponent: Int): Double {
    var result = 1.0
    repeat(exponent) { result *= 10.0 }
    return result
}
