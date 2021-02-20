package polyhedra.js.util

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import kotlinx.serialization.modules.*
import polyhedra.common.*

@OptIn(ExperimentalSerializationApi::class)
class URLEncoder(
    override val serializersModule: SerializersModule = EmptySerializersModule
) : AbstractEncoder() {
    private val sb = StringBuilder()
    private var listName: String? = null

    override fun encodeValue(value: Any) {
        sb.append(value)
    }

    override fun encodeDouble(value: Double) {
        sb.append(value.fmt)
    }

    override fun encodeEnum(enumDescriptor: SerialDescriptor, index: Int) {
        sb.append(enumDescriptor.getElementName(index))
    }

    override fun encodeElement(descriptor: SerialDescriptor, index: Int): Boolean {
        val name = descriptor.getElementName(index)
        when {
            descriptor.kind == StructureKind.LIST -> appendKey(listName!!)
            descriptor.getElementDescriptor(index).kind == StructureKind.LIST -> listName = name
            else -> appendKey(name)
        }
        return true
    }

    override fun endStructure(descriptor: SerialDescriptor) {
        listName = null
    }

    private fun appendKey(name: String) {
        if (sb.isNotEmpty()) sb.append(",")
        sb.append(name)
        sb.append("=")
    }

    @ExperimentalSerializationApi
    override fun shouldEncodeElementDefault(descriptor: SerialDescriptor, index: Int): Boolean = false

    override fun toString(): String = sb.toString()
}

@OptIn(ExperimentalSerializationApi::class)
class URLDecoder(
    private val str: String,
    override val serializersModule: SerializersModule = EmptySerializersModule
) : AbstractDecoder() {
    private var pos = 0
    private var cur: String = readNextToken()
    private var listName: String? = null
    private var listIndex = 0

    private fun isControlChar(ch: Char) = ch == '=' || ch == ','

    private fun readNextToken() = when {
        pos >= str.length -> ""
        isControlChar(str[pos]) -> str[pos++].toString()
        else -> {
            val start = pos
            do {
                pos++
            } while (pos < str.length && !isControlChar(str[pos]))
            str.substring(start, pos)
        }
    }

    private fun next() = cur.also { cur = readNextToken() }

    override fun decodeBoolean(): Boolean = next().toBoolean()
    override fun decodeDouble(): Double = next().toDouble()
    override fun decodeEnum(enumDescriptor: SerialDescriptor): Int = enumDescriptor.getElementIndex(next())

    private fun nextNameEquals() =
        next().also {
            if (it.isNotEmpty()) {
                require(next() == "=") {
                    "Expected '=' after element '$it'"
                }
            }
        }

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        while (cur == ",") next()
        if (descriptor.kind == StructureKind.LIST) {
            if (listIndex > 0) {
                if (cur != listName) return CompositeDecoder.DECODE_DONE
                nextNameEquals()
            }
            return listIndex++
        }
        val name = nextNameEquals()
        if (name.isEmpty()) return CompositeDecoder.DECODE_DONE
        val index = descriptor.getElementIndex(name)
        if (index == CompositeDecoder.UNKNOWN_NAME) return index
        if (descriptor.getElementDescriptor(index).kind == StructureKind.LIST) {
            listName = name
            listIndex = 0
        }
        return index
    }

    override fun endStructure(descriptor: SerialDescriptor) {
        listName = null
    }
}