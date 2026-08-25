/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.model.serialization

sealed class ParsedParameter {
    data class Value(val value: String) : ParsedParameter()
    data class Composite(val map: Map<String, ParsedParameter>) : ParsedParameter()
}

fun parseCompactParameters(serialized: String): ParsedParameter = CompactParameterParser(serialized).parse()

fun normalizeCompactConfiguration(configuration: String): String {
    val fragment = configuration.substringAfter("#/", configuration).removePrefix("/")
    return decodePercentEncodedUtf8(fragment)
}

fun ParsedParameter.value(tag: String): String? =
    ((this as? ParsedParameter.Composite)?.map?.get(tag) as? ParsedParameter.Value)?.value

fun ParsedParameter.composite(tag: String): ParsedParameter.Composite? =
    (this as? ParsedParameter.Composite)?.map?.get(tag) as? ParsedParameter.Composite

private class CompactParameterParser(private val serialized: String) {
    private var position = 0
    private var current = parseNextToken()

    private enum class Type { End, Value, Open, Close }
    private data class Token(val type: Type, val value: String)

    private fun separator(char: Char): Type? = when (char) {
        '(' -> Type.Open
        ')' -> Type.Close
        else -> null
    }

    private fun parseNextToken(): Token {
        if (position >= serialized.length) return Token(Type.End, "")
        val start = position++
        separator(serialized[start])?.let { return Token(it, serialized[start].toString()) }
        while (position < serialized.length && separator(serialized[position]) == null) position++
        return Token(Type.Value, serialized.substring(start, position))
    }

    fun parse(): ParsedParameter = when (current.type) {
        Type.End, Type.Open, Type.Close -> ParsedParameter.Value("")
        Type.Value -> {
            var value = current.value
            current = parseNextToken()
            if (current.type == Type.Open) {
                val map = linkedMapOf<String, ParsedParameter>()
                while (current.type == Type.Open) {
                    current = parseNextToken()
                    map[value] = parse()
                    if (current.type != Type.Close) break
                    current = parseNextToken()
                    if (current.type != Type.Value) break
                    value = current.value
                    current = parseNextToken()
                }
                ParsedParameter.Composite(map)
            } else {
                ParsedParameter.Value(value)
            }
        }
    }
}

private fun decodePercentEncodedUtf8(value: String): String = buildString {
    var index = 0
    while (index < value.length) {
        if (value[index] != '%') {
            append(value[index++])
            continue
        }
        val bytes = ArrayList<Byte>()
        while (index < value.length && value[index] == '%') {
            require(index + 2 < value.length) { "Incomplete percent escape at offset $index" }
            val high = value[index + 1].digitToIntOrNull(16)
            val low = value[index + 2].digitToIntOrNull(16)
            require(high != null && low != null) { "Invalid percent escape at offset $index" }
            bytes += ((high shl 4) or low).toByte()
            index += 3
        }
        append(bytes.toByteArray().decodeToString(throwOnInvalidSequence = true))
    }
}
