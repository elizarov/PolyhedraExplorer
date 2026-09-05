package polyhedra.core

import kotlinx.coroutines.test.runTest
import polyhedra.core.api.convertStl
import polyhedra.core.poly.*
import polyhedra.core.transform.resolved
import polyhedra.model.api.*
import polyhedra.model.poly.*
import polyhedra.model.util.*
import kotlin.test.*

class CompoundExportTest {
    @Test
    fun overlappingCoplanarMembersResolveWithoutDuplicateFaces() = runTest {
        val cube = Seed.Cube.poly
        val shift = (cube.es.first().b - cube.es.first().a) * 0.25
        val translated = polyhedron {
            cube.vs.forEach { vertex(it + shift) }
            cube.fs.forEach { face(it) }
        }
        val union = compound(listOf(cube, translated)).resolved()
        union.validateProperGeometry()
        assertEquals(cube.signedVolume() * 1.25, union.signedVolume(), 1e-7)
    }

    @Test
    fun regularCompoundsResolveAndExportClosedAndHiddenFaces() = runTest {
        for (seed in Seeds.filter { it.type == SeedType.RegularCompounds && it.chirality != Chirality.Flipped }) {
            val source = seed.poly
            println("resolve ${seed.tag}")
            source.resolved().validateProperGeometry()
            val kinds = source.faceKinds.keys.toList()
            for (hidden in listOf(emptyList(), kinds, kinds.take(1)).distinct()) {
                println("STL ${seed.tag}, hidden=$hidden")
                val result = convertStl(CoreStlRequest(presentation = CoreStlPresentation(
                    poly = source, scale = 30.0, width = 0.04, rim = 0.04, expand = 0.0,
                    hiddenFaceKinds = hidden,
                )))
                assertNull(result.error, "${seed.tag}/$hidden: ${result.error}")
                polyhedron {
                    result.vertices.forEach { vertex(it) }
                    result.triangles.forEach { face(listOf(it.a, it.c, it.b)) }
                }.validateProperGeometry()
            }
        }
    }

    @Test
    fun separatedMaterialComponentsRemainInResolvedAndStl() = runTest {
        val tetrahedron = Seed.Tetrahedron.poly
        val source = compound(listOf(tetrahedron, polyhedron {
            tetrahedron.vs.forEach { vertex(it + Vec3(4.0, 0.0, 0.0)) }
            tetrahedron.fs.forEach { face(it) }
        }))
        assertEquals(2, source.resolved().components.size)
        val result = convertStl(CoreStlRequest(presentation = CoreStlPresentation(source, emptyList(), scale = 10.0, width = 0.04, rim = 0.04, expand = 0.0)))
        assertNull(result.error, result.error?.reason)
        val poly = polyhedron {
            result.vertices.forEach { vertex(it) }
            result.triangles.forEach { face(listOf(it.a, it.c, it.b)) }
        }
        assertEquals(2, poly.components.size)
        poly.validateProperGeometry()
    }
}
