/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.common.poly

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import polyhedra.common.util.*

class PolyhedronSerializer : KSerializer<Polyhedron> {
    private val serializer = SerializedPolyhedron.serializer()
    override val descriptor: SerialDescriptor = serializer.descriptor

    override fun deserialize(decoder: Decoder): Polyhedron =
        decoder.decodeSerializableValue(serializer).toPolyhedron()

    override fun serialize(encoder: Encoder, value: Polyhedron) =
        encoder.encodeSerializableValue(serializer, value.toSerialized())
}

@Serializable
private class SerializedPolyhedron(
    val vs: List<SerializedVertex>,
    val fs: List<SerializedFace>
)

@Serializable
private class SerializedVertex(
    override val x: Double,
    override val y: Double,
    override val z: Double,
    val kind: VertexKind
) : Vec3

@Serializable
private class SerializedFace(
    val fvs: List<Int>,
    val kind: FaceKind
)

private fun Polyhedron.toSerialized() = SerializedPolyhedron(
    vs.map { v -> SerializedVertex(v.x, v.y, v.z, v.kind) },
    fs.map { f -> SerializedFace(f.fvs.map { it.id }, f.kind) }
)

private fun SerializedPolyhedron.toPolyhedron() = polyhedron {
    for (v in vs) vertex(v, v.kind)
    for (f in fs) face(f.fvs, f.kind)
}
