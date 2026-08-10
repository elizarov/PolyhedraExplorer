package polyhedra.core

import polyhedra.model.api.TransformId
import polyhedra.model.api.TransformOperation
import polyhedra.model.api.TransformSpec
import polyhedra.model.api.TransformTweak
import polyhedra.model.api.parseTransformTag
import polyhedra.model.api.tag
import polyhedra.model.poly.Chirality
import polyhedra.model.poly.FaceKind
import polyhedra.model.poly.VertexKind
import kotlin.test.Test
import kotlin.test.assertEquals

class TransformTagTest {
    @Test
    fun operationTagsAreUniqueAndRoundTripThroughTheSerializationBoundary() {
        assertEquals(
            TransformOperation.entries.size,
            TransformOperation.entries.map(TransformOperation::tag).distinct().size,
        )

        for (operation in TransformOperation.entries) {
            val target = when (operation) {
                TransformOperation.Drop -> FaceKind(0)
                else -> null
            }
            val chiralities = if (operation.isChiral) Chirality.entries else listOf(null)
            for (chirality in chiralities) {
                val spec = TransformSpec(TransformId(operation, chirality, target))
                assertEquals(spec, spec.tag.parseTransformTag(), spec.tag)
            }
        }
    }

    @Test
    fun targetsAndTweaksRoundTripWithoutStringBasedInternalRemapping() {
        val specs = listOf(
            TransformSpec(
                TransformId(TransformOperation.Kis, target = FaceKind(2)),
                mapOf(TransformTweak.Height to 0.75),
            ),
            TransformSpec(
                TransformId(TransformOperation.Truncated, target = VertexKind(3)),
                mapOf(TransformTweak.Depth to 1.2),
            ),
            TransformSpec(
                TransformId(TransformOperation.Gyro, Chirality.Flipped),
                mapOf(TransformTweak.Inset to 0.8, TransformTweak.Twist to 1.1),
            ),
        )

        for (spec in specs) assertEquals(spec, spec.tag.parseTransformTag(), spec.tag)
    }
}
