package polyhedra.core

import polyhedra.core.poly.*
import polyhedra.core.transform.*
import polyhedra.model.api.PointGroup
import polyhedra.model.api.PointGroupFamily
import polyhedra.model.api.PointGroupSuffix
import polyhedra.model.poly.*
import polyhedra.model.util.*
import kotlin.math.abs
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.*

class NonConvexGeometryTest {
    @Test
    fun concaveFacesUseEarClippingAndValidateAsOneClosedSurface() {
        val poly = concavePrism()

        poly.validateGeometry()
        val top = poly.fs.first()
        assertEquals(top.size - 2, top.triangles.size)
        assertEquals(7.0, top.triangles.sumOf { triangle ->
            triangleAreaXY(top[triangle.a], top[triangle.b], top[triangle.c])
        }, 1e-9)

        // This point lies in the rectangular notch of the C-shaped face. A triangle fan from the
        // first vertex covers it incorrectly; the shared triangulation must leave it empty.
        val notch = Vec3(0.5, 0.0, 1.0)
        assertFalse(top.triangles.any { triangle ->
            pointInTriangleXY(notch, top[triangle.a], top[triangle.b], top[triangle.c])
        })
    }

    @Test
    fun triangulationAndPropernessAreScaleInvariant() {
        val source = concavePrism()

        for (factor in listOf(1e-6, 1.0, 1e6)) {
            val scaled = source.scaled(factor)
            scaled.validateProperGeometry()
            assertEquals(
                source.fs.sumOf { face -> face.size - 2 },
                scaled.fs.sumOf { face -> face.triangles.size },
                "factor=$factor",
            )
        }
    }

    @Test
    fun edgeWithThreeIncidentFacesIsRejectedAtConstruction() {
        val tetrahedron = Seed.Tetrahedron.poly
        val failure = assertFailsWith<IllegalArgumentException> {
            polyhedron {
                vertices(tetrahedron.vs)
                faces(tetrahedron.fs)
                face(tetrahedron.fs.first())
            }
        }
        assertContains(failure.message.orEmpty(), "incident faces")
    }

    @Test
    fun disconnectedClosedSurfacesAreRejectedAsCompound() {
        val tetrahedron = Seed.Tetrahedron.poly
        val compound = polyhedron {
            tetrahedron.vs.forEach { vertex(it) }
            tetrahedron.vs.forEach { vertex(it + Vec3(4.0, 0.0, 0.0)) }
            tetrahedron.fs.forEach { face(it.fvs.map(Vertex::id)) }
            tetrahedron.fs.forEach { face(it.fvs.map { vertex -> vertex.id + tetrahedron.vs.size }) }
        }

        val failure = assertFailsWith<IllegalArgumentException> { compound.validateMeshGeometry() }
        assertContains(failure.message.orEmpty(), "2 disconnected surface components")
    }

    @Test
    fun intersectingConnectedSurfaceIsRejected() {
        val selfIntersecting = polyhedron {
            repeat(3) { index ->
                val angle = 2.0 * PI * index / 3.0
                vertex(cos(angle), sin(angle), 0.0)
            }
            val top = vertex(1.5, 0.0, -0.4)
            val bottom = vertex(0.0, 0.0, -1.0)
            repeat(3) { index ->
                val next = (index + 1) % 3
                face(index, top.id, next)
                face(index, next, bottom.id)
            }
        }

        for (factor in listOf(1e-6, 1.0, 1e6)) {
            val failure = assertFailsWith<IllegalArgumentException>("factor=$factor") {
                selfIntersecting.scaled(factor).validateProperGeometry()
            }
            assertContains(failure.message.orEmpty(), "intersect outside their shared boundary")
        }
    }

