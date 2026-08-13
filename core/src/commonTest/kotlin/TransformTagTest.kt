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
    fun legacyOperationOrdinalsStayStableAcrossCachedJsModules() {
        assertEquals(
            listOf(
                "n", "t", "a", "e", "d", "b", "s", "p", "w", "q", "c", "o", "G", "S",
                "k", "j", "N", "z", "O", "m", "g", "x",
            ),
            TransformOperation.entries.take(22).map(TransformOperation::tag),
        )
    }

    @Test
    fun operationTagsAreUniqueAndRoundTripThroughTheSerializationBoundary() {
        assertEquals(
            TransformOperation.entries.size,
            TransformOperation.entries.map(TransformOperation::tag).distinct().size,
        )

        for (operation in TransformOperation.entries) {
            val target = when (operation) {
                TransformOperation.Drop -> FaceKind(0)
                TransformOperation.Radial -> VertexKind(0)
                TransformOperation.StellateFace -> FaceKind(0)
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
            TransformSpec(
                TransformId(TransformOperation.Radial, target = VertexKind(1)),
                mapOf(TransformTweak.Radius to 1.25),
            ),
            TransformSpec(
                TransformId(TransformOperation.StellateFace, target = FaceKind(1)),
                mapOf(TransformTweak.Radius to 1.25),
            ),
            TransformSpec(
                TransformId(TransformOperation.Stellated),
                mapOf(TransformTweak.StellationResult to 2.0),
            ),
        )

        for (spec in specs) assertEquals(spec, spec.tag.parseTransformTag(), spec.tag)
    }

    @Test
    fun newOperationAndTweakTagsHaveCanonicalForms() {
        assertEquals(
            TransformSpec(TransformId(TransformOperation.Resolve)),
            "R".parseTransformTag(),
        )
        assertEquals(
            TransformSpec(
                TransformId(TransformOperation.Radial, target = VertexKind(0)),
                mapOf(TransformTweak.Radius to 1.25),
            ),
            "r[A]~R=1.25".parseTransformTag(),
        )
        assertEquals(
            TransformSpec(
                TransformId(TransformOperation.StellateFace, target = FaceKind(0)),
                mapOf(TransformTweak.Radius to 1.25),
            ),
            "f[${FaceKind(0)}]~R=1.25".parseTransformTag(),
        )
        assertEquals(
            TransformSpec(
                TransformId(TransformOperation.Greatened),
                mapOf(TransformTweak.StellationResult to 2.0),
            ),
            "G~l=2".parseTransformTag(),
        )
    }
}
