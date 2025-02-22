/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.js.params

sealed class ParsedParam {
    data class Value(val value: String) : ParsedParam()
    data class Composite(val map: Map<String, ParsedParam>) : ParsedParam()
}

fun Param.loadFromString(str: String) {
    val parsed = ParamParser(str).parse()
    val updated = ArrayList<Param>()
    loadFrom(parsed) { updated += it }
    updated.forEach {
        // mark loaded values for repaint in the next animation frame
        it.notifyUpdated(Param.LoadedValue)
        // eagerly recompute derived values just like on TargetValue change
        it.computeDerivedTargetValues()
    }
}

private class ParamParser(private val str: String) {
    private var pos = 0
    private var cur = parseNextToken()

    private enum class Type { End, Value, Open, Close }
    private data class Token(val type: Type, val value: String)

    private fun separator(ch: Char): Type? = when (ch) {
        '(' -> Type.Open
        ')' -> Type.Close
        else -> null
    }

    private fun parseNextToken(): Token {
        if (pos >= str.length) return Token(Type.End, "")
        val start = pos++
        separator(str[start])?.let { return Token(it, str[start].toString()) }
        while(pos < str.length && separator(str[pos]) == null) pos++
        return Token(Type.Value, str.substring(start, pos))
    }

    fun parse(): ParsedParam = when(cur.type) {
        Type.End, Type.Open, Type.Close -> ParsedParam.Value("")
        else -> {
            var value = cur.value
            cur = parseNextToken()
            if (cur.type == Type.Open) {
                val map = mutableMapOf<String, ParsedParam>()
                while (cur.type == Type.Open) {
                    cur = parseNextToken()
                    map[value] = parse()
                    if (cur.type != Type.Close) break
                    cur = parseNextToken()
                    if (cur.type != Type.Value) break
                    value = cur.value
                    cur = parseNextToken()
                }
                ParsedParam.Composite(map)
            } else {
                ParsedParam.Value(value)
            }
        }
    }

}
