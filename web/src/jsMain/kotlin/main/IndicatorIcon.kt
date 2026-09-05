package polyhedra.web.main

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import org.jetbrains.compose.web.dom.Span
import polyhedra.web.poly.IndicatorSymbol

/** Text-colored, baseline-independent symbols with their drawing bounds centered at (12, 12). */
@Composable
internal fun IndicatorIcon(symbol: IndicatorSymbol) = key(symbol) {
    val drawing = when (symbol) {
        IndicatorSymbol.Warning -> """<path d="M12 2 L23 22 H1 Z M12 9 V14 M12 18 h0.01"/>"""
        // Traverse alternate vertices of a regular pentagon, not the outline of a five-point star.
        IndicatorSymbol.Pentagram -> """<polygon points="12,2 18.50,22 1.49,9.64 22.51,9.64 5.50,22"/>"""
    }
    Span(attrs = {
        classes("indicator-icon")
        attr("aria-hidden", "true")
        ref { element ->
            element.innerHTML = """<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" """ +
                """stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" """ +
                """focusable="false" aria-hidden="true">$drawing</svg>"""
            onDispose { element.innerHTML = "" }
        }
    })
}
