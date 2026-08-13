/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.model.poly

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import polyhedra.model.util.*
import polyhedra.model.api.ResolvedTopologyProvenance

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
    val fs: List<SerializedFace>,
    val resolvedFaces: List<ResolvedFaceGeometry> = emptyList(),
    val resolvedTopologyProvenance: ResolvedTopologyProvenance? = null,
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
    fs.map { f -> SerializedFace(f.fvs.map { it.id }, f.kind) },
    resolvedFaces,
    resolvedTopologyProvenance,
)

private fun SerializedPolyhedron.toPolyhedron(): Polyhedron {
    val vertices = vs.mapIndexed { index, vertex ->
        MutableVertex(index, vertex, vertex.kind)
    }
    val faces = fs.mapIndexed { index, face ->
        MutableFace(index, face.fvs.map(vertices::get), face.kind)
    }
    return Polyhedron(
        vertices,
        faces,
        faceKindSources = null,
        resolvedFaceGeometry = resolvedFaces.takeIf { it.isNotEmpty() },
        resolvedTopologyProvenance = resolvedTopologyProvenance,
    )
}