    @Test
    fun localSubdivisionTransformsSupportAConcaveProperSurface() {
        val source = concavePrism()
        val transformed = listOf(
            "truncate" to { source.truncated(0.2) },
            "rectify" to { source.rectified() },
            "cantellate" to { source.cantellated(0.08) },
            "bevel" to { source.bevelled(BevellingRatio(0.08, 0.15)) },
        )

        val failures = transformed.mapNotNull { (name, operation) ->
            runCatching { operation().validateProperGeometry() }
                .exceptionOrNull()
                ?.let { failure -> "$name: ${failure.message}" }
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    @Test
    fun unsupportedConcaveLocalConstructionsFailPropernessValidationPrecisely() {
        val source = concavePrism()
        val failures = listOf(
            source.snub(SnubbingRatio(0.08, 0.03)),
            source.chamfered(0.05),
        ).map { poly -> assertFailsWith<IllegalArgumentException> { poly.validateProperGeometry() } }

        assertContains(failures[0].message.orEmpty(), "intersect outside their shared boundary")
        assertContains(failures[1].message.orEmpty(), "Face boundary intersects itself")
    }

    @Test
    fun canonicalizingTransformsAcceptNonConvexInputTopology() {
        val source = concavePrism()
        val transformed = listOf(
            "canonical" to { source.canonical() },
            "propeller" to { source.propeller() },
            "whirl" to { source.whirl() },
            "quinto" to { source.quinto() },
        )

        val failures = transformed.mapNotNull { (name, operation) ->
            runCatching {
                val result = operation()
                result.validateProperGeometry()
                require(result.fs.all(Face::isPlanar))
            }.exceptionOrNull()?.let { failure -> "$name: ${failure.message}" }
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    @Test
    fun dualCanonicalizesAReflexInputWhenDirectPolarReciprocationCrosses() {
        val source = dentedCube()
        source.validateProperGeometry()

        val dual = source.dual()
        dual.validateProperGeometry()
        assertTrue(dual.isConvexGeometry)
        assertEquals(source.fs.size, dual.vs.size)
        assertEquals(source.vs.size, dual.fs.size)
    }

    @Test
    fun symmetryAnalysisUsesTopologyAsWellAsNonConvexVertexGeometry() {
        val symmetry = dentedCube().analyzeSymmetry()

        assertEquals(
            PointGroup(PointGroupFamily.Cyclic, 4, PointGroupSuffix.Vertical),
            symmetry.pointGroup,
        )
        assertEquals(FEV(3, 4, 3), symmetry.orbitCounts)
    }
}

private fun concavePrism(): Polyhedron = polyhedron {
    val boundary = listOf(
        0.0 to 0.0,
        0.0 to 3.0,
        3.0 to 3.0,
        3.0 to 2.0,
        1.0 to 2.0,
        1.0 to 1.0,
        3.0 to 1.0,
        3.0 to 0.0,
    )
    boundary.forEachIndexed { index, (x, y) ->
        vertex(x - 1.5, y - 1.5, 1.0, VertexKind(index))
    }
    boundary.forEachIndexed { index, (x, y) ->
        vertex(x - 1.5, y - 1.5, -1.0, VertexKind(boundary.size + index))
    }
    val n = boundary.size
    face((0 until n).toList(), FaceKind(0))
    face((0 until n).map { index -> n + index }.asReversed(), FaceKind(1))
    for (index in 0 until n) {
        val next = (index + 1) % n
        face(listOf(next, index, n + index, n + next), FaceKind(2 + index))
    }
}

private fun dentedCube(): Polyhedron = polyhedron {
    val cube = Seed.Cube.poly
    cube.vs.forEach { vertex(it, VertexKind(it.id)) }
    val dent = vertex(0.0, 0.0, 0.3, VertexKind(cube.vs.size))
    val top = cube.fs.single { face -> face.z > 0.5 }
    cube.fs.filter { face -> face != top }.forEachIndexed { index, face ->
        face(face.fvs, FaceKind(index))
    }
    top.fvs.indices.forEach { index ->
        val next = (index + 1) % top.size
        face(listOf(top[index], top[next], dent), FaceKind(cube.fs.size - 1 + index))
    }
}

private fun triangleAreaXY(a: Vec3, b: Vec3, c: Vec3): Double =
    abs((b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x)) / 2.0

private fun pointInTriangleXY(point: Vec3, a: Vec3, b: Vec3, c: Vec3): Boolean {
    fun side(p: Vec3, q: Vec3, r: Vec3) =
        (q.x - p.x) * (r.y - p.y) - (q.y - p.y) * (r.x - p.x)
    val ab = side(a, b, point)
    val bc = side(b, c, point)
    val ca = side(c, a, point)
    return (ab >= -1e-9 && bc >= -1e-9 && ca >= -1e-9) ||
        (ab <= 1e-9 && bc <= 1e-9 && ca <= 1e-9)
}
